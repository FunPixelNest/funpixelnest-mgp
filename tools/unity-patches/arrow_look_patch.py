"""Arrow game look: arrows in several colors, a new background, no motion trail after a tap.
Usage: python3 arrow_look_patch.py <path to Assets/ArrowDuel>   (safe to run twice)
"""
import os, sys
root = sys.argv[1]

def patch(rel, old, new, marker):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8-sig').read()
    if marker in s:
        print('already patched:', rel); return
    assert s.count(old) == 1, ('anchor not found once in ' + rel, old[:90])
    s = s.replace(old, new)
    open(p, 'w', encoding='utf-8').write(s)
    print('patched:', rel)

# 1) Several arrow colors (same colors for everyone on the same level: picked from the arrow's cells).
patch('Scripts/Gameplay/GridManager.cs',
'''        public static readonly Color32 DefaultArrowColor = new(12, 127, 208, 255);''',
'''        public static readonly Color32 DefaultArrowColor = new(12, 127, 208, 255);

        /// <summary>Arrow colors (red is kept for blocked arrows). Each arrow gets one from its cells, so a level
        /// always looks the same for every player.</summary>
        public static readonly Color32[] ArrowPalette =
        {
            new(124, 77, 255, 255),  // purple
            new(255, 128, 0, 255),   // orange
            new(0, 150, 136, 255),   // teal
            new(233, 30, 140, 255),  // pink
            new(41, 121, 255, 255),  // blue
            new(46, 160, 67, 255),   // green
            new(0, 172, 193, 255),   // cyan
            new(171, 71, 188, 255),  // violet
        };

        public static Color32 ColorForArrow(LevelArrow data)
        {
            if (data?.Indices == null || data.Indices.Count == 0)
                return DefaultArrowColor;
            int h = data.Indices[0] * 7 + data.Indices[data.Indices.Count - 1] * 13 + data.Indices.Count;
            return ArrowPalette[(h & 0x7fffffff) % ArrowPalette.Length];
        }''', 'ArrowPalette')

patch('Scripts/Gameplay/GridManager.cs',
'''            arrow.SetThis(arrowData);
''',
'''            arrow.SetThis(arrowData);
            arrow.baseArrowColor = ColorForArrow(arrowData);
''', 'arrow.baseArrowColor = ColorForArrow(arrowData);')

# 2) No motion trail behind an arrow after it is tapped.
patch('Scripts/Gameplay/GridManager.cs',
'''        ArrowMotionTrail BeginMotionTrail(ArrowController arrow)
        {''',
'''        ArrowMotionTrail BeginMotionTrail(ArrowController arrow)
        {
            // The ribbon trail behind a moving arrow is switched off (cleaner look); callers handle null.
            if (!showMotionTrail)
                return null;
''', 'if (!showMotionTrail)')
patch('Scripts/Gameplay/GridManager.cs',
'''        public static Color32 ColorForArrow(LevelArrow data)''',
'''        /// <summary>Ribbon trail behind a moving arrow (off).</summary>
        const bool showMotionTrail = false;

        public static Color32 ColorForArrow(LevelArrow data)''', 'const bool showMotionTrail')

# 3) Tap flash: a lighter shade of the arrow's own color (was always blue).
patch('Scripts/Gameplay/ArrowController.cs',
'''            uiLine.DOColor(GridManager.CorrectFlashColor, 0.2f)''',
'''            Color flash = Color.Lerp(baseArrowColor, Color.white, 0.4f);
            uiLine.DOColor(flash, 0.2f)''', 'Color flash = Color.Lerp(baseArrowColor')

# 4) New background: FullBleedBackground can take a new picture at runtime.
patch('Scripts/UI/FullBleedBackground.cs',
'''        void Awake()
        {''',
'''        /// <summary>Replaces the background picture (drawn untinted).</summary>
        public void SetBackground(Sprite sprite)
        {
            if (_source == null)
                _source = GetComponent<Image>();
            if (_source == null)
                return;
            _source.sprite = sprite;
            _drawColor = Color.white;
            _capturedColor = true;
            _lastSprite = null;
            SyncProxy(forceLayout: true);
        }

        void Awake()
        {''', 'public void SetBackground(Sprite sprite)')

