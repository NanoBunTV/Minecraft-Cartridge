"""Synthesizes the mod's sounds (D108) into common/src/main/resources/assets/cartridge_mod/sounds.

Everything is made from sine waves and noise here, so the sounds are entirely the mod's own. Needs numpy and an
ffmpeg with libvorbis on the PATH. Run from anywhere:

    python tools/sounds.py

Each sound is mono (Minecraft only places mono sounds in the world), 44.1 kHz, Ogg Vorbis. The random generator is
seeded, so a run gives the same files every time. Variants (name_1, name_2...) differ slightly in pitch and noise.
"""

import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "common", "src", "main", "resources", "assets", "cartridge_mod",
                   "sounds")
rng = np.random.default_rng(8)


# --- Building blocks ----------------------------------------------------------------------------------------------

def silence(seconds):
    return np.zeros(int(RATE * seconds))


def t_of(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def env(seconds, attack, decay):
    """An envelope rising over `attack` seconds, then decaying exponentially with time constant `decay`."""
    t = t_of(seconds)
    rise = np.clip(t / max(attack, 1e-6), 0, 1)
    return rise * np.exp(-np.maximum(t - attack, 0) / decay)


def lowpass(x, cutoff):
    """A one-pole low-pass filter, run twice for a steeper slope."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i, v in enumerate(x):
            acc = (1 - a) * v + a * acc
            y[i] = acc
        x = y
    return x


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, low, high):
    return lowpass(highpass(x, low), high)


def noise(seconds):
    return rng.uniform(-1, 1, int(RATE * seconds))


def ping(freq, seconds, decay, phase=0.0):
    """A decaying sine: the ring of a small plastic or metal part."""
    t = t_of(seconds)
    return np.sin(2 * np.pi * freq * t + phase) * np.exp(-t / decay)


def click(bright=4000, seconds=0.02, decay=0.003, ring=None):
    """A short, sharp tick: a noise burst through a band around `bright`, with an optional ringing sine."""
    burst = bandpass(noise(seconds), bright * 0.4, bright) * env(seconds, 0.0003, decay)
    if ring:
        burst = burst + 0.4 * ping(ring, seconds, decay * 2)
    return burst


def thump(freq=110, seconds=0.12, decay=0.03, drop=0.5):
    """A low knock whose pitch falls a little, like plastic hitting plastic."""
    t = t_of(seconds)
    f = freq * (1 - drop * (1 - np.exp(-t / 0.02)))
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.sin(phase) * env(seconds, 0.001, decay)


def at(track, seconds, sound, gain=1.0):
    """Mixes `sound` into `track` starting at `seconds`, growing the track if needed."""
    start = int(RATE * seconds)
    end = start + len(sound)
    if end > len(track):
        track = np.concatenate([track, np.zeros(end - len(track))])
    track[start:end] += gain * sound
    return track


def finish(x, peak=0.85, fade=0.01):
    x = x - np.mean(x)
    n = int(RATE * fade)
    if n and len(x) > n:
        x[-n:] *= np.linspace(1, 0, n)
    top = np.max(np.abs(x))
    return x * (peak / top) if top > 0 else x


def write(name, x, peak=0.85):
    x = finish(x, peak)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((x * 32767).astype(np.int16).tobytes())
        ogg = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", ogg],
                       check=True)
    print("wrote", name)


# --- The sounds ---------------------------------------------------------------------------------------------------

def slide(seconds, rising):
    """A cartridge's plastic shell sliding against the slot: soft, filtered friction noise."""
    body = bandpass(noise(seconds), 600, 3000)
    shape = np.linspace(0.3, 1, len(body)) if rising else np.linspace(1, 0.3, len(body))
    return body * shape * env(seconds, 0.02, seconds)


def cart_insert(v):
    s = slide(0.16, True) * 0.35
    s = at(s, 0.15, thump(140 * v, 0.14, 0.035), 0.9)
    s = at(s, 0.15, click(3000 * v, 0.03, 0.004, ring=1900 * v), 0.6)
    return s


def cart_eject(v):
    s = thump(170 * v, 0.1, 0.025) * 0.6
    s = at(s, 0.0, click(3500 * v, 0.03, 0.004, ring=2300 * v), 0.6)
    s = at(s, 0.04, slide(0.18, False), 0.35)
    return s


def switch(v, pitch):
    """A chunky slide switch: two ticks a few milliseconds apart, the second louder, and a small knock."""
    s = click(4500 * pitch * v, 0.03, 0.002, ring=2600 * pitch * v) * 0.5
    s = at(s, 0.022, click(3800 * pitch * v, 0.04, 0.004, ring=2200 * pitch * v), 1.0)
    s = at(s, 0.022, thump(180 * pitch, 0.08, 0.02), 0.5)
    return s


def reset_button(v):
    """A momentary button: pressed, then sprung back up."""
    s = click(5000 * v, 0.03, 0.002, ring=3100 * v)
    s = at(s, 0.11, click(5500 * v, 0.03, 0.0015, ring=3300 * v), 0.55)
    return s


def plug(v):
    """A connector seating: a short scrape, then a firm snap."""
    s = bandpass(noise(0.05), 1500, 6000) * env(0.05, 0.01, 0.02) * 0.25
    s = at(s, 0.045, click(5000 * v, 0.03, 0.003, ring=3400 * v), 1.0)
    s = at(s, 0.045, thump(220 * v, 0.06, 0.015), 0.35)
    return s


def unplug(v):
    """Pulling a connector out: a pop and a short scrape."""
    s = click(4200 * v, 0.03, 0.003, ring=2800 * v)
    s = at(s, 0.012, bandpass(noise(0.07), 1500, 6000) * env(0.07, 0.005, 0.025), 0.3)
    return s


def rattle(count, spread, v):
    """A handful of tiny plastic ticks: a pad's buttons and cord moving as it's handled."""
    s = silence(spread + 0.05)
    for _ in range(count):
        at_s = rng.uniform(0, spread)
        s = at(s, at_s, click(rng.uniform(3000, 6000) * v, 0.02, 0.0015), rng.uniform(0.2, 0.6))
    return s


def pad_take(v):
    s = bandpass(noise(0.18), 300, 1500) * env(0.18, 0.04, 0.06) * 0.25  # cloth-like rustle of the grab
    s = at(s, 0.02, rattle(5, 0.12, v), 0.8)
    return s


def pad_put_down(v):
    s = thump(130 * v, 0.12, 0.03) * 0.8
    s = at(s, 0.0, click(2500 * v, 0.03, 0.004), 0.5)
    s = at(s, 0.01, rattle(4, 0.08, v), 0.6)
    return s


def tv_on(v):
    """A CRT coming on: the relay's clunk, the degaussing coil's falling hum, static crackle, and the high whine of
    the line transformer swelling and fading as the picture warms up (D109: only at power-on)."""
    s = thump(90 * v, 0.1, 0.02, drop=0.2) * 0.6
    s = at(s, 0.0, click(2500 * v, 0.03, 0.003), 0.5)
    # Degauss: a 60 Hz hum with its harmonics, loud and buzzing, dying away over about a second.
    t = t_of(1.0)
    hum = (np.sin(2 * np.pi * 60 * t) + 0.6 * np.sin(2 * np.pi * 120 * t) + 0.4 * np.sign(np.sin(2 * np.pi * 120 * t)))
    hum *= np.exp(-t / 0.22) * np.clip(t / 0.01, 0, 1)
    s = at(s, 0.04, hum, 0.45)
    # Crackle of static charge on the glass.
    crackle = silence(0.6)
    for _ in range(18):
        crackle = at(crackle, rng.uniform(0, 0.5), click(rng.uniform(4000, 9000), 0.01, 0.0008), rng.uniform(0.1, 0.5))
    s = at(s, 0.08, crackle, 0.6)
    # The whine, quiet, swelling as the tube warms and then fading to nothing.
    t = t_of(0.9)
    whine = np.sin(2 * np.pi * 15734 * t) * np.sin(np.pi * np.clip(t / 0.9, 0, 1)) ** 2
    s = at(s, 0.1, whine, 0.05)
    return s


def tv_off(v):
    """A CRT switching off: a falling 'zip' as the picture collapses, a pop, and a little crackle."""
    t = t_of(0.18)
    f = 1800 * v * np.exp(-t / 0.05) + 120
    zip_ = np.sin(2 * np.pi * np.cumsum(f) / RATE) * env(0.18, 0.003, 0.06)
    s = zip_ * 0.5
    s = at(s, 0.0, click(3000 * v, 0.03, 0.003), 0.7)
    s = at(s, 0.0, thump(80, 0.12, 0.03), 0.5)
    crackle = silence(0.4)
    for _ in range(8):
        crackle = at(crackle, rng.uniform(0, 0.35), click(rng.uniform(4000, 9000), 0.01, 0.0008), rng.uniform(0.05, 0.3))
    s = at(s, 0.1, crackle, 0.5)
    return s


def cable_link(v):
    """Three RCA plugs pushed home one after another (yellow, white, red, D103)."""
    s = silence(0.05)
    for i in range(3):
        s = at(s, i * 0.09, plug(v * (1 + 0.04 * i)), 0.8)
    return s


SOUNDS = {
    "cart_insert": (cart_insert, 2),
    "cart_eject": (cart_eject, 2),
    "power_on": (lambda v: switch(v, 1.0), 1),
    "power_off": (lambda v: switch(v, 0.8), 1),
    "reset": (reset_button, 1),
    "plug": (plug, 2),
    "unplug": (unplug, 2),
    "pad_take": (pad_take, 2),
    "pad_put_down": (pad_put_down, 2),
    "tv_on": (tv_on, 1),
    "tv_off": (tv_off, 1),
    "cable_link": (cable_link, 1),
}

if __name__ == "__main__":
    for name, (make, variants) in SOUNDS.items():
        for i in range(variants):
            v = 1.0 + 0.05 * (i - (variants - 1) / 2)  # small pitch spread between variants
            write(name if variants == 1 else f"{name}_{i + 1}", make(v))
