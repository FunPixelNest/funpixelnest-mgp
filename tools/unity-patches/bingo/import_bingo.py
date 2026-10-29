#!/usr/bin/env python3
"""Adds Bingo Flash (FPN/BingoFlash) to the combined FunPixelNest games project (FPN/FunPixelNestGames).

  python3 import_bingo.py <BingoFlash project dir> <FunPixelNestGames project dir> <dir with FpnBingoLink.cs>

Idempotent (safe to run again). What it does:
  1. Copies Assets/0_BingoFlash -> Assets/BingoFlash with its .meta files (asset ids kept, so the scene's references
     stay intact). Bingo's Editor tools are left out: they rebuild scenes and change Build / Player settings.
  2. Moves Bingo's Resources into Resources/BingoFlash/ and updates the load paths (another game also has an
     Audio/sfx_countdown_go).
  3. Copies the Gameplay scene as Assets/BingoFlash/Scenes/BingoFlashGame.unity (no .meta: Unity gives it a fresh
     id; the original has Unity's template id, which another scene already uses) and the iOS haptics plugin.
  4. Adds the assembly definition FunPixelNest.BingoFlash and the app link (FpnBingoLink.cs).
  5. Hooks the game up to the app (seeded random, HUD, layout, pause/back, settings, score) and registers the
     scene with the router (game id "bingo") and the build scene list.
"""
import os
import shutil
import sys

SRC, DST, LINKDIR = sys.argv[1], sys.argv[2], sys.argv[3]
A_SRC = os.path.join(SRC, 'Assets')
A_DST = os.path.join(DST, 'Assets')
BF = os.path.join(A_DST, 'BingoFlash')
changed = []


def copytree(src, dst, skip=()):
    for root, dirs, files in os.walk(src):
        rel = os.path.relpath(root, src)
        if any(rel == s or rel.startswith(s + os.sep) for s in skip):
            continue
        os.makedirs(os.path.join(dst, rel), exist_ok=True)
        for f in files:
            relf = os.path.normpath(os.path.join(rel, f))
            if any(relf == s or relf == s + '.meta' for s in skip):
                continue
            target = os.path.join(dst, relf)
            if not os.path.exists(target):
                shutil.copy2(os.path.join(root, f), target)
                changed.append(os.path.relpath(target, A_DST))


