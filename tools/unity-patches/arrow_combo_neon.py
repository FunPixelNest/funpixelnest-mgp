"""Arrows game: "Neon Segments" multiplier bar (chosen 2026-11-20).
Four rounded segments that light up one by one (cyan, violet, pink, yellow) with a soft glow; the x2..x5 labels sit
centred above their segment, the reached one white and enlarged, the others dim. Only the look changes: the meter's
logic (fill, hold, drain, fast drain on a miss, label punch) stays in ComboProgressBar.
Usage: python3 arrow_combo_neon.py <path to Assets/ArrowDuel>   (safe to run twice)
"""
import os
import sys

root = sys.argv[1]
BAR = os.path.join(root, 'Scripts/UI/ComboProgressBar.cs')
NEON = os.path.join(root, 'Scripts/UI/NeonComboBar.cs')


def patch(path, old, new, marker):
    s = open(path, encoding='utf-8').read()
    if marker in s:
        return False
    if s.count(old) != 1:
        sys.exit(f'{os.path.basename(path)}: anchor not found once: {old[:70]!r}')
    open(path, 'w', encoding='utf-8').write(s.replace(old, new))
    return True


changed = []
if patch(BAR, '''        public bool IsPaused => _paused;''',
         '''        public bool IsPaused => _paused;

        // For NeonComboBar (the "Neon Segments" look built over this bar).
        public Image FillImage => fillImage;
        public RectTransform FillPointer => fillPointer;
        public Text[] Labels => multiplierLabels;''', 'public Image FillImage => fillImage;'):
    changed.append('ComboProgressBar accessors')

if patch(BAR, '''            EnsurePointerRef();
            CacheLabelScales();
            ApplyVisuals(force: true);''',
         '''            EnsurePointerRef();
            CacheLabelScales();
            // "Neon Segments" look: dim labels, the reached multiplier in white; segments drawn by NeonComboBar.
            normalLabelColor = new Color32(110, 120, 170, 255);
            highlightLabelColor = Color.white;
            if (GetComponent<NeonComboBar>() == null)
                gameObject.AddComponent<NeonComboBar>();
            ApplyVisuals(force: true);''', 'gameObject.AddComponent<NeonComboBar>()'):
    changed.append('ComboProgressBar adds NeonComboBar')

