# FunPixelNest game host (Cloudflare Pages)

Everything in this folder is published by Cloudflare Pages project **funpixelnest-games**
(`https://funpixelnest-games.pages.dev/`) on every push to `main`.

- `catalog.json` — games that have a build (written by `tools/add_webgl_game.py`).
- `<game_id>/<version>/Build/...` — Unity WebGL builds. Use Gzip/Brotli **with Decompression Fallback**, or no compression.
- `play.html` — the player page the Android app opens in a WebView (`play.html#<launch JSON>`).
- `fpn-unity-host.js` — copy of `composeApp/src/wasmJsMain/resources/fpn-unity-host.js` (keep them identical).
- `_headers` — CORS so the web app can load games from another domain.

Add a game: `python3 tools/add_webgl_game.py block_puzzle /path/to/WebGLBuild` then commit and push.

Cloudflare setup (once): Workers & Pages → Create → Pages → Connect to Git → FunPixelNest/funpixelnest-mgp →
project name `funpixelnest-games`, production branch `main`, framework None, build command empty,
**build output directory `games-host`**.
