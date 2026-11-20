"""Arrows Midnight theme fix (2026-11-21): the gameplay scene has a plain cream panel named "Background"
(an Image with no sprite, colour 243,232,210) that the theme skipped, so the game still looked cream on the phone.
Now plain full-screen panels named *background* / *bg* are recoloured to the theme colour too.
Usage: python3 arrow_midnight_bgfix.py <path to Assets/ArrowDuel>   (safe to run twice)
"""
import os
import sys

path = os.path.join(sys.argv[1], 'Scripts/Gameplay/ArrowBackgroundTheme.cs')
s = open(path, encoding='utf-8').read()
MARK = 'Plain colour panels named'
if MARK in s:
    print('already done')
    sys.exit(0)
old = '''            foreach (var cam in Object.FindObjectsOfType<Camera>(true))
            {
                if (cam.gameObject.scene != scene)
                    continue;'''
new = '''            // Plain colour panels named *background* / *bg* (no picture, opaque): the theme colour too.
            foreach (var img in Object.FindObjectsOfType<Image>(true))
            {
                if (img.gameObject.scene != scene || img.sprite != null || img.color.a < 0.9f)
                    continue;
                string n = img.gameObject.name.ToLowerInvariant();
                if (n.Contains("background") || n == "bg" || n.EndsWith("_bg") || n.StartsWith("bg_"))
                    img.color = Middle;
            }

            foreach (var cam in Object.FindObjectsOfType<Camera>(true))
            {
                if (cam.gameObject.scene != scene)
                    continue;'''
if s.count(old) != 1:
    sys.exit('anchor not found in ArrowBackgroundTheme.cs')
open(path, 'w', encoding='utf-8').write(s.replace(old, new))
print('patched ArrowBackgroundTheme.cs')
