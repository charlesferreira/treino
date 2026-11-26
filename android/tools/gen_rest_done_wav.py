#!/usr/bin/env python3
"""Generates app/src/main/res/raw/rest_done.wav, the end-of-rest beep.

Same notes as the PWA (src/audio.ts): two 880 Hz beeps of 0.15 s at t=0 and t=0.22,
then one 1320 Hz beep of 0.3 s at t=0.44. Sine, gain ramps exponentially from 0.0001
to 0.5 in 20 ms, then exponentially down to 0.0001 at the end of the note; each
oscillator stops 50 ms after its note, as in the Web Audio version.

Run from android/:  python3 tools/gen_rest_done_wav.py
"""
import math
import struct
import wave
from pathlib import Path

RATE = 44_100
FLOOR = 0.0001
PEAK = 0.5
ATTACK = 0.02
TAIL = 0.05
NOTES = [(0.0, 880.0, 0.15), (0.22, 880.0, 0.15), (0.44, 1320.0, 0.3)]

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/rest_done.wav"


def exp_ramp(v0: float, v1: float, t0: float, t1: float, t: float) -> float:
    """Web Audio exponentialRampToValueAtTime: v0 * (v1/v0)^((t-t0)/(t1-t0))."""
    return v0 * (v1 / v0) ** ((t - t0) / (t1 - t0))


def gain(local_t: float, dur: float) -> float:
    if local_t < ATTACK:
        return exp_ramp(FLOOR, PEAK, 0.0, ATTACK, local_t)
    if local_t < dur:
        return exp_ramp(PEAK, FLOOR, ATTACK, dur, local_t)
    return FLOOR


def main() -> None:
    total = max(start + dur + TAIL for start, _, dur in NOTES)
    samples = [0.0] * int(math.ceil(total * RATE))
    for start, freq, dur in NOTES:
        first = int(round(start * RATE))
        for i in range(int(round((dur + TAIL) * RATE))):
            t = i / RATE
            samples[first + i] += gain(t, dur) * math.sin(2 * math.pi * freq * t)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print(f"wrote {OUT} ({len(samples)} samples, {total:.2f} s)")


if __name__ == "__main__":
    main()
