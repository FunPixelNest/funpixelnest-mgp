#!/usr/bin/env bash
# Stores a new Unity library in git as pieces (GitHub refuses files over 100 MB).
# Usage: tools/split-unity-library.sh path/to/unityLibrary-release.aar path/to/unity-classes.jar
# The build (composeApp/build.gradle.kts) joins the pieces back into composeApp/unity/unityLibrary-release.aar.
set -euo pipefail
AAR="$1"; JAR="$2"
cd "$(dirname "$0")/.."
DIR=composeApp/unity/parts
mkdir -p "$DIR"
git rm -q --ignore-unmatch "$DIR"/unityLibrary-release.aar.part* || true
rm -f "$DIR"/unityLibrary-release.aar.part*
split -b 45m -d -a 2 "$AAR" "$DIR/unityLibrary-release.aar.part"
sha256sum "$AAR" | cut -d' ' -f1 > "$DIR/unityLibrary-release.aar.sha256"
[ "$(realpath "$JAR")" = "$(realpath composeApp/unity/unity-classes.jar)" ] || cp "$JAR" composeApp/unity/unity-classes.jar
[ "$(realpath "$AAR")" = "$(realpath -m composeApp/unity/unityLibrary-release.aar)" ] || cp "$AAR" composeApp/unity/unityLibrary-release.aar   # local copy for builds (not in git)
ls -l "$DIR"
