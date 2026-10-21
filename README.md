# FunPixelNest MGP — Multi Gaming Platform

One app, many skill games: 5-player tournaments and async 1v1 duels. Android, iOS and Web from a
single Kotlin Multiplatform codebase, games built in Unity, backend on Supabase.

> **Status:** the app, backend and Unity bridge are built. It runs on a built-in **demo backend**
> until you add your Supabase keys, and plays any Unity WebGL build you add to the games catalog.

## How it fits together

```
┌──────────────────────────── composeApp (Kotlin Multiplatform) ────────────────────────────┐
│  Shared Compose UI: Rewards · Store · Games · Featured · Profile, duels, practice, results │
│  domain/   Tournament (pool or duel), Pool, ranking + prize split                          │
│  data/     PlatformRepository ← Supabase (real) or Fake (demo), picked by BackendConfig     │
│  game/     GameLauncher       ← the one seam to Unity                                      │
└───────────────┬───────────────────────────┬───────────────────────────┬────────────────────┘
          androidMain                   iosMain                     wasmJsMain
   MainActivity + FCM push      MainViewController → iosApp     fpn-unity-host.js loads
   (Unity as a Library later)   (UnityFramework later)          Unity WebGL builds

supabase/                Postgres schema + coin-safe functions, push sender (Edge Function), tests
unity/FunPixelNestSDK/   Unity package games use: launch data, seed RNG, SubmitScore, Quit
tools/add_webgl_game.py  adds a Unity WebGL build to the games catalog
```

## Competition rules

| | 5-player tournament | 1v1 duel (async) |
|---|---|---|
| Players | 5, same seed | 2, same seed |
| When | No timeout; closes when all have submitted | Play now, matched with the next player |
| Prizes | 1st **50%**, 2nd **30%**, 3rd the **remaining 20%** | Winner takes the pot |
| Platform fee | `platformFeePercent` per tournament, taken first (10% placeholder) | same |
| Ties | Higher score → shorter time → earlier submission | same |
| Quit | Counts as score 0 (entry is paid) | same |

The **server** ranks and pays (`supabase/migrations`). The app only displays results. Free **practice**
rounds cost nothing and don't count.

## Configuration (`local.properties`, never committed)

```properties
# Backend — without these the app uses the demo backend
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJ...

# Where WebGL game builds are served from (default: games/ next to the web app; later your R2 domain)
GAMES_BASE_URL=games/

# Android push (from Firebase console → Project settings → your Android app)
FIREBASE_PROJECT_ID=your-project
FIREBASE_API_KEY=AIza...
FIREBASE_ANDROID_APP_ID=1:1234567890:android:abc123
FIREBASE_SENDER_ID=1234567890
```

The same names work as environment variables (e.g. GitHub Actions secrets). Gradle turns them into
`BackendConfig` at build time.

## Setting things up

1. **Backend:** follow [`supabase/README.md`](supabase/README.md): create the project, run the 3 migrations,
   enable anonymous sign-ins, then put the URL and anon key in `local.properties`.
2. **Games (web):** build each game for WebGL with **Compression: Gzip + Decompression Fallback ON**, then
   ```bash
   python3 tools/add_webgl_game.py solitaire path/to/SolitaireWebGL.zip
   ```
   This copies it to `composeApp/src/wasmJsMain/resources/games/solitaire/v1/` and updates `games/catalog.json`.
   For production, upload the same `games/` folder to Cloudflare R2 and set `GAMES_BASE_URL`, and set
   `"devControls": false` in the catalog (it shows a "Test finish" button for testing).
3. **Unity side:** add [`unity/FunPixelNestSDK`](unity/FunPixelNestSDK/README.md) to each game: read
   `FpnPlatform.LaunchData`, seed the board with `FpnRandom`, call `FpnPlatform.SubmitScore(score)` once.
4. **Push:** create a Firebase project, add the Android app (`com.funpixelnest.mgp`), copy the 4 values above,
   and set up the server side as described in `supabase/README.md`.

## Running it

**Requirements:** JDK 17+, Android Studio (latest stable) with the Kotlin Multiplatform plugin.
iOS additionally needs a Mac with Xcode 16+.

