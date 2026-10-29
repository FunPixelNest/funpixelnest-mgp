#!/usr/bin/env python3
"""Adds / updates Bingo Flash (FPN/BingoFlash) in the combined FunPixelNest games project (FPN/FunPixelNestGames).

  python3 import_bingo.py <BingoFlash project dir> <FunPixelNestGames project dir> <dir with FpnBingoLink.cs>

Run it again whenever the original Bingo Flash project changes: every file is rebuilt from the LATEST original
(plain files copied when they differ; hooked files = original + the app hooks below), so the copy always matches
the original game exactly, apart from the hooks. Nothing is deleted. Writes only what changed.

  1. Assets/0_BingoFlash -> Assets/BingoFlash with .meta files (asset ids kept, so the scene's references stay
     intact). Bingo's Editor tools are left out: they rebuild scenes and change Build / Player settings.
  2. Resources go to Resources/BingoFlash/ and the load paths get that prefix (another game also has an
     Audio/sfx_countdown_go).
  3. Scenes/Gameplay.unity -> Assets/BingoFlash/Scenes/BingoFlashGame.unity (no .meta: Unity gives it a fresh id;
     the original has Unity's template id, which another scene already uses). iOS haptics plugin.
  4. Assembly definition FunPixelNest.BingoFlash and the app link (FpnBingoLink.cs).
  5. App hooks: seeded random, app HUD values, app pause / Back / settings, score to the app. The layout is NOT
     changed: the board, ball lane and multiplier sit exactly where the original puts them.
  6. Router (game id "bingo") and build scene list.
"""
import os
import shutil
import sys

SRC, DST, LINKDIR = sys.argv[1], sys.argv[2], sys.argv[3]
A_SRC = os.path.join(SRC, 'Assets')
A_DST = os.path.join(DST, 'Assets')
GAME_SRC = os.path.join(A_SRC, '0_BingoFlash')
BF = os.path.join(A_DST, 'BingoFlash')
L = 'global::FunPixelNest.Bingo.FpnBingoLink'
changed = []

# ---------------------------------------------------------------- hooks: file (relative to 0_BingoFlash) -> edits
RES = [('"Fonts/TitanOne-Regular"', '"BingoFlash/Fonts/TitanOne-Regular"'),
       ('Resources.Load<AudioClip>("Audio/', 'Resources.Load<AudioClip>("BingoFlash/Audio/')]
HOOKS = {
    'Scripts/UI/BingoTitleStyle.cs': [RES[0]],
    'Scripts/UI/BingoPraiseCallouts.cs': [RES[0]],
    'Scripts/UI/BingoTimerCallouts.cs': [RES[0], RES[1]],
    'Scripts/Core/BingoAudio.cs': [RES[1]],
    'Scripts/Core/BingoBallCallVoice.cs': [('const string ResourceFolder = "BallCalls/";', 'const string ResourceFolder = "BingoFlash/BallCalls/";')],
    'Scripts/Ball/BingoBallTimerView.cs': [('Load("BallTimer/', 'Load("BingoFlash/BallTimer/')],
    'Scripts/Data/BingoRandom.cs': [(
        '''            // TODO(Skillz): return SkillzCrossPlatform.Random.Range(minInclusive, maxExclusive);
            return Random.Range(minInclusive, maxExclusive);''',
        f'''            // FunPixelNest app: from the match seed, so everyone in a pool gets the same card and ball order.
            if (global::FunPixelNest.FpnPlatform.IsHosted) return {L}.NextInt(minInclusive, maxExclusive);
            return Random.Range(minInclusive, maxExclusive);''')],
    'Scripts/Core/BingoSettings.cs': [(
        '''        static void ApplyOnBoot()
        {
            Apply();
        }''',
        f'''        static void ApplyOnBoot()
        {{
            // Combined FunPixelNest build: don't mute every game at start-up from Bingo's saved switch.
            if ({L}.IsCombinedBuild) return;
            Apply();
        }}''')],
    'Scripts/Core/BingoGameController.cs': [
        ('''        public bool IsPaused => _isPaused;''',
         '''        public bool IsPaused => _isPaused;
        public float RoundTimeRemaining => _roundTimeRemaining;
        public int CurrentScore => hud != null ? hud.Score : 0;'''),
        ('''            BindPanels();
            BingoAudio.PlayMusic(BingoMusic.Gameplay);''',
         f'''            BindPanels();
            {L}.Attach(this, hud);
            BingoAudio.PlayMusic(BingoMusic.Gameplay);'''),
        ('''            if (Input.GetKeyDown(KeyCode.Escape))''',
         f'''            if (Input.GetKeyDown(KeyCode.Escape) && !{L}.InApp) // in the app, Back opens the app's pause menu'''),
        ('''            if (paused && _isPlaying && !_isPaused)''',
         f'''            if (paused && _isPlaying && !_isPaused && !{L}.InApp) // the app pauses the game itself'''),
        ('''        void BeginBallCalling()
        {''',
         f'''        void BeginBallCalling()
        {{
            {L}.OnRoundStarted();'''),
        ('''            // Coins for the Themes screen: 1 coin per BingoWallet.ScorePerCoin points.''',
         f'''            // FunPixelNest app: the app shows the result (breakdown + total); no themes coins, no results panel.
            if ({L}.Finish(this, total, _daubScoreTotal, _bingoScoreTotal, _boardClearBonusTotal, _timeBonusTotal, _penaltyTotal))
            {{
                return;
            }}

            // Coins for the Themes screen: 1 coin per BingoWallet.ScorePerCoin points.'''),
    ],
}


