# FunPixelNest SDK for Unity

`com.funpixelnest.sdk` lets a Unity game run inside the FunPixelNest platform app. With it, a game can:

- read the match **launch data** (game, mode, seed, ticket, player),
- build its board or deck from the match **seed**, so every player in the match gets the identical game,
- report the result **once**, either as a score or as a quit.

Supported targets: **WebGL** (the game is loaded into the platform web page) and **Android/iOS** (Unity as a Library inside the native app). In the Editor and standalone builds the SDK runs in "not hosted" mode. It uses test data there and logs what it would send.

Requires Unity 2021.3 or newer (Unity 6 supported). It has no dependencies.

---

## Install

Pick one of these:

- **Embedded:** copy the `FunPixelNestSDK` folder into your project's `Packages/` folder.
- **From disk:** open *Window > Package Manager*, click *+*, choose *Add package from disk…*, and select `FunPixelNestSDK/package.json`.

To get the sample, open *Package Manager > FunPixelNest SDK > Samples* and import **Quick Start**.

> Unity generates `.meta` files the first time it imports the package. Commit them so the GUIDs stay stable.

---

## Integration in 4 steps

```csharp
using FunPixelNest;

// 1. Read the launch data
FpnLaunchData data = FpnPlatform.LaunchData;   // data.Mode, data.Seed, data.DisplayName, ...

// 2. Build the board from the seed, using FpnRandom only
FpnRandom rng = FpnRandom.FromLaunch();        // same as new FpnRandom(data.Seed)
rng.Shuffle(deck);

// 3. Start the clock when the player can first act
FpnPlatform.StartMatch();

// 4. When the game ends, report exactly once
FpnPlatform.SubmitScore(finalScore);           // or: FpnPlatform.Quit();  if the player leaves early
```

The SDK can also send a move log with the score:

```csharp
FpnPlatform.AddEvent("m:3>7");   // keep entries short; at most 500 are kept
```

To show a "waiting for platform…" screen once the score has been sent, subscribe to `FpnPlatform.OnScoreSubmitted`.

### Rules (required for fair, paid matches)

1. **Submit exactly once.** Only the first `SubmitScore` is sent. Later calls are ignored and log a warning. `Quit` is ignored after a score has been submitted, and the reverse is also true.
2. **The score must depend on skill only.** It may depend on the seeded board and the player's actions. It must not depend on luck rolled at runtime, device speed or frame rate.
3. **Same seed means same board.** Two players with the same seed must see exactly the same deal, layout and spawn order, along with any other random content.
4. **Use `FpnRandom` for anything that affects the board or the score.** Do not use `UnityEngine.Random`, `System.Random`, `Time`, `DateTime`, `Guid` or the device's frame rate for it. Cosmetic effects such as particles and card wobble may use any RNG.
5. If the player exits mid-match, call `FpnPlatform.Quit()`. For paid matches the platform scores this as **0**.
6. Use `long` for scores and keep them below 2^53. They travel as JS numbers or doubles.

---

## API reference (namespace `FunPixelNest`)

### `static class FpnPlatform`

| Member | Description |
|---|---|
| `bool IsHosted` | True inside the platform: WebGL with `window.FPN`, or Android/iOS launched with launch data. Always false in the Editor. |
| `FpnLaunchData LaunchData` | Launch data for this match. Never null. When not hosted, it returns the test data (see below). |
| `void SetTestLaunchData(FpnLaunchData data)` | Sets the test data used when not hosted. Pass `null` to go back to a random seed. Ignored when hosted. |
| `void SetTestLaunchData(long seed, FpnMode mode = Practice, string gameId = "test-game")` | Shorthand for the above. |
| `void StartMatch()` | Records the start time for the reported duration. If you never call it, `SubmitScore` calls it and the duration is 0. |
| `void AddEvent(string e)` | Adds an entry to the move log sent with the score. The log is capped at `MaxEvents` (500). |
| `void SubmitScore(long score)` | Duration is measured from `StartMatch`. The event log is the `AddEvent` log. |
| `void SubmitScore(long score, long durationMs)` | Uses an explicit duration. |
| `void SubmitScore(long score, IList<string> eventLog)` / `(long, long, IList<string>)` | Uses an explicit event log, which replaces the `AddEvent` log. |
| `void SubmitScore(long score, string eventLog)` / `(long, long, string)` | Takes the log as a string. A JSON array string is sent as is. Any other string is wrapped as `["…"]`. |
| `void Quit()` | The player left without finishing. |
| `void Log(string message)` | Writes to the Unity console. On hosted WebGL it also calls `window.FPN.log`. |
| `bool ScoreSubmitted` | True once a score has been submitted. |
| `event Action<long> OnScoreSubmitted` | Raised after the score is handed to the platform, or logged when not hosted. |
| `const int MaxEvents = 500` | |