| Platform | Command |
|---|---|
| Android | Run **composeApp** in Android Studio, or `./gradlew :composeApp:assembleDebug` |
| Web | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| iOS (Mac) | `brew install xcodegen && cd iosApp && xcodegen generate && open iosApp.xcodeproj` |
| Tests | `./gradlew :composeApp:testDebugUnitTest` (app) · `supabase/tests/run_tests.sh` (backend) |

CI builds Android, Web and the iOS framework on every push. The latest web build and debug APK are
published to the `preview` branch; failing builds publish their log to `ci-logs-*` branches.

## App navigation

Bottom bar: **Rewards · Store · Games · Featured · Profile** (Games is home, in the center).

| Tab | What's there |
|---|---|
| Rewards | 7-day login streak, free coins from videos (3/day), missions |
| Store | Coin packs (demo: added free; real billing comes later) |
| Games | All games, 2 per row, category filter → game page with Practice, 1v1 Duels, 5-player tournaments |
| Featured | Banner, new games, big prize events |
| Profile | Name (editable), stats, balance, my tournaments and duels, coin history |

## Demo backend

With no Supabase keys the app runs on `FakePlatformRepository`: you start with 1,000 coins and other
players are **simulated "Demo" players** so you can see pools and duels fill up. This exists only for
development. The real backend never adds bots.

## Roadmap

- [x] App shell: shared UI, tournaments, leaderboards, wallet, rewards, store, theme, icons
- [x] Async 1v1 duels, free practice mode
- [x] Backend: Supabase schema + server-side rules, guest sign-in, realtime leaderboard
- [x] Push: Android client + server sender (iOS and web push later)
- [x] Web: Unity WebGL loader + bridge; Unity SDK package
- [ ] Connect your Supabase project and Firebase project (needs your accounts)
- [ ] Add your games' WebGL builds; integrate the Unity SDK in each game
- [ ] Android/iOS: Unity as a Library (native) launcher
- [ ] Google / Apple sign-in, real store billing, admin panel, analytics
- [ ] Real money: legal review, payments/KYC, geofencing (separate workstream)

## Conventions

- Package / bundle ID: `com.funpixelnest.mgp`
- Game ids and tournament ids in `SampleCatalog` must match `supabase/migrations/*_seed.sql` and the Unity games.
- "Flappy Bird" is listed as **Flap Dash** (the original name is trademarked).
- The web version can't show emoji (it draws text with the app's fonts), so the UI uses drawn icons; put
  coins in text with `CoinText("… 🪙 …")`.

## Ads, analytics and Remove Ads (Android)

- **AdMob** (`play-services-ads`) with mediation adapters for **Liftoff Monetize** (`vungle`), **Unity Ads** and **Mintegral**.
  Ad unit ids live in `composeApp/src/commonMain/.../ads/Ads.kt` (`AdUnits`) and the app id in `AndroidManifest.xml`;
  both are Google's public **test** ids for now — replace them before release. Add Liftoff / Unity / Mintegral to each
  ad unit's mediation group in the AdMob dashboard (no code change).
- **Placements:** banner under the game while playing; interstitial over the leaderboard after submitting a score;
  rewarded video for Watch Video and for "Watch video · 2X" on reward pop-ups (welcome, daily bonus, challenges,
  chests, lucky spin, level up). The 2X reward is paid by the `double_reward` database function (migration `20261021000002`).
- **Firebase Remote Config** keys (defaults in `AdSettings`): `show_banner`, `show_interstitial`,
  `interstitial_interval_seconds`, `interstitial_every_n_games`, `show_rewarded`, `rewarded_double_enabled`.
- **Firebase Analytics + Crashlytics** start when `FIREBASE_PROJECT_ID`, `FIREBASE_API_KEY` and `FIREBASE_ANDROID_APP_ID`
  are set (local.properties or config/backend.properties); without them they stay off and ads use the defaults.
- **Remove Ads**: one-time Google Play in-app product `remove_ads` (create it in Play Console). Turns off banner and
  interstitial ads; rewarded videos stay optional. Restored from Google Play on every start ("Restore purchase" too).
