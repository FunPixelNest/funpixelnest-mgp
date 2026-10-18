using System;
using System.Runtime.InteropServices;
using UnityEngine;

namespace FunPixelNest
{
    /// <summary>
    /// Platform-specific transport between the game and the host app.
    /// Exactly one implementation is compiled per build target:
    ///  - WebGL player:  window.FPN via Plugins/WebGL/FunPixelNestBridge.jslib
    ///  - Android player (Unity as a Library): launching Activity intent + static Java bridge class
    ///  - iOS player (Unity as a Library): C functions in Plugins/iOS/FunPixelNestBridge.mm
    ///  - Editor / everything else: not hosted (FpnPlatform logs instead of sending).
    /// Every method may throw; FpnPlatform wraps all calls in try/catch.
    /// </summary>
    internal static class FpnNativeBridge
    {
        internal const string AndroidLaunchExtra = "fpn_launch_data";
        internal const string AndroidBridgeClass = "com.funpixelnest.mgp.unity.FpnUnityBridge";

#if UNITY_WEBGL && !UNITY_EDITOR
        [DllImport("__Internal")] private static extern int FPN_IsHosted();
        // The jslib allocates the returned buffer with _malloc; the IL2CPP marshaler copies it into a
        // managed string and frees the native buffer automatically (Unity's documented pattern).
        [DllImport("__Internal")] private static extern string FPN_GetLaunchData();
        [DllImport("__Internal")] private static extern void FPN_SubmitScore(double score, double durationMs, string eventLogJson);
        [DllImport("__Internal")] private static extern void FPN_Quit();
        [DllImport("__Internal")] private static extern void FPN_Log(string message);
        [DllImport("__Internal")] private static extern void FPN_Hud(double score, double secondsLeft, double lives, double counter);
        [DllImport("__Internal")] private static extern double FPN_GetInset(int bottom);

        /// <summary>Returns true when the host is present; outputs the raw launch JSON (may be null).</summary>
        internal static bool DetectHost(out string launchJson)
        {
            launchJson = null;
            if (FPN_IsHosted() == 0) return false;
            launchJson = FPN_GetLaunchData();
            return true;
        }

        internal static void SendScore(FpnLaunchData data, long score, long durationMs, string eventLogJson)
        {
            FPN_SubmitScore((double)score, (double)durationMs, eventLogJson ?? "[]");
        }

        internal static void SendQuit(FpnLaunchData data)
        {
            FPN_Quit();
        }

        internal static void SendLog(string message)
        {
            FPN_Log(message ?? string.Empty);
        }

        internal static void SendHud(long score, double secondsLeft, double lives, double counter)
        {
            FPN_Hud((double)score, secondsLeft, lives, counter);
        }

        internal static float GetInset(bool bottom)
        {
            return (float)FPN_GetInset(bottom ? 1 : 0);
        }

#elif UNITY_ANDROID && !UNITY_EDITOR
        internal static bool DetectHost(out string launchJson)
        {
            launchJson = null;
            // Inside the FunPixelNest app (Unity as a Library): ask the app's bridge directly.
            // In a normal game APK the class doesn't exist and this falls through to the intent.
            try
            {
                using (var bridge = new AndroidJavaClass(AndroidBridgeClass))
                {
                    launchJson = bridge.CallStatic<string>("getLaunchData");
                }
            }
            catch (Exception)
            {
                launchJson = null;
            }
            if (!string.IsNullOrEmpty(launchJson)) return true;

            using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                if (activity == null) return false;
                using (var intent = activity.Call<AndroidJavaObject>("getIntent"))
                {
                    if (intent == null) return false;
                    launchJson = intent.Call<string>("getStringExtra", AndroidLaunchExtra);
                }
            }
            return !string.IsNullOrEmpty(launchJson);
        }

        internal static void SendScore(FpnLaunchData data, long score, long durationMs, string eventLogJson)
        {
            // Never pass null strings: AndroidJNIHelper derives the JNI signature from argument types,
            // and a null argument would produce "Ljava/lang/Object;" instead of "Ljava/lang/String;".
            using (var bridge = new AndroidJavaClass(AndroidBridgeClass))
            {
                bridge.CallStatic("onScore",
                    data != null ? data.GameId : string.Empty,
                    data != null ? data.TicketId : string.Empty,
                    score,
                    durationMs,
                    eventLogJson ?? "[]");
            }
        }

        internal static void SendQuit(FpnLaunchData data)
        {
            using (var bridge = new AndroidJavaClass(AndroidBridgeClass))
            {
                bridge.CallStatic("onQuit", data != null ? data.TicketId : string.Empty);
            }
        }

        internal static void SendLog(string message)
        {
            // Debug.Log already reaches logcat (tag "Unity"); nothing extra to do.
        }

        internal static void SendHud(long score, double secondsLeft, double lives, double counter)
        {
            using (var bridge = new AndroidJavaClass(AndroidBridgeClass))
            {
                bridge.CallStatic("onHud", score, (int)secondsLeft, (int)lives, (int)counter);
            }
        }

        internal static float GetInset(bool bottom)
        {
            using (var bridge = new AndroidJavaClass(AndroidBridgeClass))
            {
                return bridge.CallStatic<float>("getInset", bottom);
            }
        }

#elif UNITY_IOS && !UNITY_EDITOR
        // Implemented in Plugins/iOS/FunPixelNestBridge.mm. The .mm returns a strdup()'d (malloc) copy;
        // the IL2CPP marshaler converts it to a managed string and free()s the native buffer.
        [DllImport("__Internal")] private static extern string FPN_iOS_GetLaunchData();
        [DllImport("__Internal")] private static extern void FPN_iOS_SubmitScore(double score, double durationMs, string eventLogJson);
        [DllImport("__Internal")] private static extern void FPN_iOS_Quit();

        internal static bool DetectHost(out string launchJson)
        {
            launchJson = FPN_iOS_GetLaunchData();
            return !string.IsNullOrEmpty(launchJson);
        }

        internal static void SendScore(FpnLaunchData data, long score, long durationMs, string eventLogJson)
        {
            FPN_iOS_SubmitScore((double)score, (double)durationMs, eventLogJson ?? "[]");
        }

        internal static void SendQuit(FpnLaunchData data)
        {
            FPN_iOS_Quit();
        }

        internal static void SendLog(string message)
        {
            // Debug.Log already reaches the Xcode console; nothing extra to do.
        }

        internal static void SendHud(long score, double secondsLeft, double lives, double counter) { /* Unity as a Library: not wired yet */ }

        internal static float GetInset(bool bottom) { return 0f; }

#else
        // Editor, standalone, or any other target: never hosted.
        internal static bool DetectHost(out string launchJson)
        {
            launchJson = null;
            return false;
        }

        internal static void SendScore(FpnLaunchData data, long score, long durationMs, string eventLogJson) { }

        internal static void SendQuit(FpnLaunchData data) { }

        internal static void SendLog(string message) { }

        internal static void SendHud(long score, double secondsLeft, double lives, double counter) { }

        internal static float GetInset(bool bottom) { return 0f; }
#endif
    }
}
