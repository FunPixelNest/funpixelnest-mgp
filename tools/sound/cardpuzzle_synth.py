# Original, procedurally generated audio for Blackjack and Queen Sudoku (Unity games). No samples, no licensing.
# Writes WAVs into the current directory:
#   blackjack_music, blackjack_card_place, blackjack_deal, blackjack_hold, blackjack_21, blackjack_blackjack,
#   blackjack_five, blackjack_bust
#   qs_music, qs_mark, qs_unmark, qs_queen_1..qs_queen_6, qs_remove, qs_conflict, qs_life, qs_hint, qs_win, qs_timeup
# Music loops are rendered twice with tails wrapped and kept periodic, so they loop without a click. No noise beds
# (hiss); the only noise used is inside a few-millisecond card "snap" transient.
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
rng = np.random.default_rng(21)


def note(n): return 440.0 * 2 ** ((n - 69) / 12)
def lp(x, fc, o=2): b, a = butter(o, fc / (SR / 2), 'low'); return lfilter(b, a, x)
def hp(x, fc, o=2): b, a = butter(o, fc / (SR / 2), 'high'); return lfilter(b, a, x)
def bp(x, lo, hi, o=2): b, a = butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band'); return lfilter(b, a, x)
def norm(x, peak): return x / (np.max(np.abs(x)) + 1e-9) * peak
def T(d): return np.arange(int(d * SR)) / SR


def write(name, x):
    x = np.clip(x, -1, 1)
    if x.ndim == 1: x = np.stack([x, x], 1)
    wavfile.write(name + '.wav', SR, (x * 32767).astype(np.int16))


def fade_end(x, ms=10):
    n = int(SR * ms / 1000); x = x.copy(); x[-n:] *= np.linspace(1, 0, n); return x


def mix(dur, parts):
    out = np.zeros(int(dur * SR))
    for t0, s, g in parts:
        i = int(t0 * SR); e = min(len(out), i + len(s)); out[i:e] += s[:e - i] * g
    return out


def ep(f, dur, decay=3.0):          # electric-piano-ish tone (FM bell, warm)
    t = T(dur)
    mod = np.sin(2 * np.pi * f * 1.0 * t) * 1.2 * np.exp(-t * 6)
    s = np.sin(2 * np.pi * f * t + mod) + 0.25 * np.sin(2 * np.pi * 2 * f * t)
    return s * np.exp(-t * decay) * np.minimum(1, t / 0.006)


def bell(f, dur, decay=5.0, bright=0.4):   # celesta / vibraphone
    t = T(dur)
    s = np.sin(2 * np.pi * f * t) + bright * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t * 12) + 0.15 * np.sin(2 * np.pi * 2 * f * t)
    return s * np.exp(-t * decay) * np.minimum(1, t / 0.003)


def harp(f, dur, decay=3.5):
    t = T(dur)
    s = sum(a * np.sin(2 * np.pi * f * k * t) for k, a in ((1, 1), (2, .45), (3, .18), (4, .08)))
    return lp(s * np.exp(-t * decay) * np.minimum(1, t / 0.002), 6000)


def pluck_bass(f, dur):
    t = T(dur)
    s = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t) + 0.1 * np.sin(2 * np.pi * 3 * f * t)
    return lp(s * np.exp(-t * 2.8) * np.minimum(1, t / 0.008), 1200)


class Loop:
    def __init__(self, seconds):
        self.N = int(round(seconds * SR)); self.buf = np.zeros((2 * self.N, 2))

    def add(self, t0, sig, gain, pan=0.0):
        i = int(round(t0 * SR)) % self.N
        l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
        for k in range(2):
            j = i + k * self.N; e = min(len(self.buf), j + len(sig))
            self.buf[j:e, 0] += sig[:e - j] * gain * l; self.buf[j:e, 1] += sig[:e - j] * gain * r

    def render(self, cutoff, peak):
        y = self.buf[self.N:]
        # low-pass on a periodic signal: filter 3 copies, keep the middle one
        z = np.concatenate([y, y, y])
        z = np.stack([lp(z[:, 0], cutoff), lp(z[:, 1], cutoff)], 1)[self.N:2 * self.N]
        return norm(z, peak)


