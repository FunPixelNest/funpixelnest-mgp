#!/usr/bin/env python3
"""Arcade Clash wordmark (splash / login logo): chunky 3D game lettering, transparent background.

  python3 arcade_clash_logo.py <TitanOne-Regular.ttf> <out.png> [width]

"ARCADE" in gold, "CLASH" big in sky blue over a starburst, both with a navy outline, a 3D drop
and a glossy top, plus a small "SKILL GAMES" ribbon. Drawn 3x larger and scaled down for smooth edges.
"""
import math
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

FONT, OUT = sys.argv[1], sys.argv[2]
W = int(sys.argv[3]) if len(sys.argv) > 3 else 512
K = 3                      # supersampling
CW, CH = 560 * K, 520 * K  # canvas, cropped at the end
NAVY = (14, 36, 104, 255)
DEEP = (8, 22, 70, 255)


def vgrad(size, stops):
    """Vertical gradient image from [(pos 0..1, (r,g,b)), ...]."""
    w, h = size
    g = Image.new('RGBA', (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(stops) - 1):
            p0, c0 = stops[i]
            p1, c1 = stops[i + 1]
            if p0 <= t <= p1:
                u = (t - p0) / max(1e-6, p1 - p0)
                g.putpixel((0, y), tuple(int(c0[j] + (c1[j] - c0[j]) * u) for j in range(3)) + (255,))
                break
    return g.resize((w, h))


def word(text, size, stops, stroke, depth, tracking=0):
    """One word as an RGBA image: navy outline, 3D drop, gradient fill, gloss."""
    font = ImageFont.truetype(FONT, size)
    # letters placed one by one (tracking), measured on a scratch canvas
    widths = [font.getbbox(ch)[2] - font.getbbox(ch)[0] for ch in text]
    asc, desc = font.getmetrics()
    tw = sum(widths) + tracking * (len(text) - 1)
    pad = stroke + depth + 8 * K
    w, h = tw + pad * 2, asc + desc + pad * 2
    mask = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(mask)
    x = pad
    for ch, cw in zip(text, widths):
        d.text((x - font.getbbox(ch)[0], pad), ch, font=font, fill=255)
        x += cw + tracking
    outline = mask.filter(ImageFilter.MaxFilter(stroke * 2 + 1)) if stroke < 12 else _dilate(mask, stroke)

    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    # 3D drop: the outline shape repeated downward, darkest at the bottom
    for i in range(depth, 0, -1):
        layer = Image.new('RGBA', (w, h), DEEP if i > depth * 0.5 else NAVY)
        img.paste(layer, (0, i), ImageChops.offset(outline, 0, 0))
    img.paste(Image.new('RGBA', (w, h), NAVY), (0, 0), outline)
    # inner white rim, then the gradient face
    rim = mask.filter(ImageFilter.MaxFilter(max(3, (stroke // 4) * 2 + 1)))
    img.paste(Image.new('RGBA', (w, h), (255, 255, 255, 255)), (0, 0), rim)
    face = vgrad((w, h), [(0, stops[0]), (0.5, stops[1]), (1, stops[2])])
    img.paste(face, (0, 0), mask)
    # gloss: a soft white band over the top 45% of the letters
    gl = Image.new('L', (w, h), 0)
    gd = ImageDraw.Draw(gl)
    top = pad
    gd.rectangle([0, top, w, top + int((asc - desc * 0.2) * 0.42)], fill=120)
    gl = ImageChops.multiply(gl, mask.filter(ImageFilter.MinFilter(max(3, (stroke // 3) * 2 + 1))))
    img.paste(Image.new('RGBA', (w, h), (255, 255, 255, 255)), (0, 0), gl.filter(ImageFilter.GaussianBlur(2 * K)))
    return img


def _dilate(mask, r):
    """Round dilation for big strokes (MaxFilter is square and slow at large sizes)."""
    out = mask.copy()
    steps = 24
    for i in range(steps):
        a = 2 * math.pi * i / steps
        for rr in (r, r * 0.66, r * 0.33):
            out = ImageChops.lighter(out, ImageChops.offset(mask, int(round(math.cos(a) * rr)), int(round(math.sin(a) * rr))))
    return out


def burst(cx, cy, r_out, r_in, points, color, rot=0.0):
    pts = []
    for i in range(points * 2):
        a = rot + math.pi * i / points
        r = r_out if i % 2 == 0 else r_in
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts, color


canvas = Image.new('RGBA', (CW, CH), (0, 0, 0, 0))
cd = ImageDraw.Draw(canvas)

# starburst behind CLASH (the "clash" spark)
cx, cy = CW // 2, int(CH * 0.56)
for r_out, r_in, col, rot in ((250 * K, 150 * K, (14, 36, 104, 255), 0.12), (236 * K, 140 * K, (255, 176, 32, 255), 0.12),
                              (200 * K, 128 * K, (255, 214, 74, 255), 0.12)):
    pts, c = burst(cx, cy, r_out, r_in, 12, col, rot)
    cd.polygon(pts, fill=c)

arcade = word('ARCADE', 92 * K, [(255, 236, 120), (255, 196, 40), (255, 140, 0)], 9 * K, 9 * K, tracking=2 * K)
clash = word('CLASH', 150 * K, [(170, 238, 255), (64, 190, 255), (24, 110, 245)], 12 * K, 14 * K, tracking=1 * K)

# ribbon "SKILL GAMES"
rib_font = ImageFont.truetype(FONT, 34 * K)
rib_txt = 'SKILL GAMES'
rb = rib_font.getbbox(rib_txt)
rw, rh = rb[2] - rb[0] + 56 * K, 58 * K

ax = (CW - arcade.width) // 2
cy0 = int(CH * 0.23)
canvas.alpha_composite(clash.rotate(0, resample=Image.BICUBIC), ((CW - clash.width) // 2, cy0 + 40 * K))
canvas.alpha_composite(arcade, (ax, cy0 - 36 * K))

ry = cy0 + 40 * K + clash.height - 44 * K
rx = (CW - rw) // 2
ribbon = Image.new('RGBA', (rw + 16 * K, rh + 16 * K), (0, 0, 0, 0))
rd = ImageDraw.Draw(ribbon)
rd.rounded_rectangle([0, 6 * K, rw + 16 * K - 1, rh + 16 * K - 1], 22 * K, fill=DEEP)
rd.rounded_rectangle([0, 0, rw + 16 * K - 1, rh + 8 * K], 22 * K, fill=NAVY)
rd.rounded_rectangle([6 * K, 6 * K, rw + 10 * K, rh + 2 * K], 18 * K, fill=(255, 92, 140, 255))
rd.rounded_rectangle([6 * K, 6 * K, rw + 10 * K, 6 * K + rh // 2], 18 * K, fill=(255, 128, 168, 255))
rd.text(((rw + 16 * K) // 2, 4 * K + rh // 2 + 2 * K), rib_txt, font=rib_font, fill=(255, 255, 255, 255), anchor='mm',
        stroke_width=3 * K, stroke_fill=(150, 20, 70, 255))
canvas.alpha_composite(ribbon, (rx - 8 * K, ry))

# small sparkles
for (sx, sy, s) in ((0.12, 0.30, 26), (0.88, 0.26, 20), (0.86, 0.80, 24), (0.15, 0.78, 18)):
    pts, _ = burst(int(CW * sx), int(CH * sy), s * K, s * K * 0.3, 4, None)
    cd = ImageDraw.Draw(canvas)
    cd.polygon(pts, fill=(255, 255, 255, 235))

bbox = canvas.getbbox()
canvas = canvas.crop((bbox[0] - 4 * K, bbox[1] - 4 * K, bbox[2] + 4 * K, bbox[3] + 4 * K))
h = round(canvas.height * W / canvas.width)
canvas.resize((W, h), Image.LANCZOS).save(OUT)
print(OUT, W, h)
