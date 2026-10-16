# FunPixelNest MGP — Multi Gaming Platform

One app, many skill games, 5-player tournaments. Android, iOS and Web from a single
Kotlin Multiplatform codebase, with the games themselves built in Unity.

> **Status:** app shell (phase 1). Lobby, tournaments, leaderboards and wallet work
> end-to-end against an in-memory backend. Unity games and the real server come next.

## How it fits together

```
┌──────────────────────────── composeApp (Kotlin Multiplatform) ────────────────────────────┐
│  Shared Compose UI: lobby · game detail · tournaments · leaderboard · wallet · profile     │
│  domain/   Tournament, Pool, ranking + 50/30/20 prizes                                     │
│  data/     PlatformRepository  ←  FakePlatformRepository today, Nakama next                │
│  game/     GameLauncher        ←  the one seam to Unity                                    │
└───────────────┬───────────────────────────┬───────────────────────────┬────────────────────┘
          androidMain                   iosMain                     wasmJsMain
       MainActivity (APK)        MainViewController → iosApp     ComposeViewport (browser)
     + Unity as a Library        + UnityFramework (later)        + Unity WebGL (later)
```

## Tournament rules (implemented in `domain/Leaderboard.kt`)

| Rule | Value |
|---|---|
| Players per pool | 5, all get the **same seed** (identical board/deal) |
| Timeout | **None** — a pool stays open until all 5 have submitted |
| Prizes | 1st **50%**, 2nd **30%**, 3rd gets the **remaining 20%** (absorbs rounding) |
| Platform fee | Configurable per tournament (`platformFeePercent`), taken before prizes. Sample data uses 10% as a placeholder |
| Tie-breakers | Higher score → shorter duration → earlier submission (so ties can't happen) |
| Quit mid-game | Counts as score 0 (entry is already paid) |
| One ticket = one submission | Enforced in the repository; must be enforced on the server too |

The app only *displays* rankings. In production the **server** ranks pools and pays prizes.

## Project layout

```
composeApp/
  src/commonMain/kotlin/com/funpixelnest/mgp/
    App.kt                    root composable + navigation wiring
    domain/                   models, Leaderboard (ranking + payouts)
    data/                     PlatformRepository, FakePlatformRepository, SampleCatalog
    game/GameLauncher.kt      interface Unity will implement on each platform
    ui/                       theme, navigator, components, screens/
  src/commonTest/             LeaderboardTest (prize split, tie-breakers)
  src/androidMain/            MainActivity, manifest
  src/iosMain/                MainViewController (called from Swift)
  src/wasmJsMain/             main.kt, index.html
iosApp/                       SwiftUI host + XcodeGen spec (project.yml)
.github/workflows/build.yml   CI: tests, Android APK, Web, iOS framework
```

## Running it

**Requirements:** JDK 17+, Android Studio (latest stable) with the Kotlin Multiplatform plugin.
iOS additionally needs a Mac with Xcode 16+.

### Android
Open the project folder in Android Studio and run the **composeApp** configuration, or:
```bash
./gradlew :composeApp:assembleDebug
```

### Web
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```
Opens the app in your browser (needs a WasmGC-capable browser: current Chrome, Firefox, Safari, Edge).

### iOS (on a Mac)
```bash
brew install xcodegen
cd iosApp && xcodegen generate
open iosApp.xcodeproj
```
Set your team under *Signing & Capabilities* and run. The Xcode build calls Gradle to compile the shared Kotlin framework automatically.

### Tests
```bash
./gradlew :composeApp:testDebugUnitTest
```

## Trying the demo flow

1. **Play** tab → pick a game → **Join** a tournament (you start with 1,000 coins).
2. The placeholder game screen shows exactly what Unity will receive (game id, seed, ticket). Tap **Finish & submit demo score**.
3. Watch the 5-player leaderboard fill in. Other players are **simulated "Demo" players** — this exists only in `FakePlatformRepository` for UI development. The real backend never adds bots.
4. When all 5 finish, ranks lock and the top 3 are paid into the wallet.

## Roadmap

- [x] **Phase 1 — App shell:** KMP project, shared UI, tournaments, leaderboard, wallet (in-memory)
- [ ] **Phase 2 — Unity integration:** decide per platform (Unity as a Library vs WebGL), implement `GameLauncher`, port games to a common launch/score interface
- [ ] **Phase 3 — Backend:** Nakama server (pools, seeds, tickets, ranking, payouts), auth, real-time leaderboard updates
- [ ] **Phase 4 — Product:** free practice mode, Addressables game downloads, push notifications, analytics, admin panel
- [ ] **Phase 5 — Real money:** legal review, payments/KYC, geofencing (separate workstream)

## Conventions

- Package / bundle ID: `com.funpixelnest.mgp`
- Game ids (`SampleCatalog`) are the contract with Unity: the Unity project must use the same ids.
- "Flappy Bird" is listed as **Flap Dash** (the original name is trademarked).