theme = os.path.join(root, 'Scripts/Gameplay/ArrowBackgroundTheme.cs')
open(theme, 'w', encoding='utf-8').write(r'''using ArrowDuel.UI;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace ArrowDuel
{
    /// <summary>
    /// Gameplay background: a soft peach → lavender → sky gradient with light polka dots, made in code
    /// (no art file). Applied whenever a scene with the arrow grid loads: it replaces the full-screen background
    /// pictures (FullBleedBackground, or large images named *bg* / *background*) and the camera colour.
    /// </summary>
    public static class ArrowBackgroundTheme
    {
        static readonly Color Top = new Color32(255, 233, 214, 255);    // peach
        static readonly Color Middle = new Color32(243, 229, 255, 255); // lavender
        static readonly Color Bottom = new Color32(214, 234, 255, 255); // sky

        static Sprite s_sprite;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        static void Init()
        {
            SceneManager.sceneLoaded -= OnSceneLoaded;
            SceneManager.sceneLoaded += OnSceneLoaded;
            Apply(SceneManager.GetActiveScene());
        }

        static void OnSceneLoaded(Scene scene, LoadSceneMode mode) => Apply(scene);

        static void Apply(Scene scene)
        {
            if (!scene.IsValid() || !scene.isLoaded)
                return;
            GridManager grid = null;
            foreach (var g in Object.FindObjectsOfType<GridManager>(true))
            {
                if (g.gameObject.scene == scene)
                {
                    grid = g;
                    break;
                }
            }
            if (grid == null)
                return;

            Sprite bg = GetSprite();
            int replaced = 0;

            foreach (var fb in Object.FindObjectsOfType<FullBleedBackground>(true))
            {
                if (fb.gameObject.scene != scene)
                    continue;
                var img = fb.GetComponent<Image>();
                if (img == null || img.sprite == null)
                    continue; // solid dimmers behind pop-ups stay as they are
                fb.SetBackground(bg);
                replaced++;
            }

            if (replaced == 0)
            {
                foreach (var img in Object.FindObjectsOfType<Image>(true))
                {
                    if (img.gameObject.scene != scene || img.sprite == null)
                        continue;
                    string n = (img.sprite.name + " " + img.gameObject.name).ToLowerInvariant();
                    if (!(n.Contains("bg") || n.Contains("background")))
                        continue;
                    var canvas = img.canvas != null ? img.canvas.rootCanvas : null;
                    var canvasRt = canvas != null ? canvas.transform as RectTransform : null;
                    if (canvasRt == null)
                        continue;
                    Rect r = img.rectTransform.rect;
                    Rect c = canvasRt.rect;
                    if (r.width * img.rectTransform.lossyScale.x < c.width * canvasRt.lossyScale.x * 0.6f)
                        continue; // small art, not the screen background
                    img.sprite = bg;
                    img.color = Color.white;
                    img.type = Image.Type.Simple;
                    img.preserveAspect = false;
                    replaced++;
                }
            }

            foreach (var cam in Object.FindObjectsOfType<Camera>(true))
            {
                if (cam.gameObject.scene != scene)
                    continue;
                cam.clearFlags = CameraClearFlags.SolidColor;
                cam.backgroundColor = Middle;
            }
        }

        static Sprite GetSprite()
        {
            if (s_sprite != null)
                return s_sprite;
            const int w = 256, h = 512;
            var tex = new Texture2D(w, h, TextureFormat.RGBA32, false)
            {
                wrapMode = TextureWrapMode.Clamp,
                filterMode = FilterMode.Bilinear,
                name = "ArrowBackground",
            };
            var px = new Color32[w * h];
            const float spacing = 32f, radius = 3.2f;
            for (int y = 0; y < h; y++)
            {
                float t = y / (float)(h - 1); // 0 = bottom, 1 = top
                Color baseC = t > 0.5f ? Color.Lerp(Middle, Top, (t - 0.5f) * 2f) : Color.Lerp(Bottom, Middle, t * 2f);
                for (int x = 0; x < w; x++)
                {
                    // Staggered polka dots, a little brighter than the gradient.
                    float row = Mathf.Floor(y / spacing);
                    float ox = (row % 2f) * spacing * 0.5f;
                    float dx = Mathf.Repeat(x + ox, spacing) - spacing * 0.5f;
                    float dy = Mathf.Repeat(y, spacing) - spacing * 0.5f;
                    float d = Mathf.Sqrt(dx * dx + dy * dy);
                    float dot = Mathf.Clamp01(radius + 0.8f - d) * 0.45f;
                    px[y * w + x] = Color.Lerp(baseC, Color.white, dot);
                }
            }
            tex.SetPixels32(px);
            tex.Apply(false, true);
            s_sprite = Sprite.Create(tex, new Rect(0, 0, w, h), new Vector2(0.5f, 0.5f), 100f);
            s_sprite.name = "ArrowBackground";
            return s_sprite;
        }
    }
}
''')
print('written: Scripts/Gameplay/ArrowBackgroundTheme.cs')
