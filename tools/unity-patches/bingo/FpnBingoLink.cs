using System.Collections;
using BingoFlash.Core;
using BingoFlash.UI;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.SceneManagement;

namespace FunPixelNest.Bingo
{
    /// <summary>
    /// Bingo Flash inside the FunPixelNest app (gameplay scene only; no menu, themes or results screen):
    /// - The card and the ball order come from the match seed (same game for everyone in a pool).
    /// - The app draws the HUD (pause, score, timer); Bingo's own HUD row is hidden and the board keeps clear of
    ///   the app's HUD and the bottom system bar. No lives.
    /// - Pause, Back and settings (music / sound / vibration) come from the app; Bingo's own pause panel is not used.
    /// - At the end the score (with its breakdown) goes to the app, which shows the result.
    /// Outside the app (Bingo's own build) nothing changes.
    /// </summary>
    public static class FpnBingoLink
    {
        public const string SceneName = "BingoFlashGame";

        /// <summary>Height of Bingo's own HUD row (canvas units) that the app's HUD replaces.</summary>
        private const float OwnHudRow = 110f;

        /// <summary>True when Bingo Flash runs inside the FunPixelNest app.</summary>
        public static bool InApp => FpnPlatform.IsHosted;

        /// <summary>True in the combined FunPixelNest games build (it has the router's boot scene).</summary>
        public static bool IsCombinedBuild => Application.CanStreamedLevelBeLoaded("FpnBoot");

        private static FpnRandom s_random;
        private static bool s_submitting;

        /// <summary>Card and call-order random: int in [min, max), from the match seed.</summary>
        public static int NextInt(int minInclusive, int maxExclusive)
        {
            if (maxExclusive <= minInclusive) return minInclusive;
            if (s_random == null) s_random = FpnRandom.FromLaunch();
            return s_random.Next(minInclusive, maxExclusive);
        }

        /// <summary>Called from BingoGameController.Start (after its scene wiring): layout, settings, HUD reporting.</summary>
        public static void Attach(BingoGameController game, BingoHudView hud, Canvas canvas)
        {
            if (!InApp || game == null) return;

            ApplySettings(FpnSession.Settings);

            // Bingo's HUD row (score, timer, pause): hidden, the app's HUD takes its place.
            if (hud != null)
            {
                CanvasGroup group = hud.GetComponent<CanvasGroup>();
                if (group == null) group = hud.gameObject.AddComponent<CanvasGroup>();
                group.alpha = 0f;
                group.interactable = false;
                group.blocksRaycasts = false;
            }

            // No banner inside the game view (the app puts it below the game).
            BingoLayout.SetBannerReserve(24f);
            FitSafeArea(canvas);

            if (game.GetComponent<FpnBingoHud>() == null) game.gameObject.AddComponent<FpnBingoHud>();
        }

        /// <summary>Keeps the play area below the app's HUD (beyond Bingo's own HUD row) and above the system bar.</summary>
        private static void FitSafeArea(Canvas canvas)
        {
            if (canvas == null) return;
            Transform safe = canvas.transform.Find("SafeArea");
            SafeAreaFitter fitter = safe != null ? safe.GetComponent<SafeAreaFitter>() : null;
            if (fitter == null) return;

            float scale = canvas.scaleFactor > 0f ? canvas.scaleFactor : 1f;
            float topPx = Mathf.Max(0f, FpnPlatform.AppInsetTop * Screen.height - OwnHudRow * scale);
            float bottomPx = FpnPlatform.AppInsetBottom * Screen.height;
            fitter.SetExtraPadding(new Vector4(0f, bottomPx, 0f, topPx));
        }

        /// <summary>The balls start: the match clock starts.</summary>
        public static void OnRoundStarted()
        {
            if (InApp) FpnPlatform.StartMatch();
        }

