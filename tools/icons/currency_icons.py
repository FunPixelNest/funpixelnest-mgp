"""Crisp, uncropped currency + medal icons for the app, drawn in code (original art).
Replaces low-resolution art whose edges were cut off: ic_gem, ic_coin, medal_small_1..3, medal_1..3.

  python3 currency_icons.py <drawable dir> <font.ttf (Fredoka Bold)>
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT, FONT = sys.argv[1], sys.argv[2]
S = 1024  # drawing size; saved smaller with high-quality downscale


def lin_grad(size, c1, c2, angle_deg=90):
    """Linear gradient image, c1 -> c2 along the angle (90 = top to bottom)."""
    w, h = size
    y, x = np.mgrid[0:h, 0:w].astype(float)
    a = math.radians(angle_deg)
    t = (x * math.cos(a) + y * math.sin(a))
    t = (t - t.min()) / (t.max() - t.min() + 1e-9)
    c1, c2 = np.array(c1, float), np.array(c2, float)
    img = c1[None, None, :] * (1 - t[..., None]) + c2[None, None, :] * t[..., None]
    return Image.fromarray(img.astype(np.uint8), 'RGBA' if len(c1) == 4 else 'RGB').convert('RGBA')


def rad_grad(size, center, radius, c_in, c_out):
    w, h = size
    y, x = np.mgrid[0:h, 0:w].astype(float)
    t = np.clip(np.hypot(x - center[0], y - center[1]) / radius, 0, 1)[..., None]
    c_in, c_out = np.array(c_in, float), np.array(c_out, float)
    return Image.fromarray((c_in * (1 - t) + c_out * t).astype(np.uint8)).convert('RGBA')


def fill(base, mask, src):
    base.alpha_composite(Image.composite(src, Image.new('RGBA', base.size, (0, 0, 0, 0)), mask))


def mask_of(draw_fn):
    m = Image.new('L', (S, S), 0)
    draw_fn(ImageDraw.Draw(m))
    return m


def save(img, name, px):
    bbox = img.getbbox()
    img = img.crop(bbox)
    side = max(img.size)
    pad = int(side * 0.06)                     # breathing room: nothing touches the edge
    sq = Image.new('RGBA', (side + 2 * pad, side + 2 * pad), (0, 0, 0, 0))
    sq.alpha_composite(img, ((sq.width - img.width) // 2, (sq.height - img.height) // 2))
    sq = sq.resize((px, px), Image.LANCZOS)
    sq.save(os.path.join(OUT, name), 'WEBP', quality=95, method=6)
    print('wrote', name, sq.size)


def shadow_under(img, offset=(0, 18), blur=14, alpha=90):
    a = img.split()[3]
    sh = Image.new('RGBA', img.size, (20, 30, 80, 0))
    sh.putalpha(a.point(lambda v: v * alpha // 255))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    out.alpha_composite(img)
    return out


# ------------------------------------------------------------------ gem (brilliant cut, blue)
def gem():
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    gy, ty, cy = 370, 110, 940                                   # girdle, table, culet y
    g = [(110, gy), (310, gy), (512, gy), (714, gy), (914, gy)]
    t = [(270, ty), (430, ty), (594, ty), (754, ty)]
    culet = (512, cy)
    # crown strip, alternate triangles; pavilion: girdle points to culet, split by mid points
    crown = [(g[0], t[0], g[1]), (t[0], g[1], t[1]), (g[1], t[1], g[2]), (t[1], g[2], t[2]),
             (g[2], t[2], g[3]), (t[2], g[3], t[3]), (g[3], t[3], g[4])]
    table = [t[0], t[1], t[2], t[3]]
    pav = []
    for i in range(4):
        m = ((g[i][0] + g[i + 1][0]) / 2, gy)
        pav += [(g[i], m, culet), (m, g[i + 1], culet)]
    # shades: light from the top left
    crown_cols = ['#6FD8FF', '#C4F5FF', '#45B8FF', '#A6EEFF', '#2E95FF', '#62C2FF', '#1F6BF0']
    pav_cols = ['#4CC4FF', '#1B8EFF', '#3AA2FF', '#0F66EE', '#2A86FF', '#0B52D8', '#1A60E6', '#0838B0']
    d = ImageDraw.Draw(img)
    # outline body (slightly bigger) for a crisp dark rim
    body = [g[0], t[0], t[3], g[4], culet]
    d.polygon(body, fill='#0B2A8A')
    inset = mask_of(lambda m: m.polygon(body, fill=255)).filter(ImageFilter.MinFilter(25))
    facets = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    fd = ImageDraw.Draw(facets)
    for poly, c in zip(crown, crown_cols): fd.polygon(poly, fill=c)
    fd.polygon(table, fill='#D9FAFF')
    for poly, c in zip(pav, pav_cols): fd.polygon(poly, fill=c)
    # thin bright facet lines
    for poly in crown + pav + [table]:
        fd.line(list(poly) + [poly[0]], fill=(255, 255, 255, 110), width=6)
    img.alpha_composite(Image.composite(facets, Image.new('RGBA', (S, S), (0, 0, 0, 0)), inset))
    # glossy streak + sparkle
    hl = Image.new('RGBA', (S, S), (0, 0, 0, 0)); hd = ImageDraw.Draw(hl)
    hd.polygon([(300, 140), (395, 140), (260, 350), (175, 350)], fill=(255, 255, 255, 190))
    hd.polygon([(200, 390), (270, 390), (440, 780), (405, 810)], fill=(255, 255, 255, 90))
    hl = hl.filter(ImageFilter.GaussianBlur(6))
    img.alpha_composite(Image.composite(hl, Image.new('RGBA', (S, S), (0, 0, 0, 0)), inset))
    sp = Image.new('RGBA', (S, S), (0, 0, 0, 0)); sd = ImageDraw.Draw(sp)
    cx, cy2, r = 700, 200, 80
    sd.polygon([(cx, cy2 - r), (cx + 14, cy2 - 14), (cx + r, cy2), (cx + 14, cy2 + 14), (cx, cy2 + r), (cx - 14, cy2 + 14), (cx - r, cy2), (cx - 14, cy2 - 14)], fill=(255, 255, 255, 235))
    img.alpha_composite(sp)
    return shadow_under(img, (0, 14), 12, 70)


# ------------------------------------------------------------------ coin (gold, star) and number medals
STAR_PTS = lambda cx, cy, R, r: [(cx + (R if i % 2 == 0 else r) * math.sin(i * math.pi / 5), cy - (R if i % 2 == 0 else r) * math.cos(i * math.pi / 5)) for i in range(10)]

GOLD = dict(rim1=(255, 214, 64), rim2=(214, 108, 0), face1=(255, 236, 120), face2=(255, 166, 20), edge=(150, 70, 0), emb=(214, 112, 0), embhl=(255, 245, 190))
SILVER = dict(rim1=(240, 246, 255), rim2=(120, 138, 168), face1=(250, 252, 255), face2=(170, 186, 210), edge=(78, 92, 122), emb=(130, 146, 175), embhl=(255, 255, 255))
BRONZE = dict(rim1=(255, 170, 110), rim2=(150, 62, 20), face1=(255, 190, 130), face2=(206, 104, 44), edge=(110, 40, 10), emb=(170, 74, 24), embhl=(255, 220, 190))


def disc(pal, emblem):
    """Round medallion: rim, bevel, face, emblem ('star' or a digit)."""
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    c = S // 2
    R, Rf = 470, 380
    d = ImageDraw.Draw(img)
    d.ellipse([c - R - 12, c - R - 12, c + R + 12, c + R + 12], fill=pal['edge'] + (255,))
    fill(img, mask_of(lambda m: m.ellipse([c - R, c - R, c + R, c + R], fill=255)), lin_grad((S, S), pal['rim1'] + (255,), pal['rim2'] + (255,), 70))
    # bevel: inner ring darker at top, lighter at bottom (sunken face)
    fill(img, mask_of(lambda m: m.ellipse([c - Rf - 18, c - Rf - 18, c + Rf + 18, c + Rf + 18], fill=255)), lin_grad((S, S), pal['rim2'] + (255,), pal['rim1'] + (255,), 70))
    fill(img, mask_of(lambda m: m.ellipse([c - Rf, c - Rf, c + Rf, c + Rf], fill=255)), rad_grad((S, S), (c - 120, c - 160), 560, pal['face1'] + (255,), pal['face2'] + (255,)))
    if emblem == 'star':
        pts = STAR_PTS(c, c + 18, 250, 112)
        sh = [(x + 10, y + 14) for x, y in pts]
        d = ImageDraw.Draw(img)
        d.polygon(sh, fill=pal['edge'] + (110,))
        d.polygon(pts, fill=pal['emb'] + (255,))
        inner = STAR_PTS(c, c + 18, 200, 90)
        fill(img, mask_of(lambda m: m.polygon(inner, fill=255)), lin_grad((S, S), (255, 214, 90, 255), (240, 150, 10, 255), 80))
    else:
        f = ImageFont.truetype(FONT, 560)
        txt = Image.new('RGBA', (S, S), (0, 0, 0, 0)); td = ImageDraw.Draw(txt)
        bb = td.textbbox((0, 0), emblem, font=f)
        x = c - (bb[0] + bb[2]) / 2; y = c - (bb[1] + bb[3]) / 2 + 10
        td.text((x + 10, y + 16), emblem, font=f, fill=pal['edge'] + (150,))
        td.text((x, y), emblem, font=f, fill=(255, 255, 255, 255), stroke_width=22, stroke_fill=pal['edge'] + (255,))
        img.alpha_composite(txt)
    # gloss: soft highlight on the upper left
    gl = Image.new('RGBA', (S, S), (0, 0, 0, 0)); gd = ImageDraw.Draw(gl)
    gd.ellipse([c - 330, c - 400, c + 90, c - 120], fill=(255, 255, 255, 95))
    gl = gl.filter(ImageFilter.GaussianBlur(30))
    img.alpha_composite(Image.composite(gl, Image.new('RGBA', (S, S), (0, 0, 0, 0)), mask_of(lambda m: m.ellipse([c - R, c - R, c + R, c + R], fill=255))))
    return img


def rosette(pal, ribbon, digit):
    """Ribbon medal: two ribbon tails behind a scalloped rosette with the number."""
    W = int(S * 1.25)
    img = Image.new('RGBA', (S, W), (0, 0, 0, 0))
    c = S // 2; cy = 430
    d = ImageDraw.Draw(img)
    # ribbon tails (V-cut ends), darker inside fold
    r1, r2 = ribbon
    for sx in (-1, 1):
        x0 = c + sx * 70
        tail = [(x0 - 120, cy + 120), (x0 + 120, cy + 120), (x0 + sx * 150 + 110, W - 40), (x0 + sx * 150, W - 150), (x0 + sx * 150 - 110, W - 40)]
        d.polygon([(x + 0, y + 0) for x, y in tail], fill=r2 + (255,))
        inner = [(x0 - 90, cy + 120), (x0 + 90, cy + 120), (x0 + sx * 150 + 70, W - 90), (x0 + sx * 150, W - 175), (x0 + sx * 150 - 70, W - 90)]
        d.polygon(inner, fill=r1 + (255,))
    # scalloped rosette
    n, Ro, bump = 22, 420, 46
    pts = []
    for i in range(n * 8):
        a = 2 * math.pi * i / (n * 8)
        r = Ro + bump * abs(math.cos(n * a / 2))
        pts.append((c + r * math.sin(a), cy - r * math.cos(a)))
    d.polygon([(x, y + 10) for x, y in pts], fill=pal['edge'] + (255,))
    fill(img, mask_of_sz(img.size, lambda m: m.polygon(pts, fill=255)), lin_grad(img.size, pal['rim1'] + (255,), pal['rim2'] + (255,), 70))
    medal = disc(pal, digit).resize((760, 760), Image.LANCZOS)
    img.alpha_composite(medal, (c - 380, cy - 380))
    return img


def mask_of_sz(size, fn):
    m = Image.new('L', size, 0); fn(ImageDraw.Draw(m)); return m


save(gem(), 'ic_gem.webp', 256)
save(shadow_under(disc(GOLD, 'star'), (0, 14), 12, 60), 'ic_coin.webp', 256)
for i, pal in enumerate((GOLD, SILVER, BRONZE), start=1):
    save(shadow_under(disc(pal, str(i)), (0, 12), 10, 60), f'medal_small_{i}.webp', 160)
ribbons = {1: ((255, 140, 40), (214, 84, 10)), 2: ((70, 140, 255), (30, 86, 214)), 3: ((235, 70, 60), (170, 34, 30))}
for i, pal in enumerate((GOLD, SILVER, BRONZE), start=1):
    img = rosette(pal, ribbons[i], str(i))
    # square canvas for save(): pad to square
    sq = Image.new('RGBA', (max(img.size), max(img.size)), (0, 0, 0, 0))
    sq.alpha_composite(img, ((sq.width - img.width) // 2, 0))
    save(shadow_under(sq, (0, 14), 12, 60), f'medal_{i}.webp', 224)
