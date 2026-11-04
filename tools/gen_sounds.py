"""Synthesizes every sound the mod ships (original, fixed seeds).

Run from the repo root:  python tools/gen_sounds.py
Requires: numpy, soundfile (libsndfile with Vorbis).

All sounds are mono so Minecraft attenuates them with distance (stereo sounds are not positional).
"""
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
OUT = Path("src/main/resources/assets/herobrine/sounds")


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def envelope(n, attack, release, curve=3.0):
    """Linear attack, exponential-ish release, in samples."""
    env = np.ones(n)
    a = max(1, int(attack * RATE))
    r = max(1, int(release * RATE))
    env[:a] = np.linspace(0, 1, a)
    tail = np.linspace(1, 0, min(r, n))
    env[n - len(tail):] *= tail ** curve
    return env


def lowpass(x, cutoff):
    """One-pole low-pass: cheap, smooth, good enough for rumble and breath."""
    alpha = 1.0 - np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def normalize(x, peak=0.89):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def jumpscare(rng):
    """Sharp dissonant stinger: detuned high cluster + noise burst + low thump."""
    d = 1.1
    tt = t(d)
    n = len(tt)
    cluster = sum(np.sign(np.sin(2 * np.pi * f * tt)) * 0.18 for f in (1318.5, 1396.9, 1975.5, 2093.0))
    cluster *= envelope(n, 0.004, 1.0, 2.5)
    noise = rng.standard_normal(n) * envelope(n, 0.002, 0.35, 4.0) * 0.6
    thump = np.sin(2 * np.pi * (70 - 30 * tt) * tt) * envelope(n, 0.003, 0.6, 3.0) * 0.9
    return normalize(cluster + noise + thump)


def drone(rng):
    """Low, slowly beating drone with a creeping filtered-noise wind (loops cleanly)."""
    d = 8.0
    tt = t(d)
    base = 55.0
    tone = (np.sin(2 * np.pi * base * tt) * 0.5
            + np.sin(2 * np.pi * (base * 1.5 + 0.25) * tt) * 0.25   # beating fifth
            + np.sin(2 * np.pi * (base * 2.0 - 0.125) * tt) * 0.15)
    wind = lowpass(rng.standard_normal(len(tt)), 400) * 3.0
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * tt / d)  # one full cycle -> seamless loop
    fade = envelope(len(tt), 0.5, 0.5, 1.0)
    return normalize((tone + wind * 0.4) * swell * fade, 0.7)


def whisper(rng):
    """Breathy, unintelligible whisper: band-limited noise with syllable-like amplitude bursts."""
    d = 2.4
    tt = t(d)
    noise = rng.standard_normal(len(tt))
    breath = lowpass(noise, 3200) - lowpass(noise, 900)
    syllables = np.zeros(len(tt))
    pos = 0.15
    while pos < d - 0.25:
        length = rng.uniform(0.09, 0.22)
        idx = (tt >= pos) & (tt < pos + length)
        syllables[idx] = np.sin(np.pi * (tt[idx] - pos) / length) ** 2 * rng.uniform(0.5, 1.0)
        pos += length + rng.uniform(0.03, 0.12)
    return normalize(breath * syllables * envelope(len(tt), 0.05, 0.4, 1.5), 0.6)


def heartbeat(rng):
    """Two slow, muffled heartbeats."""
    d = 2.0
    tt = t(d)
    out = np.zeros(len(tt))
    for start in (0.05, 0.33, 1.05, 1.33):
        idx = tt >= start
        local = tt[idx] - start
        beat = np.sin(2 * np.pi * 48 * local) * np.exp(-local * 18)
        out[idx] += beat * (1.0 if start in (0.05, 1.05) else 0.7)
    return normalize(lowpass(out, 300), 0.85)


def save(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.ogg"
    sf.write(path, samples.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print("wrote", path)


if __name__ == "__main__":
    for i, (name, fn) in enumerate([("jumpscare", jumpscare), ("drone", drone), ("whisper", whisper), ("heartbeat", heartbeat)]):
        save(name, fn(np.random.default_rng(1000 + i)))