        /// <summary>
        /// Round over (time up or board cleared): send the breakdown and the total to the app after a short beat
        /// (lets the final sound play). Returns true when the app takes over (skip Bingo's own results screen).
        /// </summary>
        public static bool Finish(MonoBehaviour host, int total, int daub, int bingo, int boardClear, int timeBonus, int penalties)
        {
            if (!InApp) return false;
            if (s_submitting || FpnPlatform.ScoreSubmitted) return true;
            s_submitting = true;

            FpnPlatform.AddScoreLine("Daub Points", daub);
            if (bingo > 0) FpnPlatform.AddScoreLine("Bingo Lines", bingo);
            if (boardClear > 0) FpnPlatform.AddScoreLine("Board Clear Bonus", boardClear);
            if (timeBonus > 0) FpnPlatform.AddScoreLine("Time Bonus", timeBonus);
            if (penalties > 0) FpnPlatform.AddScoreLine("Wrong Daubs", -penalties);
            FpnPlatform.UpdateHud(Mathf.Max(0, total), 0f, -1, -1);
            host.StartCoroutine(SubmitAfter(1.1f, Mathf.Max(0, total)));
            return true;
        }

        private static IEnumerator SubmitAfter(float seconds, int total)
        {
            yield return new WaitForSecondsRealtime(seconds);
            if (!FpnPlatform.ScoreSubmitted) FpnPlatform.SubmitScore(total);
        }

        /// <summary>App settings → Bingo's own switches (music, sound incl. the ball-call voice, vibration).</summary>
        public static void ApplySettings(FpnSettings settings)
        {
            if (!InApp || settings == null || SceneManager.GetActiveScene().name != SceneName) return;
            if (BingoSettings.MusicOn != settings.music) BingoSettings.MusicOn = settings.music;
            if (BingoSettings.SoundOn != settings.sound) BingoSettings.SoundOn = settings.sound;
            if (BingoSettings.VibrationOn != settings.vibration) BingoSettings.VibrationOn = settings.vibration;
            BingoSettings.Apply();
            s_mutedListener = AudioListener.volume < 1f;
        }

        /// <summary>Bingo turned the whole listener down (its sound switch); undone when another game opens.</summary>
        private static bool s_mutedListener;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Init()
        {
            FpnSession.OnSettingsChanged += ApplySettings;
            SceneManager.sceneLoaded += OnSceneLoaded;
            SceneManager.activeSceneChanged += OnActiveSceneChanged;
        }

        private static void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            if (scene.name != SceneName || !InApp) return;
            // New match: fresh seeded random and submit state (BingoGameController.Start runs after this).
            s_random = FpnRandom.FromLaunch();
            s_submitting = false;
            UseOwnEventSystem(scene);
        }

        /// <summary>Another game opened: Bingo's audio object outlives its scene, so stop its music and give the
        /// next game normal volume (Bingo mutes the whole listener when its sound is off).</summary>
        private static void OnActiveSceneChanged(Scene from, Scene to)
        {
            if (to.name == SceneName || !IsCombinedBuild) return;
            // BingoAudio creates itself on use: only stop it if Bingo was actually played.
            if (Object.FindObjectOfType<BingoAudio>() != null) BingoAudio.StopMusic(0f);
            if (s_mutedListener) { AudioListener.volume = 1f; s_mutedListener = false; }
        }

        /// <summary>Only this scene's EventSystem takes touches (another game may have kept one between scenes).</summary>
        private static void UseOwnEventSystem(Scene scene)
        {
            EventSystem mine = null;
            foreach (EventSystem es in Object.FindObjectsOfType<EventSystem>(true))
            {
                if (es.gameObject.scene == scene) mine = es;
            }
            if (mine == null) return;
            foreach (EventSystem es in Object.FindObjectsOfType<EventSystem>(true))
            {
                if (es != mine && es.gameObject.scene != scene) es.gameObject.SetActive(false);
            }
            EventSystem.current = mine;
        }
    }

    /// <summary>Sends live score and time left to the app's HUD every frame (only changes are sent).</summary>
    public class FpnBingoHud : MonoBehaviour
    {
        private BingoGameController _game;

        private void Awake()
        {
            _game = GetComponent<BingoGameController>();
        }

        private void Update()
        {
            if (_game == null || FpnPlatform.ScoreSubmitted) return;
            FpnPlatform.UpdateHud(Mathf.Max(0, _game.CurrentScore), Mathf.Max(0f, _game.RoundTimeRemaining), -1, -1);
        }
    }
}
