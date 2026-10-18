package com.funpixelnest.mgp

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import java.io.File

/**
 * Test builds: when the app crashed last time (Kotlin/Java or native, e.g. inside Unity), show the details on
 * the next start with a Copy button, so the crash can be reported without a USB cable or logcat.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "crash_report"
    private const val SEEN = "seen_exit_ts"

    /** Saves the stack trace of an uncaught exception, then lets Android handle the crash as usual. */
    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText("App crash (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE})\n" +
                    "Thread: ${thread.name}\n\n" + error.stackTraceToString().take(12_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Shows the last crash, if any, once. */
    fun showIfAny(activity: Activity) {
        val text = readJavaCrash(activity) ?: readNativeCrash(activity) ?: return
        AlertDialog.Builder(activity)
            .setTitle("The app closed unexpectedly last time")
            .setMessage(text.take(3_000))
            .setPositiveButton("Copy details") { _, _ ->
                val clip = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("FunPixelNest crash", text))
                Toast.makeText(activity, "Copied, please paste it in the chat", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun readJavaCrash(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        file.delete()
        // The native report for the same exit would only repeat it.
        markExitsSeen(context)
        return text?.takeIf { it.isNotBlank() }
    }

    /** Native crashes (Unity) and ANRs: Android 11+ keeps the reason and a trace. */
    private fun readNativeCrash(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exit = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() }.getOrNull() ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (exit.timestamp <= prefs.getLong(SEEN, 0)) return null
        prefs.edit().putLong(SEEN, exit.timestamp).apply()
        val kind = when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
            ApplicationExitInfo.REASON_CRASH -> "App crash"
            ApplicationExitInfo.REASON_ANR -> "App not responding"
            else -> return null
        }
        val trace = runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        return "$kind (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE})\n" +
            (exit.description ?: "") + "\n\n" + (trace?.let { keepUseful(it) } ?: "(no trace)")
    }

    private fun markExitsSeen(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val latest = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()?.timestamp }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SEEN, latest).apply()
    }

    /** Native tombstones are long: keep the header and the crashing thread's backtrace. */
    private fun keepUseful(trace: String): String {
        val lines = trace.lines()
        val start = lines.indexOfFirst { it.contains("backtrace:") }.takeIf { it >= 0 } ?: return trace.take(8_000)
        val head = lines.take(12)
        val bt = lines.drop(start).take(40)
        return (head + listOf("...") + bt).joinToString("\n")
    }
}
