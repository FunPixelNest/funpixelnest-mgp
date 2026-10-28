#!/usr/bin/env python3
"""Sounds for Blackjack and Queen Sudoku (28 Oct 2026), applied to the Unity project.

  python3 oct28b_sounds.py <path to Assets> <folder with the .ogg files and QueenSudokuAudio.cs>

Idempotent. Copies the clips into Resources folders and hooks them up:
  - Blackjack: GameFeedback loads Resources/BlackjackAudio/blackjack_* (lounge music, card snap into a slot, deal,
    hold, 21, Blackjack, five cards, bust) before falling back to its old generated beeps.
  - Queen Sudoku: new QueenSudokuAudio (music + mark / unmark / queen placed / removed / conflict / life lost /
    hint / win / time up), called from GameBoardUI.
"""
import os
import shutil
import sys

ASSETS, SRC = sys.argv[1], sys.argv[2]
changed = []


def edit(rel, old, new, done_marker):
    path = os.path.join(ASSETS, rel)
    s = open(path, encoding='utf-8').read()
    if done_marker in s:
        return
    if s.count(old) != 1:
        sys.exit(f'anchor not found once in {rel}: {old[:70]!r} (found {s.count(old)})')
    open(path, 'w', encoding='utf-8').write(s.replace(old, new))
    changed.append(rel)


def copy(src_name, rel_dst):
    dst = os.path.join(ASSETS, rel_dst)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    src = os.path.join(SRC, src_name)
    if not os.path.exists(dst) or open(src, 'rb').read() != open(dst, 'rb').read():
        shutil.copyfile(src, dst)
        changed.append(rel_dst)


# ---------------------------------------------------------------- clips
for f in sorted(os.listdir(SRC)):
    if f.startswith('blackjack_') and f.endswith('.ogg'):
        copy(f, 'Blackjack/Resources/BlackjackAudio/' + f)
    elif f.startswith('qs_') and f.endswith('.ogg'):
        copy(f, 'QueenSudoku/Resources/QueenSudokuAudio/' + f)
copy('QueenSudokuAudio.cs', 'QueenSudoku/Scripts/Audio/QueenSudokuAudio.cs')

# ---------------------------------------------------------------- Blackjack: load the packaged clips first
edit('Blackjack/Scripts/GameFeedback.cs',
     '''        void EnsureClips()
        {
''',
     '''        void EnsureClips()
        {
            // Packaged sounds (Resources/BlackjackAudio). Their names don't start with "bj_", so
            // DestroyBuiltInClips never destroys them.
            if (cardPlaceClip == null) cardPlaceClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_card_place");
            if (dealClip == null) dealClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_deal");
            if (holdClip == null) holdClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_hold");
            if (twentyOneClip == null) twentyOneClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_21");
            if (blackjackClip == null) blackjackClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_blackjack");
            if (fiveCardsClip == null) fiveCardsClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_five");
            if (bustClip == null) bustClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_bust");
            if (musicClip == null) musicClip = Resources.Load<AudioClip>("BlackjackAudio/blackjack_music");

''',
     'Packaged sounds (Resources/BlackjackAudio)')
edit('Blackjack/Scripts/GameFeedback.cs',
     '        [SerializeField] float musicVolume = 0.22f;',
     '        [SerializeField] float musicVolume = 0.4f;',
     'float musicVolume = 0.4f;')

# ---------------------------------------------------------------- Queen Sudoku: hooks in GameBoardUI
B = 'QueenSudoku/Scripts/UI/GameBoardUI.cs'
edit(B,
     '''            GameManager.EnsureSkillzLevelPrepared(SkillzCrossPlatform.GetMatchInfo());
        }
''',
     '''            GameManager.EnsureSkillzLevelPrepared(SkillzCrossPlatform.GetMatchInfo());
        }
        QueenSudokuAudio.StartMusic();
''',
     'QueenSudokuAudio.StartMusic();')
edit(B, '        cells[row][col].ShowXMarkAnimated();\n',
     '        QueenSudokuAudio.Mark();\n        cells[row][col].ShowXMarkAnimated();\n', 'QueenSudokuAudio.Mark();')
edit(B, '        cells[row][col].AnimateRemoveX();\n',
     '        QueenSudokuAudio.Unmark();\n        cells[row][col].AnimateRemoveX();\n', 'QueenSudokuAudio.Unmark();')
edit(B, '        cells[row][col].ShowQueen();\n',
     '        if (playPlacementParticles) QueenSudokuAudio.QueenPlaced(placedQueens.Count);\n        cells[row][col].ShowQueen();\n',
     'QueenSudokuAudio.QueenPlaced(')
edit(B, '            RemoveQueen(pos);\n            lastClickRow = -1;',
     '            RemoveQueen(pos);\n            QueenSudokuAudio.QueenRemoved();\n            lastClickRow = -1;',
     'QueenSudokuAudio.QueenRemoved();')
edit(B, '        var conflicts = conflictCells ?? new List<CellPos>();',
     '        QueenSudokuAudio.Conflict();\n        var conflicts = conflictCells ?? new List<CellPos>();',
     'QueenSudokuAudio.Conflict();')
edit(B, '        livesRemaining--;\n        UpdateLivesDisplay();',
     '        livesRemaining--;\n        QueenSudokuAudio.LifeLost();\n        UpdateLivesDisplay();',
     'QueenSudokuAudio.LifeLost();')
edit(B, '        isRunning = false;\n\n        int stars = PuzzleValidator.CalculateStars(hintsUsedThisPuzzle);',
     '        isRunning = false;\n        QueenSudokuAudio.Win();\n\n        int stars = PuzzleValidator.CalculateStars(hintsUsedThisPuzzle);',
     'QueenSudokuAudio.Win();')
edit(B, '        timerExpired = true;\n        isRunning = false;\n        ShowGameOver();',
     '        timerExpired = true;\n        isRunning = false;\n        QueenSudokuAudio.TimeUp();\n        ShowGameOver();',
     'QueenSudokuAudio.TimeUp();')
edit(B, '''        string message = string.IsNullOrEmpty(hint.message) ? "Hint unavailable." : hint.message;
        ShowHintBanner(message);''',
     '''        string message = string.IsNullOrEmpty(hint.message) ? "Hint unavailable." : hint.message;
        QueenSudokuAudio.Hint();
        ShowHintBanner(message);''',
     'QueenSudokuAudio.Hint();')

print('changed:', *sorted(set(changed)) or ['nothing (already applied)'], sep='\n  ')