None of these members throw. If the host bridge is missing, the call logs an error and the game keeps running.

### `sealed class FpnLaunchData`
Has `GameId`, `Mode` (`FpnMode.Practice | Tournament | Duel`), `Seed` (`long`), `TicketId`, `PlayerId`, `DisplayName` and `IsCompetitive`. It also has a public constructor for tests.

### `sealed class FpnRandom`
`new FpnRandom(long seed)`, `static FromLaunch()`, `Seed`, `ulong NextULong()`, `int Next(int maxExclusive)`, `int Next(int min, int maxExclusive)`, `float NextFloat()` (range [0,1)), `double NextDouble()`, `bool NextBool()`, `void Shuffle<T>(IList<T>)`.

The algorithm is xoshiro256\*\*, seeded with four SplitMix64 outputs from `(ulong)seed`.
- **Bounded draws** use rejection sampling: the threshold is `2^64 mod n`, and a draw is accepted when `r >= threshold`. The result is `r mod n`.
- **`Next(0)` and `Next(1)`** return 0 and consume nothing.
- **`Shuffle`** is a Fisher–Yates shuffle: for `i = n-1` down to `1`, it draws `j = Next(i+1)` and swaps items `i` and `j`.

The class comments describe the full algorithm, so a server can re-implement it and re-check a board or a replay. Reference values below were checked against an independent BigInt implementation:

| seed | first 3 `NextULong()` | `Shuffle` of `[0..9]` |
|---|---|---|
| `0` | 11091344671253066420, 13793997310169335082, 1900383378846508768 | `[4,2,1,7,5,6,3,9,8,0]` |
| `8461298374512398123` | 1833595613455677703, 6358452137829128722, 2627425270001732784 | `[6,7,2,9,1,5,8,0,4,3]` |
| `-1` | 10328197420357168392, 14156678507024973869, 9357971779955476126 | `[1,5,4,9,3,0,7,6,8,2]` |

---

## Testing in the Editor

In the Editor `IsHosted` is false. `LaunchData` is Practice mode with a **random** seed, and `SubmitScore`/`Quit` print what they would send:

```
[FPN] (not hosted) Would submit: gameId=test-game, mode=Practice, seed=..., score=1234, durationMs=56789, eventLog=[...]
```

To reproduce a board, or to test the tournament or duel UI, set test data **before** anything reads `LaunchData`, for example in `Awake` of a bootstrap object:

```csharp
void Awake()
{
    if (!FpnPlatform.IsHosted)
        FpnPlatform.SetTestLaunchData(8461298374512398123L, FpnMode.Tournament, "solitaire");
}
```

The same applies to a WebGL build opened outside the platform page, where `window.FPN` is missing.

---

## WebGL build settings

*Project Settings > Player > WebGL > Publishing Settings*:

- **Compression Format:** Gzip or Brotli.
- **Decompression Fallback:** ON. The platform page may serve files without `Content-Encoding` headers.
- **WebGL Template:** Default or Minimal. The platform page hosts the canvas.
- **Run In Background:** not needed. Leave it at its default.

The bridge lives in `Runtime/Plugins/WebGL/FunPixelNestBridge.jslib` and is picked up automatically for WebGL builds.

### JS contract (host page)

The host page must set this object **before** it starts the Unity loader:

```js
window.FPN = {
  launchData: '{"gameId":"solitaire","mode":"tournament","seed":"8461298374512398123","ticketId":"…","playerId":"…","displayName":"Ravi"}',
  // JSON string. mode is "tournament" | "duel" | "practice". seed is a STRING (64-bit; signed or unsigned decimal).
  submitScore: function (score /*number*/, durationMs /*number*/, eventLogJson /*string, JSON array*/) {},
  quit: function () {},           // player left without finishing (score 0 for paid matches)
  log: function (message) {},     // optional debug log
};
```

Every bridge function checks for `window.FPN` and its members. Anything missing is a no-op.

---

## Android / iOS (Unity as a Library)

This path is ready in code. It will be finished during native integration.

- **Android:**
  - **Launch data:** the SDK reads the launching Activity's intent extra `"fpn_launch_data"` (a JSON string) through `UnityPlayer.currentActivity.getIntent()`.
  - **Results:** they go to the static Java methods
    `com.funpixelnest.mgp.unity.FpnUnityBridge.onScore(String gameId, String ticketId, long score, long durationMs, String eventLogJson)` and
    `FpnUnityBridge.onQuit(String ticketId)`.
  - **Missing class:** if the class is absent, the SDK only logs.
  - **Caching:** launch data is read and cached on first access. Start a fresh Unity activity for each match.
