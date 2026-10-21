package com.funpixelnest.mgp.audio

import android.os.VibratorManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.SystemClock
import android.os.Build
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.funpixelnest.mgp.R

/**
 * App sounds on Android: a soft ambient music loop (MediaPlayer, fades in / out) and short UI sounds
 * (SoundPool, preloaded). Music pauses while the app is in the background and while a game or a
 * full-screen ad plays (the activity is paused then, or the app turns music off).
 */
class AndroidUiAudio(context: Context) : UiAudio {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    private val ids = mutableMapOf<Sfx, Int>()
    private val loaded = mutableSetOf<Int>()

    private var music: MediaPlayer? = null
    private var wanted = false      // the app wants music (setting on, not in a game)
    private var foreground = true   // the activity is visible
    private var volume = 0f
    private val fadeStep = 0.04f

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
        val res = mapOf(
            Sfx.Tap to R.raw.sfx_tap,
            Sfx.Tab to R.raw.sfx_tab,
            Sfx.Popup to R.raw.sfx_popup,
            Sfx.Close to R.raw.sfx_close,
            Sfx.Reward to R.raw.sfx_reward,
            Sfx.LevelUp to R.raw.sfx_levelup,
            Sfx.Error to R.raw.sfx_error,
            Sfx.Toggle to R.raw.sfx_toggle,
        )
        res.forEach { (sfx, raw) -> ids[sfx] = pool.load(app, raw, 1) }
    }

    override fun play(sfx: Sfx) {
        val id = ids[sfx] ?: return
        if (id !in loaded || !foreground) return
        val v = when (sfx) {
            Sfx.Tap, Sfx.Toggle -> 0.45f
            Sfx.Tab -> 0.5f
            else -> 0.7f
        }
        pool.play(id, v, v, 1, 0, 1f)
    }

    /** Light tap: the system "click" effect where available, else a 12 ms soft pulse. */
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }
    private var lastTick = 0L

    override fun tick() {
        val v = vibrator ?: return
        if (!foreground) return
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 40) return // one tick per tap, even if two handlers fire
        lastTick = now
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> v.vibrate(VibrationEffect.createOneShot(12, 60))
                else -> @Suppress("DEPRECATION") v.vibrate(12)
            }
        }
    }

    override fun setMusic(on: Boolean) {
        wanted = on
        update()
    }

    fun onActivityResume() {
        foreground = true
        update()
    }

    fun onActivityPause() {
        foreground = false
        // Stop right away (a fade could still be heard over an ad or another app).
        main.removeCallbacksAndMessages(null)
        music?.let { runCatching { it.pause() } }
        volume = 0f
    }

    private fun update() {
        main.removeCallbacksAndMessages(null)
        if (wanted && foreground) fadeIn() else fadeOut()
    }

    private fun player(): MediaPlayer? {
        music?.let { return it }
        return runCatching {
            MediaPlayer.create(app, R.raw.music_ambient)?.apply {
                setAudioAttributes(attrs)
                isLooping = true
                setVolume(0f, 0f)
            }
        }.getOrNull()?.also { music = it }
    }

    private fun fadeIn() {
        val p = player() ?: return
        if (!p.isPlaying) runCatching { p.start() }
        val tick = object : Runnable {
            override fun run() {
                volume = (volume + fadeStep).coerceAtMost(MUSIC_VOLUME)
                p.setVolume(volume, volume)
                if (volume < MUSIC_VOLUME) main.postDelayed(this, 60)
            }
        }
        main.post(tick)
    }

    private fun fadeOut() {
        val p = music ?: return
        val tick = object : Runnable {
            override fun run() {
                volume = (volume - fadeStep).coerceAtLeast(0f)
                runCatching { p.setVolume(volume, volume) }
                if (volume > 0f) main.postDelayed(this, 50) else runCatching { if (p.isPlaying) p.pause() }
            }
        }
        main.post(tick)
    }

    private companion object {
        /** Background music stays quiet under the UI sounds. */
        const val MUSIC_VOLUME = 0.32f
    }
}
