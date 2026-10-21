"""Snake War in the app: the player's snake had no body and didn't grow.

Cause: the app opens the game scene directly, so the Init scene that saves the default skin
("SelectedSkinDetails" = "F/0/0/0/0/0/0") never runs on a fresh install. The player's skin string is
then empty, CreateSnake throws on ParseSkinDataString("")[1] before adding any body parts, and every
later AddBodypart (eating food) throws the same way. Bots pick random skins, so they were fine.

Fix: FpnSnakeWarLink.BeginMatch makes sure a valid skin and the player's name are set, and
PlayerController falls back to the default skin when the saved one is broken.
Usage: python3 snake_skin_fix.py <path to Assets/SnakeWar/Classes>
"""
import sys, os
root = sys.argv[1]

def patch(rel, old, new, marker):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8-sig').read()
    if marker in s:
        print('already patched:', rel); return
    assert s.count(old) == 1, ('anchor not found once in ' + rel, old[:80])
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8').write(s)
    print('patched:', rel)

patch('FunPixelNest/FpnSnakeWarLink.cs',
'''            Rng = FpnRandom.FromLaunch();
            _submitted = false;
''',
'''            Rng = FpnRandom.FromLaunch();
            _submitted = false;
            EnsurePlayerSetup();
''', 'EnsurePlayerSetup();')

patch('FunPixelNest/FpnSnakeWarLink.cs',
'''        /// <summary>
        /// Hides the game's own score / timer / ping boxes''',
'''        /// <summary>Default skin (the Init scene normally saves it; the app skips that scene).</summary>
        public const string DefaultSkin = "F/0/0/0/0/0/0";

        /// <summary>True when the saved skin string has all 7 parts as numbers after the type ("F/0/0/0/0/0/0").</summary>
        public static bool IsValidSkin(string skin)
        {
            if (string.IsNullOrEmpty(skin))
            {
                return false;
            }

            string[] parts = skin.Split('/');
            if (parts.Length < 7 || (parts[0] != "F" && parts[0] != "C"))
            {
                return false;
            }

            for (int i = 1; i < 7; i++)
            {
                if (!int.TryParse(parts[i], out _))
                {
                    return false;
                }
            }

            return true;
        }

        /// <summary>
        /// The player's skin and nickname: the app opens the game scene directly, so the Init scene that
        /// saves the default skin never ran. Without a skin the player's snake gets no body and can't grow.
        /// </summary>
        private static void EnsurePlayerSetup()
        {
            if (!IsValidSkin(PlayerPrefs.GetString("SelectedSkinDetails", "")))
            {
                PlayerPrefs.SetString("SelectedSkinDetails", DefaultSkin);
                PlayerPrefs.Save();
            }

            string name = FpnPlatform.LaunchData != null ? FpnPlatform.LaunchData.DisplayName : null;
            LocalPlayerData.NickName = string.IsNullOrWhiteSpace(name) ? "You" : name;
        }

        /// <summary>
        /// Hides the game's own score / timer / ping boxes''', 'public const string DefaultSkin')

patch('PlayerController.cs',
'''            NetworkSkinDetail = PlayerPrefs.GetString("SelectedSkinDetails");
''',
'''            NetworkSkinDetail = PlayerPrefs.GetString("SelectedSkinDetails");
            if (!FpnSnakeWarLink.IsValidSkin(NetworkSkinDetail))
            {
                // No saved skin (fresh install, the menu scene never ran): without one the body can't be built.
                NetworkSkinDetail = FpnSnakeWarLink.DefaultSkin;
                PlayerPrefs.SetString("SelectedSkinDetails", NetworkSkinDetail);
            }
''', 'FpnSnakeWarLink.IsValidSkin(NetworkSkinDetail)')
