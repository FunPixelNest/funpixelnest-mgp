using System;
using UnityEngine;

namespace FunPixelNest
{
    /// <summary>Music / sound / vibration chosen by the player in the platform app.</summary>
    [Serializable]
    public sealed class FpnSettings
    {
        public bool music = true;
        public bool sound = true;
        public bool vibration = true;
    }

    /// <summary>
    /// Pause and settings coming from the platform app.
    ///
    /// When the player presses Back (Android / browser) or Pause, the app freezes the game and shows its own
    /// pause menu (resume within 5 minutes or the match ends). By default the SDK pauses time and audio for you
    /// (<see cref="AutoPause"/>); subscribe to <see cref="OnPaused"/> / <see cref="OnResumed"/> to do more,
    /// e.g. stop your own timers that use unscaled time.
    /// </summary>
    public static class FpnSession
    {
        /// <summary>Set Time.timeScale = 0 and pause audio while the platform pause menu is open. Default true.</summary>
        public static bool AutoPause = true;

        public static bool IsPaused { get; private set; }
        public static FpnSettings Settings { get; private set; } = new FpnSettings();

        public static event Action OnPaused;
        public static event Action OnResumed;
        public static event Action<FpnSettings> OnSettingsChanged;

        private static float _timeScaleBeforePause = 1f;

        internal static void SetPaused(bool paused)
        {
            if (paused == IsPaused) return;
            IsPaused = paused;
            if (AutoPause)
            {
                if (paused)
                {
                    _timeScaleBeforePause = Time.timeScale;
                    Time.timeScale = 0f;
                }
                else
                {
                    Time.timeScale = _timeScaleBeforePause;
                }
                AudioListener.pause = paused;
            }
            if (paused) OnPaused?.Invoke(); else OnResumed?.Invoke();
        }

        internal static void SetSettings(FpnSettings settings)
        {
            Settings = settings ?? new FpnSettings();
            OnSettingsChanged?.Invoke(Settings);
        }
    }

    /// <summary>
    /// Receives messages from the platform (WebGL host: unityInstance.SendMessage("FunPixelNestPlatform", ...)).
    /// Created automatically; do not add it to a scene yourself.
    /// </summary>
    [AddComponentMenu("")]
    public sealed class FpnPlatformReceiver : MonoBehaviour
    {
        public const string ObjectName = "FunPixelNestPlatform";

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Create()
        {
            if (GameObject.Find(ObjectName) != null) return;
            var go = new GameObject(ObjectName);
            go.AddComponent<FpnPlatformReceiver>();
            DontDestroyOnLoad(go);
        }

        /// <summary>
        /// The app starts a new match in the running game (Unity as a Library). JSON = launch data.
        /// Resets the match state, clears any pause, and raises <see cref="FpnPlatform.OnNewMatch"/>.
        /// </summary>
        public void OnPlatformLaunch(string launchJson)
        {
            FpnSession.SetPaused(false);
            FpnPlatform.BeginNewMatch(launchJson);
        }

        /// <summary>"1" = pause, "0" = resume.</summary>
        public void OnPlatformPause(string value) => FpnSession.SetPaused(value == "1" || value == "true");

        /// <summary>JSON: {"music":true,"sound":true,"vibration":false}</summary>
        public void OnPlatformSettings(string json)
        {
            try
            {
                FpnSession.SetSettings(JsonUtility.FromJson<FpnSettings>(json));
            }
            catch (Exception e)
            {
                Debug.LogWarning("[FunPixelNest] Bad settings JSON: " + e.Message);
            }
        }
    }
}