def write_if_changed(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if os.path.exists(path) and open(path, encoding='utf-8').read() == text:
        return
    open(path, 'w', encoding='utf-8').write(text)
    changed.append(os.path.relpath(path, A_DST))


def edit(rel, old, new, done_marker):
    path = os.path.join(A_DST, rel)
    s = open(path, encoding='utf-8').read()
    if done_marker in s:
        return
    if s.count(old) != 1:
        sys.exit(f'anchor not found once in {rel}: {old[:80]!r} (found {s.count(old)})')
    open(path, 'w', encoding='utf-8').write(s.replace(old, new))
    changed.append(rel)


# ---------------------------------------------------------------- 1. game folder (no Editor tools)
copytree(os.path.join(A_SRC, '0_BingoFlash'), BF, skip=(os.path.join('Scripts', 'Editor'), 'Resources'))
# Resources go one level down, into Resources/BingoFlash (another game also has an Audio/sfx_countdown_go).
copytree(os.path.join(A_SRC, '0_BingoFlash', 'Resources'), os.path.join(BF, 'Resources', 'BingoFlash'))

# ---------------------------------------------------------------- 2. Resources load paths -> BingoFlash/...
S = 'BingoFlash/Scripts/'
for rel, old, new in [
    (S + 'UI/BingoTitleStyle.cs', '"Fonts/TitanOne-Regular"', '"BingoFlash/Fonts/TitanOne-Regular"'),
    (S + 'UI/BingoTimerCallouts.cs', '"Fonts/TitanOne-Regular"', '"BingoFlash/Fonts/TitanOne-Regular"'),
    (S + 'UI/BingoPraiseCallouts.cs', '"Fonts/TitanOne-Regular"', '"BingoFlash/Fonts/TitanOne-Regular"'),
    (S + 'UI/BingoTimerCallouts.cs', 'Resources.Load<AudioClip>("Audio/', 'Resources.Load<AudioClip>("BingoFlash/Audio/'),
    (S + 'Core/BingoAudio.cs', 'Resources.Load<AudioClip>("Audio/', 'Resources.Load<AudioClip>("BingoFlash/Audio/'),
    (S + 'Core/BingoBallCallVoice.cs', 'const string ResourceFolder = "BallCalls/";', 'const string ResourceFolder = "BingoFlash/BallCalls/";'),
    (S + 'Ball/BingoBallTimerView.cs', 'Load("BallTimer/', 'Load("BingoFlash/BallTimer/'),
]:
    path = os.path.join(A_DST, rel)
    s = open(path, encoding='utf-8').read()
    s2 = s.replace(new, '\0').replace(old, new).replace('\0', new)
    if s2 != s:
        open(path, 'w', encoding='utf-8').write(s2)
        changed.append(rel)

# ---------------------------------------------------------------- 3. scene + iOS plugin
scene_dst = os.path.join(BF, 'Scenes', 'BingoFlashGame.unity')
os.makedirs(os.path.dirname(scene_dst), exist_ok=True)
if not os.path.exists(scene_dst):
    shutil.copy2(os.path.join(A_SRC, 'Scenes', 'Gameplay.unity'), scene_dst)
    changed.append('BingoFlash/Scenes/BingoFlashGame.unity')
copytree(os.path.join(A_SRC, 'Plugins', 'iOS'), os.path.join(BF, 'Plugins', 'iOS'))

# ---------------------------------------------------------------- 4. assembly + link
write_if_changed(os.path.join(BF, 'FunPixelNest.BingoFlash.asmdef'), '''{
    "name": "FunPixelNest.BingoFlash",
    "rootNamespace": "",
    "references": [
        "FunPixelNest.SDK",
        "UnityEngine.UI"
    ],
    "includePlatforms": [],
    "excludePlatforms": [],
    "allowUnsafeCode": false,
    "overrideReferences": false,
    "precompiledReferences": [],
    "autoReferenced": false,
    "defineConstraints": [],
    "versionDefines": [],
    "noEngineReferences": false
}
''')
write_if_changed(os.path.join(BF, 'Scripts', 'FunPixelNest', 'FpnBingoLink.cs'),
                 open(os.path.join(LINKDIR, 'FpnBingoLink.cs'), encoding='utf-8').read())

# ---------------------------------------------------------------- 5. hooks
L = 'global::FunPixelNest.Bingo.FpnBingoLink'
edit(S + 'Data/BingoRandom.cs',
     '''            // TODO(Skillz): return SkillzCrossPlatform.Random.Range(minInclusive, maxExclusive);
            return Random.Range(minInclusive, maxExclusive);''',
     f'''            // FunPixelNest app: from the match seed, so everyone in a pool gets the same card and ball order.
            if (global::FunPixelNest.FpnPlatform.IsHosted) return {L}.NextInt(minInclusive, maxExclusive);
            return Random.Range(minInclusive, maxExclusive);''',
     f'{L}.NextInt(')
edit(S + 'Core/BingoSettings.cs',
     '''        static void ApplyOnBoot()
        {
            Apply();
        }''',
     f'''        static void ApplyOnBoot()
        {{
            // Combined FunPixelNest build: don't mute every game at start-up from Bingo's saved switch.
            if ({L}.IsCombinedBuild) return;
            Apply();
        }}''',
     f'{L}.IsCombinedBuild')
edit(S + 'UI/SafeAreaFitter.cs',
     '''        [ContextMenu("Apply Safe Area Now")]''',
     '''        /// <summary>Extra padding in pixels (left, bottom, right, top), e.g. to keep clear of a host app's HUD.</summary>
        public void SetExtraPadding(Vector4 pixels)
        {
            extraPadding = pixels;
            ApplySafeArea(force: true);
        }

        [ContextMenu("Apply Safe Area Now")]''',
     'public void SetExtraPadding(')
G = S + 'Core/BingoGameController.cs'
edit(G, '''        public bool IsPaused => _isPaused;''',
     '''        public bool IsPaused => _isPaused;
        public float RoundTimeRemaining => _roundTimeRemaining;
        public int CurrentScore => hud != null ? hud.Score : 0;''',
     'public float RoundTimeRemaining =>')
edit(G, '''            BindPanels();
            BingoAudio.PlayMusic(BingoMusic.Gameplay);''',
     f'''            BindPanels();
            {L}.Attach(this, hud, canvas);
            BingoAudio.PlayMusic(BingoMusic.Gameplay);''',
     f'{L}.Attach(')
edit(G, '''            if (Input.GetKeyDown(KeyCode.Escape))''',
     f'''            if (Input.GetKeyDown(KeyCode.Escape) && !{L}.InApp) // in the app, Back opens the app's pause menu''',
     'KeyCode.Escape) && !')
edit(G, '''            if (paused && _isPlaying && !_isPaused)''',
     f'''            if (paused && _isPlaying && !_isPaused && !{L}.InApp) // the app pauses the game itself''',
     '!_isPaused && !global::')
edit(G, '''        void BeginBallCalling()
        {
            _isPlaying = true;''',
     f'''        void BeginBallCalling()
        {{
            {L}.OnRoundStarted();
            _isPlaying = true;''',
     f'{L}.OnRoundStarted();')
edit(G, '''            // Coins for the Themes screen: 1 coin per BingoWallet.ScorePerCoin points.''',
     f'''            // FunPixelNest app: the app shows the result (breakdown + total); no themes coins, no results panel.
            if ({L}.Finish(this, total, _daubScoreTotal, _bingoScoreTotal, _boardClearBonusTotal, _timeBonusTotal, _penaltyTotal))
            {{
                return;
            }}

            // Coins for the Themes screen: 1 coin per BingoWallet.ScorePerCoin points.''',
     f'{L}.Finish(')

# ---------------------------------------------------------------- 6. router + build scenes
R = 'FunPixelNestApp/FpnGameRouter.cs'
edit(R, '''            { "snake_war", "SnakeWarGame" },''',
     '''            { "snake_war", "SnakeWarGame" },
            { "bingo", "BingoFlashGame" },''',
     '{ "bingo", "BingoFlashGame" }')
edit(R, '''    ///   flap_dash → FlapDashGame · snake_war → SnakeWarGame.''',
     '''    ///   flap_dash → FlapDashGame · snake_war → SnakeWarGame · bingo → BingoFlashGame.''',
     'bingo → BingoFlashGame')
B = 'FunPixelNestApp/Editor/FpnBuildSetup.cs'
edit(B, '''            "Assets/SnakeWar/_Scenes/SnakeWarGame.unity",
        };''',
     '''            "Assets/SnakeWar/_Scenes/SnakeWarGame.unity",
            "Assets/BingoFlash/Scenes/BingoFlashGame.unity",
        };''',
     'Assets/BingoFlash/Scenes/BingoFlashGame.unity')
edit(B, '''    /// Queen Sudoku, Solitaire Slots, Bubble Blitz, Mahjong, Flap Dash, Snake War.''',
     '''    /// Queen Sudoku, Solitaire Slots, Bubble Blitz, Mahjong, Flap Dash, Snake War, Bingo Flash.''',
     'Snake War, Bingo Flash.')

print(f'{len(changed)} changes')
for c in changed[:25]:
    print('  ', c)
if len(changed) > 25:
    print('   ...')
