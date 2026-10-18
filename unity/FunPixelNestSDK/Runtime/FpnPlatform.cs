using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using UnityEngine;

namespace FunPixelNest
{
    /// <summary>
    /// Static entry point for talking to the FunPixelNest platform app.
    ///
    /// Typical flow:
    /// <code>
    /// var data = FpnPlatform.LaunchData;          // 1. read launch data
    /// var rng  = FpnRandom.FromLaunch();          // 2. seed the board/deck
    /// FpnPlatform.StartMatch();                   // 3. start the clock when play begins
    /// FpnPlatform.SubmitScore(finalScore);        // 4. exactly once at game over (or FpnPlatform.Quit())
    /// </code>
    ///
    /// None of the public members throw. When not hosted (Editor, standalone, WebGL page without
    /// window.FPN) everything works against test launch data and "sending" just logs to the console.
    /// All members must be called from the Unity main thread.
    /// </summary>
    public static class FpnPlatform
    {
        /// <summary>Maximum number of entries kept by <see cref="AddEvent"/>.</summary>
        public const int MaxEvents = 500;

        /// <summary>Scores above this cannot be represented exactly as a JS/C double (2^53).</summary>
        private const long MaxExactScore = 9007199254740992L;

        private const string LogPrefix = "[FPN] ";

        private static bool s_initialized;
        private static bool s_isHosted;
        private static string s_rawLaunchJson;
        private static FpnLaunchData s_launchData;
        private static FpnLaunchData s_testLaunchData;

        private static bool s_matchStarted;
        private static double s_matchStartTime;
        private static bool s_scoreSubmitted;
        private static bool s_quitSent;

        private static readonly List<string> s_events = new List<string>();
        private static bool s_eventCapWarned;

        /// <summary>
        /// Raised (on the main thread) right after the score has been handed to the platform
        /// (or logged, when not hosted). Use it to show a "waiting for platform" / results screen.
        /// </summary>
        public static event Action<long> OnScoreSubmitted;

        /// <summary>
        /// Raised (on the main thread) when the app starts a new match in an already running game
        /// (Unity as a Library keeps Unity loaded between matches). Restart your game scene here;
        /// <see cref="LaunchData"/> already holds the new match.
        /// </summary>
        public static event Action<FpnLaunchData> OnNewMatch;

