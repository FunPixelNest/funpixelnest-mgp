# FunPixelNest game host (Cloudflare Pages)

Everything in this folder is published by CI (GitHub Actions) to the `games` branch, which GitHub Pages serves at
**https://funpixelnest.github.io/funpixelnest-mgp/** (one-time: repo Settings → Pages → Deploy from branch → `games` / root).
GitHub Pages sends `Access-Control-Allow-Origin: *`, so the web app can load the games too.

- `catalog.json` — games that have a build (written by `tools/add_webgl_game.py`).
- `<game_id>/<version>/Build/...` — Unity WebGL builds. Use Gzip/Brotli **with Decompression Fallback**, or no compression.
- `play.html` — the player page the Android app opens in a WebView (`play.html#<launch JSON>`).
- `fpn-unity-host.js` — copy of `composeApp/src/wasmJsMain/resources/fpn-unity-host.js` (keep them identical).
- `_headers` — CORS so the web app can load games from another domain.

Add a game: `python3 tools/add_webgl_game.py block_puzzle /path/to/WebGLBuild` then commit and push.

Moving to Cloudflare later (optional, for heavy traffic): Workers & Pages → Create → Pages → Connect to Git → FunPixelNest/funpixelnest-mgp →
project name `funpixelnest-games`, production branch `main`, framework None, build command empty,
**build output directory `games-host`**.
