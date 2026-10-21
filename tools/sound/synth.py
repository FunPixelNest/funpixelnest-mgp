# Original, procedurally generated audio for the FunPixelNest app (no samples, no licensing issues).
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter
SR = 44100
rng = np.random.default_rng(7)

def note(n):  # MIDI -> Hz
    return 440.0 * 2 ** ((n - 69) / 12)

def env_adsr(n, a, d, s, r, total):
    t = np.arange(n) / SR
    e = np.ones(n) * s
    e[t < a] = t[t < a] / a
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / d
    rel_start = total - r
    m = t >= rel_start
    e[m] = s * np.clip(1 - (t[m] - rel_start) / r, 0, 1)
    return e

def lowpass(x, fc, order=2):
    b, a = butter(order, fc / (SR / 2), 'low')
    return lfilter(b, a, x)

def highpass(x, fc, order=2):
    b, a = butter(order, fc / (SR / 2), 'high')
    return lfilter(b, a, x)

def reverb(x, mix=0.3, decay=0.5):
    # Simple Schroeder-style: a few feedback combs + allpass.
    out = np.zeros_like(x)
    for dly, g in [(1557, 0.84), (1617, 0.83), (1491, 0.82), (1422, 0.81), (1277, 0.8), (1356, 0.79)]:
        y = np.zeros(len(x) + dly)
        buf = np.zeros_like(x)
        # comb via recursive filter
        b = np.zeros(dly + 1); b[-1] = 1
        a = np.zeros(dly + 1); a[0] = 1; a[-1] = -g * decay / 0.5 * 0.5 if False else -g
        buf = lfilter(b, a, x)
        out += buf
    out /= 6
    for dly, g in [(225, 0.5), (556, 0.5)]:
        b = np.zeros(dly + 1); b[0] = -g; b[-1] = 1
        a = np.zeros(dly + 1); a[0] = 1; a[-1] = -g
        out = lfilter(b, a, out)
    return (1 - mix) * x + mix * out

def norm(x, peak=0.8):
    return x / (np.max(np.abs(x)) + 1e-9) * peak

def write(name, x):
    x = np.clip(x, -1, 1)
    if x.ndim == 1:
        x = np.stack([x, x], 1)
    wavfile.write(name, SR, (x * 32767).astype(np.int16))

# ------------------------------------------------------------------ ambient music loop
BPM = 72
beat = 60 / BPM
bar = beat * 4
chords = [  # soothing progression in C: Cmaj9, Am9, Fmaj7(#11), Gsus/6
    [48, 55, 59, 62, 64],
    [45, 52, 55, 59, 60],
    [41, 48, 52, 57, 59],
    [43, 50, 55, 57, 62],
]
bars_per_chord = 2
L = bar * bars_per_chord * len(chords)        # loop length (~26.7 s)
N = int(L * SR)
total = np.zeros((3 * N, 2))
pent = [60, 62, 64, 67, 69, 72, 74, 76, 79]

def add(buf, start, sig, pan=0.0):
    i = int(start * SR)
    j = min(len(buf), i + len(sig))
    if i >= len(buf):
        return
    l = np.cos((pan + 1) * np.pi / 4); r = np.sin((pan + 1) * np.pi / 4)
    buf[i:j, 0] += sig[: j - i] * l
    buf[i:j, 1] += sig[: j - i] * r

for cycle in range(3):
    base = cycle * L
    for ci, ch in enumerate(chords):
        cs = base + ci * bars_per_chord * bar
        dur = bars_per_chord * bar + 1.5
        n = int(dur * SR); t = np.arange(n) / SR
        pad = np.zeros(n)
        for k, m in enumerate(ch):
            f = note(m)
            for det in (-0.12, 0.12):
                ff = f * 2 ** (det / 12)
                ph = (k * 1.7 + ci * 0.9 + (det > 0) * 2.3) % (2 * np.pi)  # same every cycle
                pad += np.sin(2 * np.pi * ff * t + ph) * (0.55 if k == 0 else 0.35)
                pad += 0.12 * np.sin(2 * np.pi * 2 * ff * t + ph)
        pad *= env_adsr(n, 1.6, 0.5, 0.85, 1.8, dur)
        pad *= 1 + 0.08 * np.sin(2 * np.pi * 0.25 * t)  # slow swell
        pad = lowpass(pad, 1400)
        add(total, cs, pad * 0.05, pan=-0.15 + 0.1 * ci)
        # soft sub bass
        nb = int(bars_per_chord * bar * SR); tb = np.arange(nb) / SR
        bass = np.sin(2 * np.pi * note(ch[0] - 12) * tb) * env_adsr(nb, 0.4, 0.3, 0.8, 0.8, bars_per_chord * bar)
        add(total, cs, bass * 0.10)
        # gentle pluck arpeggio (8th notes with rests), chord tones / pentatonic
        tones = sorted(set([m + 12 for m in ch[1:]] + [p for p in pent if p % 12 in [c % 12 for c in ch]]))
        steps = int(bars_per_chord * 8)
        prng = np.random.default_rng(100 + ci)  # same pattern every cycle (seamless loop)
        for s in range(steps):
            if prng.random() < 0.38:
                continue
            m = tones[prng.integers(0, len(tones))]
            pd = 1.4; pn = int(pd * SR); pt = np.arange(pn) / SR
            f = note(m)
            pl = (np.sin(2 * np.pi * f * pt) + 0.3 * np.sin(2 * np.pi * 2 * f * pt) + 0.08 * np.sin(2 * np.pi * 3 * f * pt))
            pl *= np.exp(-pt * 3.2) * np.minimum(1, pt / 0.004)
            pl = lowpass(pl, 3200)
            vel = 0.5 + 0.5 * prng.random()
            st = cs + s * beat / 2 + prng.normal(0, 0.008)
            pan = prng.uniform(-0.6, 0.6)
            add(total, st, pl * 0.045 * vel, pan)
            add(total, st + beat * 0.75, pl * 0.016 * vel, -pan)  # echo
    # airy texture: filtered noise shimmer
