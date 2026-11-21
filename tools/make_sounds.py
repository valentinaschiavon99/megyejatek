#!/usr/bin/env python3
"""Synthesises the game's short sound effects into app/src/main/res/raw/*.ogg (needs numpy, ffmpeg).

Soft marimba-like notes, so they are pleasant at low volume and never clip.
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def note(freq, length, decay=9.0, volume=0.5):
    t = np.arange(int(RATE * length)) / RATE
    tone = np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 4 * t) * np.exp(-t * 30)
    attack = np.minimum(1.0, t / 0.004)
    return volume * tone * attack * np.exp(-t * decay)


def sequence(notes, gap):
    """notes: (freq, length) pairs started every `gap` seconds, overlapping like a struck bar."""
    total = gap * (len(notes) - 1) + max(length for _, length in notes)
    out = np.zeros(int(RATE * total) + 1)
    for i, (f, length) in enumerate(notes):
        start = int(RATE * gap * i)
        n = note(f, length)
        out[start:start + len(n)] += n
    return out / max(1.0, np.abs(out).max() / 0.8)


def C(n):  # semitones from C5
    return 523.25 * 2 ** (n / 12)


SOUNDS = {
    "sound_correct": sequence([(C(4), 0.25), (C(11), 0.35)], 0.07),          # E5 → B5
    "sound_wrong": sequence([(C(-5) * 0.5, 0.3), (C(-8) * 0.5, 0.35)], 0.09),  # low G → E, soft
    "sound_finish": sequence([(C(0), 0.4), (C(4), 0.4), (C(7), 0.4), (C(12), 0.7)], 0.11),
    "sound_lose": sequence([(C(7) * 0.5, 0.35), (C(3) * 0.5, 0.35), (C(0) * 0.5, 0.6)], 0.14),
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, samples in SOUNDS.items():
            wav = pathlib.Path(tmp) / f"{name}.wav"
            with wave.open(str(wav), "wb") as w:
                w.setnchannels(1)
                w.setsampwidth(2)
                w.setframerate(RATE)
                w.writeframes((samples * 32767).astype("<i2").tobytes())
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "4",
                            "-map_metadata", "-1", str(OUT / f"{name}.ogg")], check=True)
            print(OUT / f"{name}.ogg")


if __name__ == "__main__":
    main()
