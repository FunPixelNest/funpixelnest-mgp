# Original, procedurally generated audio for Bird Dash (Unity game "FlapDash"). No samples, no licensing issues.
# Writes WAVs into the current directory: bd_music.wav (seamless loop), bd_flap.wav, bd_coin.wav, bd_hit.wav.
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter
SR = 44100

def note(n): return 440.0 * 2 ** ((n - 69) / 12)
def lowpass(x, fc, order=2):
    b, a = butter(order, fc / (SR / 2), 'low'); return lfilter(b, a, x)
def norm(x, peak): return x / (np.max(np.abs(x)) + 1e-9) * peak
def write(name, x):
    x = np.clip(x, -1, 1)
    if x.ndim == 1: x = np.stack([x, x], 1)
    wavfile.write(name, SR, (x * 32767).astype(np.int16))
def fade_end(x, ms=12):
    n = int(SR * ms / 1000); x = x.copy(); x[-n:] *= np.linspace(1, 0, n); return x

# ------------------------------------------------------------ music: bright, bouncy, 128 BPM, 8 bars, loops cleanly
BPM = 128; beat = 60 / BPM; bar = 4 * beat
prog = [(60, 'M'), (57, 'm'), (53, 'M'), (55, 'M')] * 2          # C  Am  F  G
L = len(prog) * bar; N = int(round(L * SR))
buf = np.zeros(2 * N)                                             # render 2 loops, keep the 2nd (tails wrap in)

def add(start, sig, gain):
    i = int(round(start * SR)) % N
    for k in range(2):                                            # write into both copies so tails wrap
        j = i + k * N; e = min(len(buf), j + len(sig)); buf[j:e] += sig[: e - j] * gain

def pluck(f, dur, decay, harm=(1, .35, .12)):
    t = np.arange(int(dur * SR)) / SR
    s = sum(a * np.sin(2 * np.pi * f * (h + 1) * t) for h, a in enumerate(harm))
    return s * np.exp(-t * decay) * np.minimum(1, t / 0.004)

mel_rng = np.random.default_rng(11)
for bi, (root, q) in enumerate(prog):
    t0 = bi * bar
    third = 4 if q == 'M' else 3
    chord = [root, root + third, root + 7]
    # bass: root on beats, octave hop on the "and" of 2 and 4
    for b in range(4):
        add(t0 + b * beat, pluck(note(root - 24), beat * 0.9, 7, (1, .5, .1)), 0.33)
        if b % 2 == 1: add(t0 + (b + 0.5) * beat, pluck(note(root - 12), beat * 0.45, 9, (1, .4)), 0.18)
    # offbeat chord stabs
    for b in range(4):
        for m in chord: add(t0 + (b + 0.5) * beat, pluck(note(m), beat * 0.4, 14, (1, .25)), 0.07)
    # lead: 8th-note melody from the chord + pentatonic, simple and catchy
    scale = sorted(set(chord + [m + 12 for m in chord] + [72, 74, 76, 79]))
    scale = [m for m in scale if 64 <= m <= 81]
    for e in range(8):
        if mel_rng.random() < 0.3: continue
        m = scale[mel_rng.integers(len(scale))]
        add(t0 + e * beat / 2, pluck(note(m), beat * 0.6, 8, (1, .2, .05)), 0.13)
    # soft kick on every beat, light "tick" hat on offbeats (pitched, no noise)
    for b in range(4):
        t = np.arange(int(0.18 * SR)) / SR
        kick = np.sin(2 * np.pi * (55 + 90 * np.exp(-t * 30)) * t) * np.exp(-t * 18)
        add(t0 + b * beat, kick, 0.38)
        th = np.arange(int(0.04 * SR)) / SR
        add(t0 + (b + 0.5) * beat, np.sin(2 * np.pi * 5200 * th) * np.exp(-th * 140), 0.025)

music = lowpass(buf[N:2 * N], 7000)                             # second copy: tails from the first wrapped in
write('bd_music.wav', norm(music, 0.5))

# ------------------------------------------------------------ flap: quick rising "boop"
t = np.arange(int(0.11 * SR)) / SR
f = 420 + 520 * (t / t[-1]) ** 0.7
flap = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 24) * np.minimum(1, t / 0.003)
write('bd_flap.wav', fade_end(norm(lowpass(flap, 5000), 0.5)))

# ------------------------------------------------------------ coin: classic two-note ding (B5 -> E6)
a = pluck(note(83), 0.07, 20, (1, .3, .1)); b = pluck(note(88), 0.32, 9, (1, .3, .1))
coin = np.zeros(int(0.40 * SR)); coin[:len(a)] += a; s = int(0.065 * SR); coin[s:s + len(b)] += b
write('bd_coin.wav', fade_end(norm(lowpass(coin, 8000), 0.5)))

# ------------------------------------------------------------ hit: soft low thump + falling tone
t = np.arange(int(0.3 * SR)) / SR
hit = np.sin(2 * np.pi * (180 * np.exp(-t * 6) + 60) * t) * np.exp(-t * 10)
write('bd_hit.wav', fade_end(norm(lowpass(hit, 2500), 0.6)))
print('music loop seconds', L)
