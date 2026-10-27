"""Arrow: board always fits the screen + plain white background. Block Puzzle: a picked-up piece sits a little
above the finger. Usage: python3 fix_1027.py <path to FunPixelNestGames/Assets>   (safe to run twice)"""
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

G = 'ArrowDuel/Scripts/Gameplay/GridManager.cs'
# 1) Fit: centre the board on the middle of GridBounds (not its pivot), keep a margin, and fit again whenever
#    the space changes (the app's banner / HUD insets arrive after the level was built). Refitting only scales
#    the board, so arrows, dots and move distances stay consistent.
patch(G,
'''            cellSize = cell;
            gridRT.sizeDelta = new Vector2(cell * gridX, cell * gridY);
            gridRT.position = gridBoundsRT.position;
            gridRT.localScale = Vector3.one;
        }''',
'''            cellSize = cell;
            gridRT.sizeDelta = new Vector2(cell * gridX, cell * gridY);
            gridRT.localScale = Vector3.one;
            gridRT.position = BoundsCenterWorld();
            _fitBoundsSize = gridBoundsRT.rect.size;
            _fitScreen = new Vector2(Screen.width, Screen.height);
            _fitGridX = gridX;
            _fitGridY = gridY;
            _refitPending = true; // measure again on the next frame, once the layout has settled
        }

        // ---- keep the board inside the screen (FunPixelNest) ----
        private Vector2 _fitBoundsSize;
        private Vector2 _fitScreen;
        private int _fitGridX;
        private int _fitGridY;
        private bool _refitPending;

        /// <summary>Middle of the GridBounds rectangle in world space (its pivot may not be centred).</summary>
        private Vector3 BoundsCenterWorld()
        {
            return gridBoundsRT.TransformPoint(gridBoundsRT.rect.center);
        }

        /// <summary>
        /// Called every frame: right after a level is built and whenever the space for the board changes (screen
        /// size, insets, safe area), scale the board so the whole level fits inside the part of GridBounds that is
        /// actually on screen, centred there. Only the board's scale changes, so arrows and moves stay consistent.
        /// </summary>
        private void KeepBoardInBounds()
        {
            if (gridBoundsRT == null || gridRT == null || _fitGridX <= 0 || _fitGridY <= 0 || cellSize <= 0f)
                return;
            Vector2 bounds = gridBoundsRT.rect.size;
            Vector2 screen = new Vector2(Screen.width, Screen.height);
            if (!_refitPending && (bounds - _fitBoundsSize).sqrMagnitude < 1f && screen == _fitScreen)
                return;
            _refitPending = false;
            _fitBoundsSize = bounds;
            _fitScreen = screen;

            // GridBounds corners in world space, clipped to the visible canvas.
            var corners = new Vector3[4];
            gridBoundsRT.GetWorldCorners(corners);
            Vector3 min = corners[0], max = corners[2];
            Canvas canvas = gridBoundsRT.GetComponentInParent<Canvas>();
            RectTransform canvasRt = canvas != null ? canvas.rootCanvas.transform as RectTransform : null;
            if (canvasRt != null)
            {
                var c = new Vector3[4];
                canvasRt.GetWorldCorners(c);
                min = Vector3.Max(min, c[0]);
                max = Vector3.Min(max, c[2]);
            }
            Vector3 lossy = gridRT.parent != null ? gridRT.parent.lossyScale : Vector3.one;
            float availW = Mathf.Max(1f, (max.x - min.x) / Mathf.Max(1e-5f, lossy.x)) * boundsFill;
            float availH = Mathf.Max(1f, (max.y - min.y) / Mathf.Max(1e-5f, lossy.y)) * boundsFill;
            float cell = Mathf.Min(availW / _fitGridX, availH / _fitGridY);
            if (maxCellSize > 0f)
                cell = Mathf.Min(cell, maxCellSize);
            float scale = cell / cellSize;
            gridRT.localScale = new Vector3(scale, scale, 1f);
            gridRT.position = (min + max) * 0.5f;
        }''', 'private void KeepBoardInBounds()')
patch(G,
'''        private void Update()
        {
            RefreshScaleAndPosOfOthers();''',
'''        private void Update()
        {
            KeepBoardInBounds();
            RefreshScaleAndPosOfOthers();''', 'KeepBoardInBounds();\n            RefreshScaleAndPosOfOthers();')

# 2) Plain white background (replaces the gradient + dots).
T = 'ArrowDuel/Scripts/Gameplay/ArrowBackgroundTheme.cs'
patch(T,
'''        static readonly Color Top = new Color32(255, 233, 214, 255);    // peach
        static readonly Color Middle = new Color32(243, 229, 255, 255); // lavender
        static readonly Color Bottom = new Color32(214, 234, 255, 255); // sky''',
'''        // Plain white background.
        static readonly Color Top = Color.white;
        static readonly Color Middle = Color.white;
        static readonly Color Bottom = Color.white;
        const bool Dots = false;''', 'const bool Dots = false;')
patch(T,
'''                    float dot = Mathf.Clamp01(radius + 0.8f - d) * 0.45f;''',
'''                    float dot = Dots ? Mathf.Clamp01(radius + 0.8f - d) * 0.45f : 0f;''', 'float dot = Dots ?')

# 3) Block Puzzle: as soon as a piece is picked up it sits a little above the finger (its bottom edge just
#    above the touch point), so the player can see the piece and where it will land.
P = 'MiniBlockPuzzle/Scripts/Core/PieceView.cs'
patch(P,
'''                // Keep grab point, then lift so the shape sits above the finger/cursor.
                dragOffset = RectTransform.anchoredPosition - localPoint;
                dragOffset.y += ResolveDragFingerOffsetY();
                RectTransform.anchoredPosition = localPoint + dragOffset;''',
'''                // Keep the grab point sideways; lift the piece so its bottom edge sits a little above the finger.
                dragOffset = RectTransform.anchoredPosition - localPoint;
                dragOffset.y = LiftAboveFinger();
                RectTransform.anchoredPosition = localPoint + dragOffset;''', 'dragOffset.y = LiftAboveFinger();')
patch(P,
'''                dragOffset = new Vector2(0f, ResolveDragFingerOffsetY());''',
'''                dragOffset = new Vector2(0f, LiftAboveFinger());''', 'dragOffset = new Vector2(0f, LiftAboveFinger());')
patch(P,
'''        private float ResolveDragFingerOffsetY()
        {''',
'''        /// <summary>Half the piece's height plus a small gap (about half a board cell): the piece floats just above the finger.</summary>
        private float LiftAboveFinger()
        {
            float cell = boardView != null ? boardView.FittedCellSize : trayBlockSize;
            float halfHeight = blocksRoot != null ? blocksRoot.rect.height * 0.5f * Mathf.Abs(blocksRoot.localScale.y) : cell;
            return halfHeight + Mathf.Max(24f, cell * 0.55f);
        }

        private float ResolveDragFingerOffsetY()
        {''', 'private float LiftAboveFinger()')