- **iOS:**
  - **Bridge:** `Runtime/Plugins/iOS/FunPixelNestBridge.mm`.
  - **Before showing Unity:** the host calls `FPN_iOS_SetLaunchData(const char* json)`, then `FPN_iOS_SetHandlers(onScore, onQuit)` to register the C callbacks:
    `void onScore(double score, double durationMs, const char* eventLogJson)` and `void onQuit(void)`.
  - **Early results:** if Unity reports before the handlers are set, the result is held and delivered when they are set.

---

## Migrating from the Skillz SDK

| Skillz | FunPixelNest |
|---|---|
| `SkillzCrossPlatform.LaunchSkillz()` / `OnMatchWillBegin(Match)` | The platform launches the game. Read `FpnPlatform.LaunchData` at scene start. |
| `match.IsCustomSynchronousMatch` / tournament info | `FpnPlatform.LaunchData.Mode` (`Tournament`, `Duel`, `Practice`) |
| `SkillzCrossPlatform.Random.Value()` / `Range(min, max)` | `FpnRandom`: `NextFloat()` / `Next(min, max)` (create with `FpnRandom.FromLaunch()` and keep one instance) |
| `SkillzCrossPlatform.ReportFinalScore(score)` / `SubmitScore` + `DisplayTournamentResultsWithScore` | `FpnPlatform.SubmitScore(score)`, then show your own "waiting for platform…" text via `OnScoreSubmitted` |
| `SkillzCrossPlatform.AbortMatch()` | `FpnPlatform.Quit()` |
| `SkillzCrossPlatform.UpdatePlayersCurrentScore` | Not needed. Optionally use `FpnPlatform.AddEvent` for a move log. |

Skillz's RNG produces a different sequence, so boards will not match old Skillz seeds. Remove the Skillz SDK and its `Plugins` before building for FunPixelNest.

---

## Pause, Back button and settings

The platform app owns the pause menu. When the player presses **Back** (Android, browser) or **Pause**, the app
hides the game, shows *Game Paused* (music / sound / vibration toggles, How to play, Resume, End now) and gives
the player **5 minutes** to resume; after that, or on *End now*, the match ends (a paid match counts as score 0).

The SDK handles this for you: while paused it sets `Time.timeScale = 0` and pauses audio, and restores both on
resume. To react yourself:

```csharp
FpnSession.OnPaused   += () => myTimer.Stop();
FpnSession.OnResumed  += () => myTimer.Start();
FpnSession.OnSettingsChanged += s => { music.mute = !s.music; sfx.mute = !s.sound; };
// FpnSession.AutoPause = false;   // if you want to handle time/audio completely yourself
```

The starting values are also in the launch data (`music`, `sound`, `vibration`), and in `FpnSession.Settings`
once the platform sends them. Don't add your own in-game Back/Exit button for paid matches; the platform's pause
menu is the way out.

## App HUD (pause, score, timer, lives)

Inside the FunPixelNest app the **app draws the HUD for every game**: a pause button, lives, score and time
left, floating over the top of the game. The pause button and the phone's Back button open the app's pause
menu (the SDK pauses your game through `FpnSession`).

When `FpnPlatform.AppShowsHud` is true:

1. Hide your own HUD and pause button.
2. Keep the top `FpnPlatform.AppInsetTop` (share of screen height) free of gameplay and buttons. Your background
   can run underneath; the HUD is see-through. Keep the bottom `FpnPlatform.AppInsetBottom` clear as well.
3. Every frame: `FpnPlatform.UpdateHud(score, secondsLeft, lives);`
   Pass `-1` for `secondsLeft` or `lives` if the game has no timer or lives; the HUD shows them greyed out.
   Only changes are sent, so calling it every frame is fine.

## Inside the Android app (Unity as a Library)

The app can play games natively instead of WebGL. Unity runs inside the app's own screen, under the app's
HUD and pause menu, and **stays loaded between matches**:

- The first match's launch data comes from the activity intent (`fpn_launch_data`).
- Each later match arrives as the `OnPlatformLaunch` message: the SDK resets its state and raises
  `FpnPlatform.OnNewMatch`. **Reload your game scene there.**
- Score / quit / HUD values go to `com.funpixelnest.mgp.unity.FpnUnityBridge` in the app.
- Don't call `Application.Quit()` inside the app (it would close the whole app); use `FpnPlatform.Quit()`.

Only one Unity library fits in an app, so every native game must be a scene in the same Unity project.