# =============================================================== BLACKJACK
# Casino lounge: 92 BPM swing, electric piano ii-V-I-VI, upright bass, soft pitched rim ticks.
BPM = 92; beat = 60 / BPM; bar = 4 * beat
prog = [  # (bass root, chord voicing)
    (50, [62, 65, 69, 72, 76]),   # Dm9
    (43, [59, 64, 65, 69, 71]),   # G13
    (48, [59, 62, 64, 67, 71]),   # Cmaj9
    (45, [61, 64, 67, 70, 73]),   # A7b9
] * 2
lp_ = Loop(len(prog) * bar)
sw = beat * 0.62  # swung 8th
for bi, (root, ch) in enumerate(prog):
    t0 = bi * bar
    # comping: chord on 1 (long) and on the swung "and" of 2 (short)
    for m in ch: lp_.add(t0, ep(note(m), bar * 0.9, 2.2), 0.05, pan=-0.2)
    for m in ch[1:]: lp_.add(t0 + beat + sw, ep(note(m), beat * 0.8, 6), 0.035, pan=-0.2)
    # walking bass: root, 3rd/5th, approach
    walk = [root, root + 7, root + 12 if bi % 2 == 0 else root + 4, prog[(bi + 1) % len(prog)][0] + 1]
    for b, m in enumerate(walk):
        lp_.add(t0 + b * beat, pluck_bass(note(m), beat * 1.05), 0.30)
    # rim "tick" on 2 and 4, very soft, pitched (no noise)
    for b in (1, 3):
        t = T(0.05); lp_.add(t0 + b * beat, np.sin(2 * np.pi * 1700 * t) * np.exp(-t * 90), 0.035, pan=0.3)
    # ride-ish: soft high bell on swung 8ths
    for b in range(4):
        t = T(0.12); ride = (np.sin(2 * np.pi * 3100 * t) + 0.5 * np.sin(2 * np.pi * 4650 * t)) * np.exp(-t * 30)
        lp_.add(t0 + b * beat, ride, 0.010, pan=0.35); lp_.add(t0 + b * beat + sw, ride, 0.006, pan=0.35)
    # a few melody notes on vibes, every other bar
    if bi % 2 == 1:
        mel = [ch[-1] + 12, ch[-2] + 12, ch[-3] + 12]
        for k, m in enumerate(mel):
            lp_.add(t0 + (k * 2 + 1) * beat * 0.5 + beat, bell(note(m - 12), 0.9, 3.5, 0.25), 0.05, pan=0.25)
write('blackjack_music', lp_.render(6500, 0.5))

# card landing in a slot: crisp paper snap (8 ms band-passed transient) + soft table "thock"
t = T(0.12)
snap = bp(rng.normal(0, 1, len(t)), 1800, 6500) * np.exp(-t * 260)
thock = np.sin(2 * np.pi * (230 * np.exp(-t * 25) + 110) * t) * np.exp(-t * 38)
write('blackjack_card_place', fade_end(norm(lp(snap * 0.9 + thock * 0.8, 9000), 0.6)))

# deal: card sliding off the deck (soft swish, shaped) + small snap at the end
t = T(0.16)
env = np.sin(np.pi * np.clip(t / 0.13, 0, 1)) ** 2
slide = bp(rng.normal(0, 1, len(t)), 900, 3800) * env
tail = np.zeros_like(t); k = int(0.12 * SR); tt = t[:len(t) - k]
tail[k:] = bp(rng.normal(0, 1, len(tt)), 2000, 6000) * np.exp(-tt * 300)
write('blackjack_deal', fade_end(norm(lp(slide * 0.35 + tail * 0.6, 8000), 0.42)))

# hold: card tucked into the Hold spot: lower, softer thock + light snap
t = T(0.12)
hold = np.sin(2 * np.pi * (180 * np.exp(-t * 20) + 95) * t) * np.exp(-t * 30) + 0.5 * bp(rng.normal(0, 1, len(t)), 1500, 4500) * np.exp(-t * 320)
write('blackjack_hold', fade_end(norm(lp(hold, 7000), 0.5)))

# 21: warm vibes triad, rising
write('blackjack_21', fade_end(norm(mix(0.9, [(0, bell(note(72), 0.8, 4), 1), (0.07, bell(note(76), 0.75, 4), 1), (0.14, bell(note(79), 0.7, 3.5), 1)]), 0.55), 40))
# Blackjack: bigger, arpeggio + chord bloom on EP
parts = [(i * 0.065, bell(note(m), 0.9, 4), 0.9) for i, m in enumerate([67, 72, 76, 79, 84])]
parts += [(0.33, ep(note(m), 1.0, 2.5), 0.6) for m in (72, 76, 79, 83)]
write('blackjack_blackjack', fade_end(norm(mix(1.4, parts), 0.6), 60))
# five cards: sparkly run
write('blackjack_five', fade_end(norm(mix(1.0, [(i * 0.05, bell(note(m), 0.6, 5, 0.5), 1) for i, m in enumerate([76, 79, 81, 84, 88])]), 0.5), 40))
# bust: soft descending "wah-wah" on a muted tone (no noise)
t = T(0.55)
f = np.where(t < 0.22, note(55), note(52)) * (1 - 0.04 * (t % 0.22) / 0.22)
s = np.sin(2 * np.pi * np.cumsum(f) / SR); s = s + 0.4 * np.sin(2 * np.pi * 2 * np.cumsum(f) / SR)
envb = np.minimum(1, (t % 0.22) / 0.01) * np.exp(-(t % 0.22) * 6) * (t < 0.5)
write('blackjack_bust', fade_end(norm(lp(s * envb, 1400), 0.5), 40))

