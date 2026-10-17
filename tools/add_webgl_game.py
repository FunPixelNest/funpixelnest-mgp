#!/usr/bin/env python3
"""
Add a Unity WebGL build to the FunPixelNest games catalog.

    python3 tools/add_webgl_game.py <game_id> <build.zip or build folder> [--games-dir DIR] [--version v1]

- Copies the build into <games-dir>/<game_id>/<version>/ (default games dir: composeApp/src/wasmJsMain/resources/games,
  which the web app serves at "games/"; for production upload the same folder layout to Cloudflare R2).
- Finds the Unity files in Build/ (loader, data, framework, wasm) and writes/updates <games-dir>/catalog.json.
- Warns when the build needs special server headers (compressed without "Decompression Fallback").

game_id must match the app catalog (e.g. solitaire, bubble_shooter, mahjong, block_puzzle, bingo, catdoku, ...).
"""
import argparse
import json
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

KNOWN_IDS = {
    "solitaire", "block_puzzle", "bingo", "bubble_shooter", "mahjong", "blackjack", "arrows", "slots",
    "snake_war", "flap_dash", "kitty_hole", "catdoku", "rugby_rumble",
}


def find_build_root(path: Path) -> Path:
    """The folder that contains Build/*.loader.js (zips often wrap the build in an extra folder)."""
    for loader in sorted(path.rglob("*.loader.js")):
        if loader.parent.name == "Build":
            return loader.parent.parent
    sys.exit(f"Couldn't find Build/*.loader.js inside {path}. Is this a Unity WebGL build?")


def pick(files, pattern):
    matches = [f for f in files if re.search(pattern, f.name)]
    return matches[0] if matches else None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("game_id")
    parser.add_argument("source", type=Path, help="Unity WebGL build .zip or folder")
    parser.add_argument("--games-dir", type=Path, default=Path("composeApp/src/wasmJsMain/resources/games"))
    parser.add_argument("--version", default="v1")
    parser.add_argument("--name", help="Product name shown while loading (default: from the loader file name)")
    args = parser.parse_args()

    if args.game_id not in KNOWN_IDS:
        print(f"Warning: '{args.game_id}' isn't a game id in the app catalog ({', '.join(sorted(KNOWN_IDS))}).")

    with tempfile.TemporaryDirectory() as tmp:
        src = args.source
        if src.is_file() and src.suffix.lower() == ".zip":
            with zipfile.ZipFile(src) as z:
                z.extractall(tmp)
            src = Path(tmp)
        build_root = find_build_root(src)

        dest = args.games_dir / args.game_id / args.version
        if dest.exists():
            shutil.rmtree(dest)
        dest.mkdir(parents=True)
        shutil.copytree(build_root / "Build", dest / "Build")
        if (build_root / "StreamingAssets").exists():
            shutil.copytree(build_root / "StreamingAssets", dest / "StreamingAssets")

    build_files = sorted((dest / "Build").iterdir())
    loader = pick(build_files, r"\.loader\.js$")
    data = pick(build_files, r"\.data(\.unityweb|\.gz|\.br)?$")
    framework = pick(build_files, r"\.framework\.js(\.unityweb|\.gz|\.br)?$")
    code = pick(build_files, r"\.wasm(\.unityweb|\.gz|\.br)?$")
    missing = [n for n, f in [("loader", loader), ("data", data), ("framework", framework), ("wasm", code)] if f is None]
    if missing:
        sys.exit(f"Build/ is missing: {', '.join(missing)}. Files found: {[f.name for f in build_files]}")

    loader_text = loader.read_text(errors="ignore")
    compressed = any(f.name.endswith((".gz", ".br")) for f in (data, framework, code))
    has_fallback = any(f.name.endswith(".unityweb") for f in (data, framework, code)) or "decompressionFallback" in loader_text
    if compressed and not has_fallback:
        print("Warning: this build is compressed (.gz/.br) without 'Decompression Fallback'.\n"
              "  It only loads from a server that sends Content-Encoding headers. Rebuild with\n"
              "  Player Settings → Publishing Settings → Decompression Fallback ON for easy hosting.")

    product = args.name or loader.name.replace(".loader.js", "")
    entry = {
        "path": f"{args.game_id}/{args.version}/",
        "loaderUrl": f"Build/{loader.name}",
        "dataUrl": f"Build/{data.name}",
        "frameworkUrl": f"Build/{framework.name}",
        "codeUrl": f"Build/{code.name}",
        "streamingAssetsUrl": "StreamingAssets",
        "companyName": "FunPixelNest",
        "productName": product,
        "productVersion": args.version,
    }

    catalog_path = args.games_dir / "catalog.json"
    catalog = json.loads(catalog_path.read_text()) if catalog_path.exists() else {"devControls": True, "games": {}}
    catalog.setdefault("games", {})[args.game_id] = entry
    catalog_path.write_text(json.dumps(catalog, indent=2) + "\n")

    total = sum(f.stat().st_size for f in dest.rglob("*") if f.is_file())
    print(f"Added {args.game_id} ({product}) → {dest}  [{total / 1_048_576:.1f} MB]")
    for f in (loader, data, framework, code):
        print(f"  {f.name:50s} {f.stat().st_size / 1_048_576:7.2f} MB")


if __name__ == "__main__":
    main()
