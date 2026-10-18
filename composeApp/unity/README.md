# Native games (Unity as a Library)

Put the game library here to play the games inside it natively (instead of WebGL):

- `unityLibrary-release.aar`
- `unity-classes.jar`

Both come from the Unity project **FPN/FunPixelNestGames** (all native games: Block Puzzle, Arrow Duel, ...):
*Build Settings → Android → Export Project* into `FunPixelNestGamesAndroidProject`, then double-click
**Build for FunPixelNest App.command** next to it. The files appear in `ForFunPixelNestApp/`.

Games inside the library are listed in `UnityGameLauncher.GAMES` (`src/androidUnity/...`).
Without these files the app builds normally and all games use WebGL.
Only one Unity library can be in the app: every native game must be a scene in the same Unity project.
