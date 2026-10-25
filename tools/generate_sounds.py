#!/usr/bin/env python3
"""
Synthesizes the Guilded SMP sound effects from scratch (no samples, no third-party audio).

Every sound is built from sine waves, filtered noise and envelopes, then written as mono
Ogg Vorbis (Minecraft needs mono for positional sounds).

Usage:  python tools/generate_sounds.py
Writes: resourcepack/assets/guildedsmp/sounds/legendary/*.ogg
Needs:  numpy, soundfile
"""
import os

import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "resourcepack", "assets", "guildedsmp", "sounds", "legendary")
SR = 44100
RNG = np.random.default_rng(20261024)  # fixed seed: rebuilding gives identical files


def t_axis(seconds):
    return np.arange(int(SR * seconds)) / SR


def env(t, attack, decay):
    """Linear attack then exponential decay."""
    a = np.clip(t / max(attack, 1e-6), 0, 1)
    d = np.exp(-np.maximum(t - attack, 0) / decay)
    return a * d


def one_pole_lowpass(x, cutoff):
    """cutoff may be a scalar or a per-sample array (Hz)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    alpha = 1 - np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha[i] * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - one_pole_lowpass(x, cutoff)


def sweep(t, f_start, f_end, curve="exp"):
    if curve == "exp":
        f = f_start * (f_end / f_start) ** (t / t[-1])
    else:
        f = f_start + (f_end - f_start) * (t / t[-1])
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def finish(x, peak_db=-1.0):
    x = x - np.mean(x)
    fade = int(0.006 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    peak = np.max(np.abs(x)) or 1.0
    return (x / peak * 10 ** (peak_db / 20)).astype(np.float32)


def inferno_ignite():
    t = t_axis(0.75)
    noise = RNG.standard_normal(len(t))
    cutoff = 700 + 2600 * np.exp(-((t - 0.12) / 0.12) ** 2) + 400 * np.exp(-t / 0.4)
    whoosh = one_pole_lowpass(noise, cutoff) * env(t, 0.03, 0.22) * 1.6
    thump = np.sin(2 * np.pi * 85 * t) * env(t, 0.004, 0.08) * 0.8
    crackle = np.zeros_like(t)
    for _ in range(22):
        start = int(RNG.uniform(0.06, 0.6) * SR)
        length = int(0.004 * SR)
        amp = RNG.uniform(0.2, 0.7) * np.exp(-start / SR / 0.35)
        crackle[start:start + length] += amp * RNG.standard_normal(length) * np.linspace(1, 0, length)
    crackle = highpass(crackle, 1500)
    return finish(whoosh + thump + crackle * 1.4)


def frostbite_chill():
    t = t_axis(1.0)
    partials = [(1568.0, 0.55, 0.30), (2349.3, 0.40, 0.22), (3136.0, 0.30, 0.16), (4186.0, 0.22, 0.10), (5274.0, 0.12, 0.07)]
    chime = np.zeros_like(t)
    for freq, amp, decay in partials:
        detune = 1 + 0.002 * np.sin(2 * np.pi * 5.5 * t)
        chime += amp * np.sin(2 * np.pi * freq * detune * t) * env(t, 0.002, decay)
    crack_noise = RNG.standard_normal(len(t))
    crack = highpass(crack_noise, 4000) * env(t, 0.001, 0.025) * 0.9
    hiss = highpass(RNG.standard_normal(len(t)), 6000) * env(t, 0.08, 0.35) * 0.12
    return finish(chime + crack + hiss)


def void_drain():
    t = t_axis(0.95)
    tone = sweep(t, 720, 95) * env(t, 0.02, 0.45)
    sub = sweep(t, 360, 48) * env(t, 0.02, 0.55) * 0.6
    tremolo = 0.65 + 0.35 * np.sin(2 * np.pi * 13 * t)
    swell = one_pole_lowpass(RNG.standard_normal(len(t)), 900) * np.clip(t / 0.5, 0, 1) ** 2 * env(t, 0.5, 0.15) * 1.2
    return finish(np.tanh(1.6 * (tone + sub) * tremolo) + swell)


def void_reap():
    t = t_axis(1.5)
    boom = np.tanh(2.2 * sweep(t, 62, 34) * env(t, 0.006, 0.35))
    chord = np.zeros_like(t)
    for freq in (220.0, 261.63, 329.63, 440.0):
        for detune in (-0.6, 0.6):
            chord += np.sin(2 * np.pi * (freq + detune) * t + RNG.uniform(0, 6.28))
    chord = one_pole_lowpass(chord, 1800) * env(t, 0.25, 0.5) * 0.18
    air = highpass(RNG.standard_normal(len(t)), 3000) * env(t, 0.01, 0.12) * 0.25
    return finish(boom + chord + air)


def forge():
    t = t_axis(1.7)
    strike = np.zeros_like(t)
    f0 = 620.0
    for ratio, amp, decay in [(1.0, 0.6, 0.55), (2.32, 0.45, 0.40), (4.25, 0.30, 0.25), (6.63, 0.18, 0.15), (9.1, 0.10, 0.08)]:
        strike += amp * np.sin(2 * np.pi * f0 * ratio * t) * env(t, 0.001, decay)
    clank = highpass(RNG.standard_normal(len(t)), 2500) * env(t, 0.0005, 0.02) * 0.8
    sparkle = np.zeros_like(t)
    for i, freq in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        start = 0.32 + i * 0.11
        tt = np.maximum(t - start, 0)
        sparkle += (t >= start) * np.sin(2 * np.pi * freq * tt) * np.exp(-tt / 0.22) * 0.35
    return finish(strike + clank + sparkle)


SOUNDS = {
    "inferno_ignite": inferno_ignite,
    "frostbite_chill": frostbite_chill,
    "void_drain": void_drain,
    "void_reap": void_reap,
    "forge": forge,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SOUNDS.items():
        path = os.path.join(OUT, name + ".ogg")
        sf.write(path, fn(), SR, format="OGG", subtype="VORBIS")
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