SRC = r'''using UnityEngine;
using UnityEngine.UI;

namespace ArrowDuel
{
    /// <summary>
    /// "Neon Segments" look for the combo meter: four rounded segments (x2..x5) that fill one by one in cyan,
    /// violet, pink and yellow, each with a soft glow, over a dark track; the x2..x5 labels sit centred above their
    /// segment. Built in code over the scene's bar (its old background, fill and pointer art are hidden).
    /// Only reads ComboProgressBar.Value, so the meter's timing and logic are unchanged.
    /// </summary>
    [DisallowMultipleComponent]
    public class NeonComboBar : MonoBehaviour
    {
        static readonly Color32[] SegmentColors =
        {
            new Color32(0, 220, 255, 255),   // x2 cyan
            new Color32(150, 130, 255, 255), // x3 violet
            new Color32(255, 90, 200, 255),  // x4 pink
            new Color32(255, 200, 40, 255),  // x5 yellow
        };
        static readonly Color32 EmptyColor = new Color32(40, 48, 90, 255);
        static readonly Color32 EdgeColor = new Color32(70, 82, 140, 255);
        const int Segments = 4;
        const float Gap = 8f;           // between segments (canvas units)
        const float LabelLift = 20f;    // label centre above the bar's top edge

        static Sprite s_round;
        static Sprite s_glow;

        ComboProgressBar _bar;
        RectTransform _track;
        readonly RectTransform[] _fills = new RectTransform[Segments];
        readonly Image[] _rounds = new Image[Segments * 3];
        readonly Image[] _glows = new Image[Segments];
        float _builtHeight = -1f;
        int _labelFrames;

        void Start()
        {
            _bar = GetComponent<ComboProgressBar>();
            if (_bar == null || _bar.FillImage == null || !(_bar.FillImage.rectTransform.parent is RectTransform track))
            {
                enabled = false;
                return;
            }
            _track = track;

            // Hide the old bar art: outer frame, track, fill, ticks and pointer (every image of the meter;
            // the labels are Text and stay).
            foreach (var img in _bar.GetComponentsInChildren<Image>(true))
                img.enabled = false;

            var box = NewRect("NeonSegments", _track);
            box.anchorMin = Vector2.zero;
            box.anchorMax = Vector2.one;
            box.offsetMin = box.offsetMax = Vector2.zero;
            box.SetAsLastSibling();

            for (int i = 0; i < Segments; i++)
            {
                var seg = NewRect("Segment" + (i + 2), box);
                seg.anchorMin = new Vector2(i / (float)Segments, 0f);
                seg.anchorMax = new Vector2((i + 1) / (float)Segments, 1f);
                seg.offsetMin = new Vector2(i == 0 ? 0f : Gap / 2f, 0f);
                seg.offsetMax = new Vector2(i == Segments - 1 ? 0f : -Gap / 2f, 0f);

                var glow = NewImage("Glow", seg, GlowSprite(), SegmentColors[i]);
                Stretch(glow.rectTransform, -12f);
                glow.type = Image.Type.Sliced;
                _glows[i] = glow;

                var bg = NewImage("Track", seg, RoundSprite(), EmptyColor);
                Stretch(bg.rectTransform, 0f);
                var edge = bg.gameObject.AddComponent<Outline>();
                edge.effectColor = EdgeColor;
                edge.effectDistance = new Vector2(1.5f, -1.5f);
                _rounds[i * 3] = bg;

                var fill = NewImage("Fill", seg, RoundSprite(), SegmentColors[i]);
                var frt = fill.rectTransform;
                frt.anchorMin = Vector2.zero;
                frt.anchorMax = new Vector2(0f, 1f);
                frt.offsetMin = frt.offsetMax = Vector2.zero;
                _fills[i] = frt;
                _rounds[i * 3 + 1] = fill;

                var shine = NewImage("Shine", frt, RoundSprite(), new Color(1f, 1f, 1f, 0.35f));
                var srt = shine.rectTransform;
                srt.anchorMin = new Vector2(0f, 0.55f);
                srt.anchorMax = new Vector2(1f, 0.88f);
                srt.offsetMin = new Vector2(4f, 0f);
                srt.offsetMax = new Vector2(-4f, 0f);
                _rounds[i * 3 + 2] = shine;
            }

            foreach (var label in _bar.Labels)
            {
                if (label == null)
                    continue;
                if (label.GetComponent<LayoutElement>() == null)
                    label.gameObject.AddComponent<LayoutElement>().ignoreLayout = true;
                label.alignment = TextAnchor.MiddleCenter;
            }
            LateUpdate();
        }

        void LateUpdate()
        {
            if (_track == null)
                return;

            float h = Mathf.Max(4f, _track.rect.height);
            if (!Mathf.Approximately(h, _builtHeight))
            {
                _builtHeight = h;
                // Sprite corners: 16 px in the sprite → half the bar height on screen (fully round ends).
                for (int k = 0; k < _rounds.Length; k++)
                {
                    if (_rounds[k] == null)
                        continue;
                    float part = k % 3 == 2 ? 0.33f : 1f;
                    _rounds[k].pixelsPerUnitMultiplier = 32f / Mathf.Max(2f, h * part);
                }
                foreach (var g in _glows)
                    if (g != null)
                        g.pixelsPerUnitMultiplier = 24f / (h / 2f + 12f);
            }

            float v = _bar.Value;
            int tier = _bar.CurrentTier;
            for (int i = 0; i < Segments; i++)
            {
                float p = Mathf.Clamp01(v - (1f + i));
                _fills[i].anchorMax = new Vector2(p, 1f);
                bool on = p > 0.02f;
                if (_fills[i].gameObject.activeSelf != on)
                    _fills[i].gameObject.SetActive(on);
                // Glow: soft for filled segments, brighter (and gently pulsing) for the current multiplier.
                float a = p * (tier == i + 2 ? 0.55f + 0.15f * Mathf.Sin(Time.unscaledTime * 6f) : 0.35f);
                var c = _glows[i].color;
                c.a = a;
                _glows[i].color = c;
            }

            // Labels: centred above their segment (re-placed for the first frames while the layout settles).
            if (_labelFrames < 5)
            {
                _labelFrames++;
                PlaceLabels();
            }
        }

        void PlaceLabels()
        {
            Rect r = _track.rect;
            float segW = (r.width - Gap * (Segments - 1)) / Segments;
            var labels = _bar.Labels;
            for (int i = 0; i < labels.Length && i < Segments; i++)
            {
                if (labels[i] == null)
                    continue;
                float x = r.xMin + i * (segW + Gap) + segW / 2f;
                float y = r.yMax + LabelLift;
                labels[i].rectTransform.position = _track.TransformPoint(new Vector3(x, y, 0f));
            }
        }

        static RectTransform NewRect(string name, Transform parent)
        {
            var go = new GameObject(name, typeof(RectTransform));
            go.layer = parent.gameObject.layer;
            var rt = (RectTransform)go.transform;
            rt.SetParent(parent, false);
            return rt;
        }

        static Image NewImage(string name, Transform parent, Sprite sprite, Color color)
        {
            var img = NewRect(name, parent).gameObject.AddComponent<Image>();
            img.sprite = sprite;
            img.type = Image.Type.Sliced;
            img.color = color;
            img.raycastTarget = false;
            return img;
        }

        static void Stretch(RectTransform rt, float inset)
        {
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = new Vector2(inset, inset);
            rt.offsetMax = new Vector2(-inset, -inset);
        }

        /// <summary>White rounded-end bar (64×32, radius 16), sliced so it stretches to any length.</summary>
        static Sprite RoundSprite()
        {
            if (s_round != null)
                return s_round;
            const int w = 64, h = 32, rad = 16;
            var tex = new Texture2D(w, h, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp, name = "NeonRound" };
            var px = new Color32[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    float cx = Mathf.Clamp(x + 0.5f, rad, w - rad);
                    float cy = Mathf.Clamp(y + 0.5f, rad, h - rad);
                    float d = Vector2.Distance(new Vector2(x + 0.5f, y + 0.5f), new Vector2(cx, cy));
                    byte a = (byte)(Mathf.Clamp01(rad - d + 0.5f) * 255f);
                    px[y * w + x] = new Color32(255, 255, 255, a);
                }
            tex.SetPixels32(px);
            tex.Apply(false, true);
            s_round = Sprite.Create(tex, new Rect(0, 0, w, h), new Vector2(0.5f, 0.5f), 100f, 0,
                SpriteMeshType.FullRect, new Vector4(rad, rad, rad, rad));
            return s_round;
        }

        /// <summary>Soft white glow with a rounded core (64×64), sliced.</summary>
        static Sprite GlowSprite()
        {
            if (s_glow != null)
                return s_glow;
            const int s = 64, border = 24;
            var tex = new Texture2D(s, s, TextureFormat.RGBA32, false) { wrapMode = TextureWrapMode.Clamp, name = "NeonGlow" };
            var px = new Color32[s * s];
            for (int y = 0; y < s; y++)
                for (int x = 0; x < s; x++)
                {
                    float cx = Mathf.Clamp(x + 0.5f, border, s - border);
                    float cy = Mathf.Clamp(y + 0.5f, border, s - border);
                    float d = Vector2.Distance(new Vector2(x + 0.5f, y + 0.5f), new Vector2(cx, cy));
                    float t = Mathf.Clamp01(1f - d / border);
                    px[y * s + x] = new Color32(255, 255, 255, (byte)(t * t * 255f));
                }
            tex.SetPixels32(px);
            tex.Apply(false, true);
            s_glow = Sprite.Create(tex, new Rect(0, 0, s, s), new Vector2(0.5f, 0.5f), 100f, 0,
                SpriteMeshType.FullRect, new Vector4(border, border, border, border));
            return s_glow;
        }
    }
}
'''

old = open(NEON, encoding='utf-8').read() if os.path.exists(NEON) else None
if old != SRC:
    open(NEON, 'w', encoding='utf-8').write(SRC)
    changed.append('NeonComboBar.cs')

print('changed: ' + (', '.join(changed) if changed else 'nothing (already applied)'))
