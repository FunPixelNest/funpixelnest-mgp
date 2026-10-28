"""Game icon for Arrows: the game's own look (multi-colour arrows on a white board). Original art, drawn in code.
Usage: python3 arrows_icon.py out.webp"""
import sys
from PIL import Image, ImageDraw, ImageFilter

S = 1600                       # draw big, scale down to 400 for smooth edges
G = 7                          # grid cells
M = 150                        # margin
C = (S - 2 * M) / G            # cell size
W = int(C * 0.34)              # stroke width

PAL = {  # GridManager.ArrowPalette
    'purple': (124, 77, 255), 'orange': (255, 128, 0), 'teal': (0, 150, 136), 'pink': (233, 30, 140),
    'blue': (41, 121, 255), 'green': (46, 160, 67), 'cyan': (0, 172, 193), 'violet': (171, 71, 188),
}
# Each arrow: cells (col,row) from tail to head; the head points in the direction of the last step.
ARROWS = [
    ('purple', [(0, 0), (0, 1), (0, 2), (1, 2), (2, 2)]),
    ('orange', [(3, 0), (2, 0), (1, 0)]),
    ('blue',   [(3, 3), (3, 2), (3, 1), (4, 1), (5, 1), (5, 0)]),
    ('pink',   [(6, 3), (6, 2), (6, 1), (6, 0)]),
    ('teal',   [(0, 6), (0, 5), (0, 4), (1, 4), (1, 3)]),
    ('green',  [(4, 5), (4, 4), (5, 4), (5, 3), (5, 2), (4, 2)]),
    ('cyan',   [(2, 6), (3, 6), (4, 6), (5, 6), (6, 6), (6, 5)]),
    ('violet', [(1, 5), (2, 5), (3, 5), (3, 4), (2, 4), (2, 3)]),
]

def pt(c):
    return (M + (c[0] + 0.5) * C, M + (c[1] + 0.5) * C)

def draw_arrow(d, color, cells, off=(0, 0)):
    p = [pt(c) for c in cells]
    p = [(x + off[0], y + off[1]) for x, y in p]
    (x1, y1), (x2, y2) = p[-2], p[-1]
    dx, dy = (x2 - x1), (y2 - y1)
    n = (dx * dx + dy * dy) ** 0.5; ux, uy = dx / n, dy / n
    hl = C * 0.48; hw = C * 0.36                   # head length / half width
    tip = (x2 + ux * C * 0.28, y2 + uy * C * 0.28)
    base = (tip[0] - ux * hl, tip[1] - uy * hl)
    body = p[:-1] + [base]
    d.line(body, fill=color, width=W, joint='curve')
    r = W / 2
    tx, ty = body[0]
    d.ellipse([tx - r, ty - r, tx + r, ty + r], fill=color)
    d.polygon([tip, (base[0] - uy * hw, base[1] + ux * hw), (base[0] + uy * hw, base[1] - ux * hw)], fill=color)

img = Image.new('RGB', (S, S), (255, 255, 255))
d = ImageDraw.Draw(img)
# faint board dots, like the in-game grid
for i in range(G):
    for j in range(G):
        x, y = pt((i, j)); r = C * 0.06
        d.ellipse([x - r, y - r, x + r, y + r], fill=(226, 232, 242))
# soft drop shadows
sh = Image.new('L', (S, S), 0); sd = ImageDraw.Draw(sh)
for col, cells in ARROWS:
    draw_arrow(sd, 255, cells, off=(0, C * 0.09))
sh = sh.filter(ImageFilter.GaussianBlur(C * 0.07))
img.paste(Image.new('RGB', (S, S), (150, 160, 185)), (0, 0), sh.point(lambda v: int(v * 0.45)))
d = ImageDraw.Draw(img)
for col, cells in ARROWS:
    c = PAL[col]
    draw_arrow(d, tuple(int(v * 0.78) for v in c), cells, off=(0, C * 0.045))   # darker rim below
    draw_arrow(d, c, cells)
# glossy highlight on each body
hl = Image.new('L', (S, S), 0); hd = ImageDraw.Draw(hl)
for col, cells in ARROWS:
    p = [pt(c) for c in cells[:-1]]
    if len(p) >= 2:
        hd.line([(x, y - W * 0.18) for x, y in p], fill=255, width=int(W * 0.22), joint='curve')
hl = hl.filter(ImageFilter.GaussianBlur(C * 0.03))
img.paste(Image.new('RGB', (S, S), (255, 255, 255)), (0, 0), hl.point(lambda v: int(v * 0.35)))
img = img.resize((400, 400), Image.LANCZOS)
img.save(sys.argv[1], quality=92)