def put(dst, data):
    """Writes bytes if different. Returns True when written."""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(dst):
        with open(dst, 'rb') as f:
            if f.read() == data:
                return False
    with open(dst, 'wb') as f:
        f.write(data)
    changed.append(os.path.relpath(dst, A_DST))
    return True


def hooked(rel, data):
    text = data.decode('utf-8')
    for old, new in HOOKS[rel]:
        n = text.count(old)
        if n == 0:
            sys.exit(f'Bingo changed: anchor not found in {rel}: {old[:70]!r}')
        if old.startswith(('"', 'Resources', 'Load(', 'const')):
            text = text.replace(old, new)              # resource paths: every occurrence
        elif n != 1:
            sys.exit(f'Bingo changed: anchor found {n} times in {rel}: {old[:70]!r}')
        else:
            text = text.replace(old, new)
    return text.encode('utf-8')


# ---------------------------------------------------------------- 1-2. game folder from the latest original
seen_hooks = set()
for root, dirs, files in os.walk(GAME_SRC):
    rel_dir = os.path.relpath(root, GAME_SRC)
    parts = [] if rel_dir == '.' else rel_dir.split(os.sep)
    if parts[:2] == ['Scripts', 'Editor']:
        continue
    for f in files:
        if f == '.DS_Store' or (parts == ['Scripts'] and f == 'Editor.meta') or (not parts and f == 'Resources.meta'):
            continue
        rel = '/'.join(parts + [f])
        if parts[:1] == ['Resources']:
            dst = os.path.join(BF, 'Resources', 'BingoFlash', *parts[1:], f)
        else:
            dst = os.path.join(BF, *parts, f)
        with open(os.path.join(root, f), 'rb') as fh:
            data = fh.read()
        if rel in HOOKS:
            data = hooked(rel, data)
            seen_hooks.add(rel)
        put(dst, data)
missing = set(HOOKS) - seen_hooks
if missing:
    sys.exit('Bingo changed: files not found: ' + ', '.join(sorted(missing)))

# ---------------------------------------------------------------- 3. scene + iOS plugin
with open(os.path.join(A_SRC, 'Scenes', 'Gameplay.unity'), 'rb') as fh:
    put(os.path.join(BF, 'Scenes', 'BingoFlashGame.unity'), fh.read())
ios = os.path.join(A_SRC, 'Plugins', 'iOS')
if os.path.isdir(ios):
    for f in os.listdir(ios):
        if f.startswith('BingoHaptics'):
            with open(os.path.join(ios, f), 'rb') as fh:
                put(os.path.join(BF, 'Plugins', 'iOS', f), fh.read())

# ---------------------------------------------------------------- 4. assembly + link
put(os.path.join(BF, 'FunPixelNest.BingoFlash.asmdef'), b'''{
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
with open(os.path.join(LINKDIR, 'FpnBingoLink.cs'), 'rb') as fh:
    put(os.path.join(BF, 'Scripts', 'FunPixelNest', 'FpnBingoLink.cs'), fh.read())


# ---------------------------------------------------------------- 6. router + build scenes (FunPixelNest files)
def edit(rel, old, new, done_marker):
    path = os.path.join(A_DST, rel)
    s = open(path, encoding='utf-8').read()
    if done_marker in s:
        return
    if s.count(old) != 1:
        sys.exit(f'anchor not found once in {rel}: {old[:80]!r} (found {s.count(old)})')
    open(path, 'w', encoding='utf-8').write(s.replace(old, new))
    changed.append(rel)


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

print(f'{len(changed)} files updated')
for c in changed[:40]:
    print('  ', c)
if len(changed) > 40:
    print('   ...')
