"""Synthesizes every sound the mod ships (original, fixed seeds).

Run from the repo root:  python tools/gen_sounds.py [name ...]
Requires: numpy, soundfile (libsndfile with Vorbis).

All sounds are mono so Minecraft attenuates them with distance (stereo sounds are not positional).
"""
import sys
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


def arrival(rng):
    """His arrival: a reversed-cymbal-like swell over a sinking sub tone, cut off dead.

    1.5 s long so that, played when the confrontation begins, the cut lands on the lightning.
    """
    d = 1.5
    tt = t(d)
    n = len(tt)
    rise = (tt / d) ** 3
    hiss = (lowpass(rng.standard_normal(n), 6000) - lowpass(rng.standard_normal(n), 1500)) * rise
    sub = np.sin(2 * np.pi * (48 - 10 * tt / d) * tt) * (0.3 + 0.7 * tt / d)
    cut = np.ones(n)
    cut[-int(0.02 * RATE):] = np.linspace(1, 0, int(0.02 * RATE))
    return normalize((hiss * 0.8 + sub) * envelope(n, 0.2, 0.01, 1.0) * cut, 0.85)


def shatter(rng):
    """A mirror breaking: a noise crack, then many short glassy partials ringing out."""
    d = 1.6
    tt = t(d)
    n = len(tt)
    crack = rng.standard_normal(n) * np.exp(-tt * 60) * 0.9
    shards = np.zeros(n)
    for _ in range(28):
        start = rng.uniform(0.0, 0.5)
        freq = rng.uniform(2200, 7500)
        decay = rng.uniform(8, 30)
        idx = tt >= start
        local = tt[idx] - start
        shards[idx] += np.sin(2 * np.pi * freq * local) * np.exp(-local * decay) * rng.uniform(0.2, 0.6)
    return normalize(crack + shards * 0.5, 0.8)


def save(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.ogg"
    sf.write(path, samples.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print("wrote", path)


if __name__ == "__main__":
    # Append new sounds at the end: each sound's seed is its index, so existing sounds keep their
    # waveform. The Vorbis encoder isn't bit-exact across runs, so regenerate only what you changed:
    #   python tools/gen_sounds.py arrival shatter
    sounds = [("jumpscare", jumpscare), ("drone", drone), ("whisper", whisper), ("heartbeat", heartbeat),
              ("arrival", arrival), ("shatter", shatter)]
    wanted = set(sys.argv[1:]) or {name for name, _ in sounds}
    unknown = wanted - {name for name, _ in sounds}
    if unknown:
        sys.exit("unknown sounds: " + ", ".join(sorted(unknown)))
    for i, (name, fn) in enumerate(sounds):
        if name in wanted:
            save(name, fn(np.random.default_rng(1000 + i)))
