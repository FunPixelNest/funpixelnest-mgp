using System.Collections;
using FunPixelNest;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Mkey
{
    /// <summary>
    /// Mahjong inside the FunPixelNest app: gameplay only.
    /// - The app opens the game scene directly (no splash, map, tutorial, pause, end-now or game-over pages):
    ///   the app shows its own HUD (pause, score, timer) and pause menu; the round ends by sending the
    ///   total score (matches + streaks + time and tiles-cleared bonuses, as in the Skillz build) to the app.
    /// - Layout and tile faces come from the match seed, so every player in a pool gets the same board.
    /// - Sound / music / vibration follow the app's settings.
    /// Outside the app (Mahjong's own builds) nothing changes.
    /// </summary>
    public static class FpnMahjongLink
    {
        /// <summary>Scene name of the game inside the FunPixelNest games build.</summary>
        public const string SceneName = "MahjongGame";

        /// <summary>Prefab that references every Mahjong settings asset (holders, construct set).</summary>
        private const string HoldersPath = "MahjongHolders/ScriptableHolder";

        private static GameObject holders;
        private static bool holdersTried;
        private static bool submitted;

        /// <summary>True when Mahjong runs inside the FunPixelNest app.</summary>
        public static bool InApp => FpnPlatform.IsHosted;

        /// <summary>
        /// Loads the settings assets the map scene normally keeps in memory (the app skips the map).
        /// Returns true when they were loaded by this call.
        /// </summary>
        public static bool EnsureHolders()
        {
            if (holdersTried)
            {
                return false;
            }

            holdersTried = true;
            holders = Resources.Load<GameObject>(HoldersPath);
            return holders != null;
        }

        /// <summary>Level for this match, the same for everyone with the same seed.</summary>
        public static void PickLevel()
        {
            GameConstructSet set = GameConstructSet.Instance;
            int count = set != null ? set.LevelCount : 0;
            GameLevelHolder.CurrentLevel = count <= 0 ? 0 : FpnRandom.FromLaunch().Next(count);
            Debug.Log("[Mahjong] App match level " + (GameLevelHolder.CurrentLevel + 1) + " / " + count);
        }

        /// <summary>Called by the board just before it builds the level.</summary>
        public static void BeginMatch(GameBoard board)
        {
            submitted = false;

            // Tile faces and their shuffle use UnityEngine.Random: seed it from the match.
            FpnRandom random = FpnRandom.FromLaunch();
            random.Next(1000); // the first value picks the level
            Random.InitState(random.Next(int.MaxValue));

            // Same number of shuffles for everyone in every match.
            if (ShuffleHolder.Instance)
            {
                ShuffleHolder.Instance.SetCount(ShuffleHolder.Instance.DefaultCount);
            }

            ApplyAppLayout();
            ApplySettings(FpnSession.Settings);
            if (board && !board.GetComponent<FpnMahjongHud>())
            {
                board.gameObject.AddComponent<FpnMahjongHud>();
            }
        }

        /// <summary>Hides Mahjong's own header (timer, score, pause) and debug buttons; keeps UI clear of the app's HUD.</summary>
        public static void ApplyAppLayout()
        {
            GameObject header = GameObject.Find("HeaderGui");
            if (header != null)
            {
                foreach (string name in new[] { "Timer", "ScoreCounter", "PauseButton", "ButtonToMap", "ButtonMenu", "Level", "PossibleMatchesCounter" })
                {
                    Transform child = header.transform.Find(name);
                    if (child != null)
                    {
                        child.gameObject.SetActive(false);
                    }
                }
            }

            foreach (string name in new[] { "LevelCompleteButton", "FPSText_debug" })
            {
                GameObject debugObject = GameObject.Find(name);
                if (debugObject != null)
                {
                    debugObject.SetActive(false);
                }
            }

            FitToApp(GameObject.Find("SceneGui"));
            FitToApp(GameObject.Find("Announcer"));
        }

        private static void FitToApp(GameObject target)
        {
            if (target == null || !(target.transform is RectTransform rt))
            {
                return;
            }

            rt.anchorMin = new Vector2(rt.anchorMin.x, Mathf.Max(rt.anchorMin.y, FpnPlatform.AppInsetBottom));
            rt.anchorMax = new Vector2(rt.anchorMax.x, Mathf.Min(rt.anchorMax.y, 1f - FpnPlatform.AppInsetTop));
        }

        /// <summary>Shrinks the tile grid if its top would sit under the app's HUD.</summary>
        public static void FitBoard(GameBoard board)
        {
            Camera cam = Camera.main;
            if (board == null || board.GridContainer == null || cam == null || !cam.orthographic)
            {
                return;
            }

            SpriteRenderer[] renderers = board.GridContainer.GetComponentsInChildren<SpriteRenderer>();
            if (renderers.Length == 0)
            {
                return;
            }

            Bounds bounds = renderers[0].bounds;
            for (int i = 1; i < renderers.Length; i++)
            {
                bounds.Encapsulate(renderers[i].bounds);
            }

            float depth = Mathf.Abs(board.GridContainer.position.z - cam.transform.position.z);
            float topLimit = cam.ViewportToWorldPoint(new Vector3(0.5f, 1f - FpnPlatform.AppInsetTop - 0.015f, depth)).y;
            if (bounds.max.y <= topLimit || bounds.size.y <= 0f)
            {
                return;
            }

            float scale = Mathf.Clamp((topLimit - bounds.min.y) / bounds.size.y, 0.6f, 1f);
            Vector3 pivot = new Vector3(bounds.center.x, bounds.min.y, board.GridContainer.position.z);
            Transform grid = board.GridContainer;
            grid.localScale *= scale;
            grid.position = pivot + (grid.position - pivot) * scale;
        }

        /// <summary>Round over: hand the total to the app (it closes the game and shows the result).</summary>
        public static void SubmitScore(int total)
        {
            if (submitted)
            {
                return;
            }

            submitted = true;
            FpnPlatform.SubmitScore(Mathf.Max(0, total));
        }

        /// <summary>Copies the app's sound settings into Mahjong's own settings.</summary>
        public static void ApplySettings(FpnSettings settings)
        {
            if (!InApp || settings == null)
            {
                return;
            }

            if (SoundMaster.Instance)
            {
                SoundMaster.Instance.SetSound(settings.sound);
                SoundMaster.Instance.SetMusic(settings.music);
            }

            Vibration.IsOn = settings.vibration;
        }

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Init()
        {
            FpnSession.OnSettingsChanged += ApplySettings;
            FpnSession.OnPaused += () => SetAppPause(true);
            FpnSession.OnResumed += () => SetAppPause(false);

            // Mahjong's sound object survives scene changes: drop it when the app opens another game.
            SceneManager.activeSceneChanged += (from, to) =>
            {
                if (InApp && to.name != SceneName && SoundMaster.Instance)
                {
                    Object.Destroy(SoundMaster.Instance.gameObject);
                }
            };
        }

        private static void SetAppPause(bool paused)
        {
            // The app freezes Time.timeScale; Mahjong's clock runs on unscaled time, so stop it too.
            if (InApp && HeaderGUIController.Instance)
            {
                HeaderGUIController.Instance.SetAppPause(paused);
            }
        }
    }

    /// <summary>
    /// Sends live values to the app's HUD (score, time left) while a Mahjong round runs in the app, and keeps
    /// touch input healthy inside the app:
    /// - the board's camera is the one tagged MainCamera (another game's camera kept between scenes can't take it),
    /// - this scene's EventSystem is the active one,
    /// - tile colliders follow the board after it is fitted under the app's HUD,
    /// - if the board stays locked (no touch) for 3 s while the round is running and nothing is animating
    ///   (e.g. the 3-2-1-GO countdown never reported back), touch is switched on again.
    /// Logs "[Mahjong]" lines (Android logcat) for the first taps to help diagnose touch problems.
    /// </summary>
    public class FpnMahjongHud : MonoBehaviour
    {
        private LevelCountdownTimer timer;
        private GameBoard board;
        private float lockedSince = -1f;
        private int loggedTaps;

        private IEnumerator Start()
        {
            timer = FindObjectOfType<LevelCountdownTimer>();
            board = GetComponent<GameBoard>();
            if (FpnMahjongLink.InApp)
            {
                FixCameraAndEventSystem();
            }

            // After the sound object has read its saved settings and the board has its final size.
            yield return null;
            yield return null;
            FpnMahjongLink.ApplySettings(FpnSession.Settings);
            FpnMahjongLink.FitBoard(board);
            Physics2D.SyncTransforms();
        }

        /// <summary>Makes this scene's camera and EventSystem the ones input uses.</summary>
        private void FixCameraAndEventSystem()
        {
            var scene = gameObject.scene;
            Camera mine = null;
            foreach (Camera cam in FindObjectsOfType<Camera>(true))
            {
                if (cam.gameObject.scene == scene)
                {
                    if (mine == null && cam.CompareTag("MainCamera")) mine = cam;
                }
                else if (cam.CompareTag("MainCamera"))
                {
                    cam.tag = "Untagged"; // a camera another game kept between scenes
                    Debug.Log("[Mahjong] untagged foreign MainCamera " + cam.name);
                }
            }

            UnityEngine.EventSystems.EventSystem sceneEs = null;
            foreach (var es in FindObjectsOfType<UnityEngine.EventSystems.EventSystem>(true))
            {
                if (es.gameObject.scene == scene) sceneEs = es;
            }
            if (sceneEs != null)
            {
                foreach (var es in FindObjectsOfType<UnityEngine.EventSystems.EventSystem>(true))
                {
                    if (es != sceneEs && es.gameObject.scene != scene)
                    {
                        es.gameObject.SetActive(false);
                        Debug.Log("[Mahjong] disabled foreign EventSystem " + es.name);
                    }
                }
                if (!sceneEs.gameObject.activeSelf) sceneEs.gameObject.SetActive(true);
                if (!sceneEs.enabled) sceneEs.enabled = true;
                UnityEngine.EventSystems.EventSystem.current = sceneEs;
            }
            Debug.Log("[Mahjong] camera=" + (mine ? mine.name : "none") + " main=" + (Camera.main ? Camera.main.name : "none")
                      + " eventSystem=" + (UnityEngine.EventSystems.EventSystem.current ? UnityEngine.EventSystems.EventSystem.current.name : "none"));
        }

        private void Update()
        {
            if (!FpnMahjongLink.InApp)
            {
                return;
            }

            float secondsLeft = timer ? timer.RemainingSeconds : -1f;
            FpnPlatform.UpdateHud(Mathf.Max(0, ScoreHolder.Count), secondsLeft, -1, -1);

            LogFirstTaps();
            UnlockIfStuck();
        }

        private void LogFirstTaps()
        {
            if (loggedTaps >= 5 || Input.touchCount == 0) return;
            Touch t = Input.GetTouch(0);
            if (t.phase != TouchPhase.Began) return;
            loggedTaps++;
            Camera cam = Camera.main;
            Vector3 w = cam ? cam.ScreenToWorldPoint(t.position) : Vector3.zero;
            int hits = Physics2D.OverlapPointAll(new Vector2(w.x, w.y)).Length;
            Debug.Log("[Mahjong] tap " + t.position + " world " + w + " colliders " + hits
                      + " touchActive " + (TouchPad.Instance ? TouchPad.Instance.IsActive.ToString() : "no pad")
                      + " timeScale " + Time.timeScale + " paused " + FpnSession.IsPaused
                      + " timerStarted " + (timer && timer.IsStarted));
        }

        private void UnlockIfStuck()
        {
            bool paused = FpnSession.IsPaused || (HeaderGUIController.Instance && HeaderGUIController.Instance.IsPaused);
            bool locked = TouchPad.Instance && !TouchPad.Instance.IsActive;
            if (!locked || paused || board == null || board.LevelCompleted)
            {
                lockedSince = -1f;
                return;
            }

            if (lockedSince < 0f)
            {
                lockedSince = Time.unscaledTime;
                return;
            }

            // Matches and shuffles lock the board for well under a second; the countdown for about 3 s.
            float limit = (timer && !timer.IsStarted) ? 6f : 3f;
            if (Time.unscaledTime - lockedSince < limit) return;

            Debug.Log("[Mahjong] touch was locked for " + limit + " s: unlocking");
            lockedSince = -1f;
            if (Time.timeScale == 0f) Time.timeScale = 1f;
            board.SetControlActivity(true, true);
        }
    }
}
