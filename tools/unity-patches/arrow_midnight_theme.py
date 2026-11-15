"""Arrows game: "Midnight" theme (chosen 2026-11-15). Plain deep-navy background, the same thin arrows and
dot grid as before, bright glowing arrow colours, dots and guide lines darkened to suit the navy.
Usage: python3 arrow_midnight_theme.py <path to Assets/ArrowDuel>   (safe to run twice)
"""
import os
import re
import sys

root = sys.argv[1]


def patch_file(rel, subs):
    path = os.path.join(root, rel)
    s = open(path, encoding='utf-8').read()
    orig = s
    for pattern, repl in subs:
        s, n = re.subn(pattern, repl, s, flags=re.S)
        if n == 0 and repl not in s:
            sys.exit(f'{rel}: pattern not found: {pattern[:80]}')
    if s != orig:
        open(path, 'w', encoding='utf-8').write(s)
        print('patched', rel)
    else:
        print('already done', rel)


NAVY = 'new Color32(24, 30, 60, 255)'
patch_file('Scripts/Gameplay/ArrowBackgroundTheme.cs', [
    (r'// Plain white background\.\n        static readonly Color Top = [^;]+;\n        static readonly Color Middle = [^;]+;\n        static readonly Color Bottom = [^;]+;',
     f'// "Midnight": plain deep-navy background.\n        static readonly Color Top = {NAVY};\n'
     f'        static readonly Color Middle = {NAVY};\n        static readonly Color Bottom = {NAVY};'),
    (r'const bool Dots = true;', 'const bool Dots = false;'),
])

PALETTE = '''        public static readonly Color32[] ArrowPalette =
        {
            new(0, 220, 255, 255),   // cyan
            new(255, 90, 200, 255),  // pink
            new(160, 240, 70, 255),  // lime
            new(255, 200, 40, 255),  // yellow
            new(150, 130, 255, 255), // violet
            new(255, 130, 70, 255),  // orange
            new(80, 170, 255, 255),  // blue
            new(60, 230, 170, 255),  // aqua
        };'''
patch_file('Scripts/Gameplay/GridManager.cs', [
    (r'        public static readonly Color32\[\] ArrowPalette =\n        \{.*?\n        \};', PALETTE),
    (r'public static readonly Color32 DefaultArrowColor = new\([^)]*\);', 'public static readonly Color32 DefaultArrowColor = new(0, 220, 255, 255);'),
    (r'public static readonly Color32 GridDotColor = new\([^)]*\);', 'public static readonly Color32 GridDotColor = new(70, 80, 120, 255);'),
    (r'public static readonly Color32 GridLineColor = new\([^)]*\);', 'public static readonly Color32 GridLineColor = new(46, 56, 98, 255);'),
    (r'public static readonly Color32 ArrowShadowColor = new\([^)]*\);', 'public static readonly Color32 ArrowShadowColor = new(0, 0, 0, 110);'),
])