        // Reset statics when entering Play Mode with "Reload Domain" disabled.
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.SubsystemRegistration)]
        private static void ResetStatics()
        {
            s_initialized = false;
            s_isHosted = false;
            s_rawLaunchJson = null;
            s_launchData = null;
            s_testLaunchData = null;
            s_matchStarted = false;
            s_matchStartTime = 0;
            s_scoreSubmitted = false;
            s_quitSent = false;
            s_events.Clear();
            s_eventCapWarned = false;
            s_hudScore = long.MinValue;
            s_hudSeconds = int.MinValue;
            s_hudLives = int.MinValue;
            s_hudCounter = int.MinValue;
            OnScoreSubmitted = null;
            OnNewMatch = null;
        }

        // ------------------------------------------------------------------ hosting / launch data

        /// <summary>
        /// True when running inside the FunPixelNest app: WebGL with window.FPN present, or
        /// Android/iOS (Unity as a Library) launched by the app with launch data.
        /// Always false in the Editor.
        /// </summary>
        public static bool IsHosted
        {
            get
            {
                EnsureInitialized();
                return s_isHosted;
            }
        }

        /// <summary>
        /// Launch data for this match. When not hosted, returns test data: the data set via
        /// <see cref="SetTestLaunchData(FpnLaunchData)"/>, or Practice mode with a random seed.
        /// Never null.
        /// </summary>
        public static FpnLaunchData LaunchData
        {
            get
            {
                EnsureInitialized();
                if (s_launchData == null)
                {
                    if (s_isHosted)
                    {
                        s_launchData = ParseLaunchData(s_rawLaunchJson);
                    }
                    else
                    {
                        s_launchData = s_testLaunchData ?? CreateRandomTestLaunchData();
                        Debug.Log(LogPrefix + "Not hosted - using test launch data: " + s_launchData);
                    }
                }
                return s_launchData;
            }
        }

        /// <summary>
        /// Editor/standalone testing: use this launch data instead of a random-seed Practice match.
        /// Ignored (with a warning) when hosted. Pass null to go back to a random seed.
        /// Call it before anything reads <see cref="LaunchData"/> (e.g. in Awake of a bootstrap object).
        /// </summary>
        public static void SetTestLaunchData(FpnLaunchData data)
        {
            try
            {
                EnsureInitialized();
                if (s_isHosted)
                {
                    Debug.LogWarning(LogPrefix + "SetTestLaunchData ignored: running inside the platform.");
                    return;
                }
                if (s_launchData != null && s_matchStarted)
                {
                    Debug.LogWarning(LogPrefix + "SetTestLaunchData called after StartMatch; the board may already have been built from the previous seed.");
                }
                s_testLaunchData = data;
                s_launchData = null; // rebuilt lazily on next LaunchData access
            }
            catch (Exception e)
            {
                Debug.LogWarning(LogPrefix + "SetTestLaunchData failed: " + e.Message);
            }
        }

        /// <summary>Convenience overload: test data with the given seed and mode.</summary>
        public static void SetTestLaunchData(long seed, FpnMode mode = FpnMode.Practice, string gameId = "test-game")
        {
            SetTestLaunchData(new FpnLaunchData(gameId, mode, seed, "test-ticket", "test-player", "Test Player"));
        }

        // ------------------------------------------------------------------ match lifecycle

        /// <summary>
        /// Marks the start of play (records Time.realtimeSinceStartup) for the reported duration.
        /// Call when the player can first interact with the board. Calling again before the score is
        /// submitted restarts the clock. Called automatically by SubmitScore if never called.
        /// </summary>
        public static void StartMatch()
        {
            try
            {
                if (s_scoreSubmitted || s_quitSent)
                {
                    Debug.LogWarning(LogPrefix + "StartMatch ignored: match already finished.");
                    return;
                }
                s_matchStarted = true;
                s_matchStartTime = Time.realtimeSinceStartupAsDouble;
            }
            catch (Exception e)
            {
                Debug.LogWarning(LogPrefix + "StartMatch failed: " + e.Message);
            }
        }

        /// <summary>
        /// Adds an entry to the lightweight move log sent with the score (e.g. "m:3>7", "draw").
        /// Keep entries short. At most <see cref="MaxEvents"/> entries are kept; later ones are dropped.
        /// Ignored after the score has been submitted.
        /// </summary>
        public static void AddEvent(string e)
        {
            if (s_scoreSubmitted || s_quitSent) return;
            if (s_events.Count >= MaxEvents)
            {
                if (!s_eventCapWarned)
                {
                    s_eventCapWarned = true;
                    Debug.LogWarning(LogPrefix + "Event log is full (" + MaxEvents + " entries); further events are dropped.");
                }
                return;
            }
            s_events.Add(e ?? string.Empty);
        }

        /// <summary>True once a score has been submitted for this session.</summary>
        public static bool ScoreSubmitted
        {
            get { return s_scoreSubmitted; }
        }

        /// <summary>Submit the final score. Duration is measured from StartMatch. Event log = AddEvent entries.</summary>
        public static void SubmitScore(long score)
        {
            SubmitInternal(score, -1, null);
        }

        /// <summary>Submit the final score with an explicit play duration in milliseconds.</summary>
        public static void SubmitScore(long score, long durationMs)
        {
            SubmitInternal(score, ClampDuration(durationMs), null);
        }

        /// <summary>Submit the final score with an explicit event log (replaces the AddEvent log).</summary>
        public static void SubmitScore(long score, IList<string> eventLog)
        {
            SubmitInternal(score, -1, eventLog != null ? ToJsonArray(eventLog) : null);
        }

        /// <summary>Submit with explicit duration and event log (replaces the AddEvent log).</summary>
        public static void SubmitScore(long score, long durationMs, IList<string> eventLog)
        {
            SubmitInternal(score, ClampDuration(durationMs), eventLog != null ? ToJsonArray(eventLog) : null);
        }

        /// <summary>
        /// Submit with an event log given as a string. If it is already a JSON array ("[...]") it is sent
        /// as-is; otherwise it is wrapped as a one-element JSON array. Null = use the AddEvent log.
        /// </summary>
        public static void SubmitScore(long score, string eventLog)
        {
            SubmitInternal(score, -1, NormalizeEventLog(eventLog));
        }

        /// <summary>Submit with explicit duration and a string event log (see the other string overload).</summary>
        public static void SubmitScore(long score, long durationMs, string eventLog)
        {
            SubmitInternal(score, ClampDuration(durationMs), NormalizeEventLog(eventLog));
        }

        /// <summary>
        /// The player left without finishing. For paid matches the platform treats this as score 0.
        /// Sent at most once. Ignored if a score was already submitted (the host already has the result;
        /// let the host close the game).
        /// </summary>
        public static void Quit()
        {
            try
            {
                if (s_quitSent)
                {
                    Debug.LogWarning(LogPrefix + "Quit already sent; ignoring.");
                    return;
                }
                if (s_scoreSubmitted)
                {
                    Debug.LogWarning(LogPrefix + "Quit ignored: score already submitted.");
                    return;
                }
                s_quitSent = true;

                var data = LaunchData;
                if (IsHosted)
                {
                    FpnNativeBridge.SendQuit(data);
                    Debug.Log(LogPrefix + "Quit sent to platform.");
                }
                else
                {
                    Debug.Log(LogPrefix + "(not hosted) Would send Quit(ticketId=" + data.TicketId + ").");
                }
            }
            catch (Exception e)
            {
                Debug.LogWarning(LogPrefix + "Quit failed (host bridge missing?): " + e.Message);
            }
        }

        // ------------------------------------------------------------------ app HUD

        /// <summary>
        /// True when the app draws the in-game HUD (pause, score, timer, lives) over the game.
        /// Hide your own HUD and pause button when this is true, keep the top <see cref="AppInsetTop"/> of the
        /// screen clear, and report values with <see cref="UpdateHud"/>. Same as <see cref="IsHosted"/>.
        /// </summary>
        public static bool AppShowsHud
        {
            get { return IsHosted; }
        }

        /// <summary>
        /// Share of the screen height (0..0.5) at the top covered by the app's HUD. Keep gameplay and buttons
        /// out of it; your background can run underneath (the HUD is see-through). 0 when not hosted.
        /// </summary>
        public static float AppInsetTop
        {
            get { return Inset(false); }
        }

        /// <summary>Share of the screen height (0..0.5) at the bottom covered by the phone's system bar. 0 when not hosted.</summary>
        public static float AppInsetBottom
        {
            get { return Inset(true); }
        }

        private static float Inset(bool bottom)
        {
            try
            {
                return IsHosted ? Mathf.Clamp(FpnNativeBridge.GetInset(bottom), 0f, 0.5f) : 0f;
            }
            catch (Exception)
            {
                return 0f;
            }
        }

        private static long s_hudScore = long.MinValue;
        private static int s_hudSeconds = int.MinValue;
        private static int s_hudLives = int.MinValue;
        private static int s_hudCounter = int.MinValue;

        /// <summary>
        /// Sends the live score, remaining time and lives to the app's HUD. Cheap to call every frame: only
        /// changes are sent (score, lives, or the whole second shown on the timer).
        /// </summary>
        /// <param name="score">Current score.</param>
        /// <param name="secondsLeft">Time left in seconds; negative = this game has no timer (shown disabled).</param>
        /// <param name="lives">Lives left; negative = this game has no lives (shown disabled).</param>
        /// <param name="counter">
        /// A game's own count shown in the HUD instead of lives, e.g. arrows left in Arrow Duel; negative = none.
        /// </param>
        public static void UpdateHud(long score, float secondsLeft = -1f, int lives = -1, int counter = -1)
        {
            int shown = secondsLeft < 0f ? -1 : Mathf.CeilToInt(secondsLeft);
            int livesShown = lives < 0 ? -1 : lives;
            int counterShown = counter < 0 ? -1 : counter;
            if (score == s_hudScore && shown == s_hudSeconds && livesShown == s_hudLives && counterShown == s_hudCounter) return;
            s_hudScore = score;
            s_hudSeconds = shown;
            s_hudLives = livesShown;
            s_hudCounter = counterShown;
            try
            {
                if (IsHosted) FpnNativeBridge.SendHud(score, shown, livesShown, counterShown);
            }
            catch (Exception)
            {
                // The HUD must never break the game.
            }
        }

        /// <summary>Debug log: Unity console, plus window.FPN.log on WebGL when hosted.</summary>
        public static void Log(string message)
        {
            Debug.Log(LogPrefix + message);
            try
            {
                if (IsHosted) FpnNativeBridge.SendLog(message);
            }
            catch (Exception)
            {
                // Logging must never break the game.
            }
        }

        // ------------------------------------------------------------------ internals

        /// <summary>Called by the platform receiver: a new match in the running game (fresh launch data and state).</summary>
        internal static void BeginNewMatch(string launchJson)
        {
            s_initialized = true;
            s_isHosted = true;
            s_rawLaunchJson = launchJson;
            s_launchData = null;
            s_matchStarted = false;
            s_matchStartTime = 0;
            s_scoreSubmitted = false;
            s_quitSent = false;
            s_events.Clear();
            s_eventCapWarned = false;
            s_hudScore = long.MinValue;
            s_hudSeconds = int.MinValue;
            s_hudLives = int.MinValue;
            s_hudCounter = int.MinValue;
            Debug.Log(LogPrefix + "New match from the platform: " + LaunchData);
            try
            {
                OnNewMatch?.Invoke(LaunchData);
            }
            catch (Exception e)
            {
                Debug.LogException(e);
            }
        }

        private static void EnsureInitialized()
        {
            if (s_initialized) return;
            s_initialized = true;
            try
            {
                string json;
                s_isHosted = FpnNativeBridge.DetectHost(out json);
                s_rawLaunchJson = json;
            }
            catch (Exception e)
            {
                s_isHosted = false;
                s_rawLaunchJson = null;
                Debug.LogWarning(LogPrefix + "Host detection failed, running as not hosted: " + e.Message);
            }
        }

        private static void SubmitInternal(long score, long durationMs, string eventLogJson)
        {
            bool submitted = false;
            try
            {
                if (s_scoreSubmitted)
                {
                    Debug.LogWarning(LogPrefix + "SubmitScore called more than once; only the first score (already sent) counts. Ignoring " + score + ".");
                    return;
                }
                if (s_quitSent)
                {
                    Debug.LogWarning(LogPrefix + "SubmitScore ignored: Quit was already sent.");
                    return;
                }

                if (!s_matchStarted) StartMatch();
                if (durationMs < 0) durationMs = ElapsedMs();
                if (eventLogJson == null) eventLogJson = ToJsonArray(s_events);

                if (score < 0) Debug.LogWarning(LogPrefix + "Negative score submitted: " + score);
                if (score > MaxExactScore || score < -MaxExactScore)
                    Debug.LogWarning(LogPrefix + "Score " + score + " exceeds 2^53 and will lose precision in transport.");

                // Mark first so nothing can ever send twice, even if the bridge throws.
                s_scoreSubmitted = true;
                submitted = true;

                var data = LaunchData;
                if (IsHosted)
                {
                    FpnNativeBridge.SendScore(data, score, durationMs, eventLogJson);
                    Debug.Log(LogPrefix + "Score sent to platform: score=" + score + ", durationMs=" + durationMs);
                }
                else
                {
                    Debug.Log(LogPrefix + "(not hosted) Would submit: gameId=" + data.GameId + ", mode=" + data.Mode
                        + ", seed=" + data.Seed + ", ticketId=" + data.TicketId + ", score=" + score
                        + ", durationMs=" + durationMs + ", eventLog=" + eventLogJson);
                }
            }
            catch (Exception e)
            {
                Debug.LogError(LogPrefix + "SubmitScore failed to reach the platform (host bridge missing?): " + e.Message);
            }

            if (!submitted) return;
            var handler = OnScoreSubmitted;
            if (handler == null) return;
            try
            {
                handler(score);
            }
            catch (Exception e)
            {
                Debug.LogException(e);
            }
        }

        private static long ElapsedMs()
        {
            double elapsed = (Time.realtimeSinceStartupAsDouble - s_matchStartTime) * 1000.0;
            if (elapsed < 0 || double.IsNaN(elapsed)) return 0;
            return (long)Math.Round(elapsed);
        }

        private static long ClampDuration(long durationMs)
        {
            if (durationMs >= 0) return durationMs;
            Debug.LogWarning(LogPrefix + "Negative durationMs " + durationMs + " clamped to 0.");
            return 0;
        }

        private static string NormalizeEventLog(string eventLog)
        {
            if (eventLog == null) return null;
            string trimmed = eventLog.Trim();
            if (trimmed.Length >= 2 && trimmed[0] == '[' && trimmed[trimmed.Length - 1] == ']') return trimmed;
            return ToJsonArray(new[] { eventLog });
        }

        internal static string ToJsonArray(IList<string> items)
        {
            var sb = new StringBuilder(2 + items.Count * 8);
            sb.Append('[');
            int count = Math.Min(items.Count, MaxEvents);
            for (int i = 0; i < count; i++)
            {
                if (i > 0) sb.Append(',');
                AppendJsonString(sb, items[i]);
            }
            sb.Append(']');
            return sb.ToString();
        }

        private static void AppendJsonString(StringBuilder sb, string s)
        {
            sb.Append('"');
            if (s != null)
            {
                for (int i = 0; i < s.Length; i++)
                {
                    char c = s[i];
                    switch (c)
                    {
                        case '"': sb.Append("\\\""); break;
                        case '\\': sb.Append("\\\\"); break;
                        case '\n': sb.Append("\\n"); break;
                        case '\r': sb.Append("\\r"); break;
                        case '\t': sb.Append("\\t"); break;
                        case '\b': sb.Append("\\b"); break;
                        case '\f': sb.Append("\\f"); break;
                        default:
                            if (c < ' ' || c == (char)0x2028 || c == (char)0x2029)
                                sb.Append("\\u").Append(((int)c).ToString("x4", CultureInfo.InvariantCulture));
                            else
                                sb.Append(c);
                            break;
                    }
                }
            }
            sb.Append('"');
        }

        private static readonly Regex s_seedRegex =
            new Regex("\"seed\"\\s*:\\s*\"?\\s*(-?[0-9]+)", RegexOptions.CultureInvariant);

        private static FpnLaunchData ParseLaunchData(string json)
        {
            FpnLaunchDataDto dto = null;
            if (string.IsNullOrEmpty(json))
            {
                Debug.LogError(LogPrefix + "Hosted but launch data is empty; using seed 0.");
            }
            else
            {
                try
                {
                    dto = JsonUtility.FromJson<FpnLaunchDataDto>(json);
                }
                catch (Exception e)
                {
                    Debug.LogError(LogPrefix + "Could not parse launch data JSON (" + e.Message + "): " + json);
                }
            }
            if (dto == null) dto = new FpnLaunchDataDto();

            // If the host sent seed as a JSON number, JsonUtility may not fill the string field: fall back to a regex.
            string seedText = dto.seed;
            if (string.IsNullOrEmpty(seedText) && !string.IsNullOrEmpty(json))
            {
                Match m = s_seedRegex.Match(json);
                if (m.Success) seedText = m.Groups[1].Value;
            }

            var data = new FpnLaunchData(dto.gameId, ParseMode(dto.mode), ParseSeed(seedText), dto.ticketId, dto.playerId, dto.displayName);
            Debug.Log(LogPrefix + "Hosted launch data: " + data);
            return data;
        }

        internal static FpnMode ParseMode(string mode)
        {
            if (string.IsNullOrEmpty(mode)) return FpnMode.Practice;
            switch (mode.Trim().ToLowerInvariant())
            {
                case "tournament": return FpnMode.Tournament;
                case "duel": return FpnMode.Duel;
                case "practice": return FpnMode.Practice;
                default:
                    Debug.LogWarning(LogPrefix + "Unknown mode '" + mode + "', treating as Practice.");
                    return FpnMode.Practice;
            }
        }

        /// <summary>
        /// Parses a 64-bit seed string. Accepts signed (long) and unsigned (ulong, reinterpreted bitwise)
        /// decimal values. Anything else is hashed (FNV-1a 64 over UTF-8) so it is still deterministic.
        /// </summary>
        internal static long ParseSeed(string text)
        {
            if (string.IsNullOrEmpty(text))
            {
                Debug.LogError(LogPrefix + "Launch data has no seed; using 0.");
                return 0;
            }
            string s = text.Trim();
            long seed;
            if (long.TryParse(s, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out seed)) return seed;
            ulong useed;
            if (ulong.TryParse(s, NumberStyles.None, CultureInfo.InvariantCulture, out useed)) return unchecked((long)useed);

            Debug.LogWarning(LogPrefix + "Seed '" + text + "' is not a 64-bit integer; hashing it.");
            unchecked
            {
                ulong h = 14695981039346656037UL;
                byte[] bytes = Encoding.UTF8.GetBytes(s);
                for (int i = 0; i < bytes.Length; i++)
                {
                    h ^= bytes[i];
                    h *= 1099511628211UL;
                }
                return (long)h;
            }
        }

        private static FpnLaunchData CreateRandomTestLaunchData()
        {
            // Randomness here only picks a test seed; it never affects determinism of a given seed.
            long seed = BitConverter.ToInt64(Guid.NewGuid().ToByteArray(), 0);
            return new FpnLaunchData("test-game", FpnMode.Practice, seed, "test-ticket", "test-player", "Test Player");
        }
    }
}