# =============================================================== QUEEN SUDOKU
# Calm, elegant: 70 BPM, harp arpeggios over a soft pad, celesta melody. Lydian colour.
BPM = 70; beat = 60 / BPM; bar = 4 * beat
prog = [(53, [65, 69, 72, 76, 79]),   # Fmaj7(9)
        (55, [67, 71, 74, 78]),       # G(add#11 colour)
        (57, [69, 72, 76, 79]),       # Am7
        (52, [64, 67, 71, 74])] * 2   # Em7
q = Loop(len(prog) * bar)
mrng = np.random.default_rng(5)
for bi, (root, ch) in enumerate(prog):
    t0 = bi * bar
    # pad: soft sines, slow attack
    d = bar + 1.2; t = T(d)
    padenv = np.minimum(1, t / 0.9) * np.minimum(1, np.maximum(0, (d - t) / 1.0))
    pad = sum(np.sin(2 * np.pi * note(m - 12) * t + k) for k, m in enumerate(ch[:3])) * padenv
    q.add(t0, lp(pad, 1500), 0.035)
    q.add(t0, pluck_bass(note(root - 12), bar), 0.16)
    # harp: up-down arpeggio in 8ths
    arp = ch + ch[-2:0:-1]
    for k in range(8):
        q.add(t0 + k * beat / 2, harp(note(arp[k % len(arp)]), 1.6, 2.8), 0.07, pan=-0.3 + 0.08 * k)
    # celesta melody: a few notes per bar
    for k in range(4):
        if mrng.random() < 0.45: continue
        m = ch[mrng.integers(len(ch))] + 12
        q.add(t0 + k * beat + beat * 0.5, bell(note(m), 1.2, 3.2, 0.3), 0.045, pan=0.3)
write('qs_music', q.render(7000, 0.45))

# mark X: tiny soft wooden tick; unmark: lower
t = T(0.07)
write('qs_mark', fade_end(norm(np.sin(2 * np.pi * 1250 * t) * np.exp(-t * 85) + 0.5 * np.sin(2 * np.pi * 620 * t) * np.exp(-t * 60), 0.35)))
write('qs_unmark', fade_end(norm(np.sin(2 * np.pi * 820 * t) * np.exp(-t * 85) + 0.5 * np.sin(2 * np.pi * 410 * t) * np.exp(-t * 60), 0.3)))
# queen placed: harp + bell, pitch climbs with each queen (1..6) on a pentatonic scale
for i, m in enumerate([72, 74, 76, 79, 81, 84], start=1):
    s = mix(0.8, [(0, harp(note(m), 0.8, 4.5), 1), (0, bell(note(m + 12), 0.6, 6, 0.3), 0.5)])
    write(f'qs_queen_{i}', fade_end(norm(s, 0.55), 30))
# queen removed: soft falling blip
t = T(0.16); f = 700 * np.exp(-t * 6)
write('qs_remove', fade_end(norm(np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 20), 0.4)))
# conflict: gentle low double "dun-dun" (not harsh)
c1 = harp(note(50), 0.18, 18); c2 = harp(note(49), 0.22, 16)
write('qs_conflict', fade_end(norm(lp(mix(0.36, [(0, c1, 1), (0.12, c2, 1)]), 1500), 0.5)))
# life lost: heart thump + descending minor third
t = T(0.2); thump = np.sin(2 * np.pi * (90 * np.exp(-t * 12) + 50) * t) * np.exp(-t * 18)
write('qs_life', fade_end(norm(mix(0.6, [(0, thump, 1), (0.05, bell(note(67), 0.5, 7, 0.1), 0.4), (0.18, bell(note(64), 0.4, 7, 0.1), 0.4)]), 0.55), 30))
# hint: sparkle up
write('qs_hint', fade_end(norm(mix(0.6, [(i * 0.04, bell(note(m), 0.4, 9, 0.5), 1) for i, m in enumerate([84, 88, 91, 96])]), 0.45), 30))
# win: short (the app takes over 0.6 s later): quick celesta run + chord
parts = [(i * 0.05, bell(note(m), 0.5, 6), 1) for i, m in enumerate([72, 76, 79, 84])]
parts += [(0.2, harp(note(m), 0.5, 4), 0.7) for m in (72, 76, 79)]
write('qs_win', fade_end(norm(mix(0.62, parts), 0.6), 60))
# time up: three falling notes
write('qs_timeup', fade_end(norm(mix(0.8, [(i * 0.16, bell(note(m), 0.4, 6, 0.15), 1) for i, m in enumerate([76, 72, 67])]), 0.5), 40))
print('blackjack loop', len(prog) * bar)