na = 3 * N
air1 = lowpass(highpass(rng.normal(0, 1, N * 2), 3000), 6000)[N:]  # one settled period
air = np.tile(air1, 3) * 0.004
air *= 1 + 0.5 * np.sin(2 * np.pi * np.arange(na) / N * 2)
total[:, 0] += air; total[:, 1] += np.roll(air, 1234)

wet = np.stack([reverb(total[:, 0], 0.35), reverb(total[:, 1], 0.35)], 1)
loop = wet[N:2 * N]
loop = norm(loop, 0.55)
write('music_ambient.wav', loop)

# ------------------------------------------------------------------ UI sounds
def tone(freqs, dur, decay, amp=1.0, attack=0.003, harm=(1.0, 0.25, 0.06)):
    n = int(dur * SR); t = np.arange(n) / SR
    x = np.zeros(n)
    for f in freqs:
        for k, h in enumerate(harm):
            x += h * np.sin(2 * np.pi * f * (k + 1) * t)
    x *= np.exp(-t * decay) * np.minimum(1, t / attack)
    return x * amp

def seq(parts, dur):
    out = np.zeros(int(dur * SR))
    for st, sig in parts:
        i = int(st * SR); j = min(len(out), i + len(sig))
        out[i:j] += sig[: j - i]
    return out

def fade_end(x, ms=15):
    n = int(ms / 1000 * SR); x[-n:] *= np.linspace(1, 0, n); return x

# tap: soft rounded "pop"
n = int(0.09 * SR); t = np.arange(n) / SR
f = 900 * np.exp(-t * 18) + 380
tap = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 55) * np.minimum(1, t / 0.002)
write('sfx_tap.wav', fade_end(norm(lowpass(tap, 4000), 0.55)))

# tab: airy tick + tiny chime
tab = seq([(0, tone([note(84)], 0.25, 22, 0.6, harm=(1, 0.2))), (0, tap[: int(0.05 * SR)] * 0.5)], 0.28)
write('sfx_tab.wav', fade_end(norm(reverb(tab, 0.2), 0.45)))

# popup: gentle rising two-note bell
pop = seq([(0, tone([note(76)], 0.6, 7, 0.7)), (0.07, tone([note(83)], 0.7, 6, 0.6))], 0.8)
write('sfx_popup.wav', fade_end(norm(reverb(pop, 0.3), 0.5)))

# close: soft falling
cl = seq([(0, tone([note(79)], 0.35, 12, 0.6)), (0.05, tone([note(72)], 0.4, 11, 0.5))], 0.45)
write('sfx_close.wav', fade_end(norm(reverb(cl, 0.2), 0.4)))

# coins / reward: sparkly bells
parts = []
for i, m in enumerate([84, 88, 91, 96, 91, 96]):
    parts.append((i * 0.055, tone([note(m)], 0.5, 9, 0.6 - i * 0.05, harm=(1, 0.5, 0.2))))
coin = seq(parts, 0.85)
write('sfx_reward.wav', fade_end(norm(reverb(coin, 0.35), 0.55)))

# level up: warm arpeggio + chord bloom
parts = []
for i, m in enumerate([60, 64, 67, 72, 76, 79]):
    parts.append((i * 0.08, tone([note(m)], 0.9, 4.5, 0.55, harm=(1, 0.35, 0.1))))
parts.append((0.5, tone([note(72), note(76), note(79), note(84)], 1.4, 2.4, 0.35, attack=0.03)))
lv = seq(parts, 2.0)
write('sfx_levelup.wav', fade_end(norm(reverb(lv, 0.4), 0.6), 60))

# error: soft low double thud
er = seq([(0, tone([note(52)], 0.18, 25, 0.8, harm=(1, 0.3))), (0.12, tone([note(48)], 0.22, 22, 0.8, harm=(1, 0.3)))], 0.38)
write('sfx_error.wav', fade_end(norm(lowpass(er, 2000), 0.45)))

# toggle: tiny click
tg = tone([note(91)], 0.06, 60, 0.6, harm=(1, 0.1))
write('sfx_toggle.wav', fade_end(norm(tg, 0.4)))
print('loop seconds', L)
