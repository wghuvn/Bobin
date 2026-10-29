# FPV motor loop provenance

`src/main/resources/assets/bobin_craft/sounds/drone/fpv_motor_loop.ogg` is an original procedural sound synthesized for this project during the 2026-10-27 stabilization milestone. It contains no source recording, sampled Minecraft animal sound, commercial recording or third-party audio.

The accompanying `generate_fpv_motor.py` is the source: four detuned blade/rotor harmonic series with periodic frequency/amplitude modulation, plus deterministically seeded, band-shaped periodic airflow noise. Integer/half-integer carrier frequencies complete integral cycles in eight seconds. NumPy produces PCM; FFmpeg encodes mono Vorbis at 48 kHz. These are asset-production tools, not new mod dependencies. The signal is original synthesis, not an authentic field recording of a named motor or aircraft.

Regenerate from project root with `python docs/audio/generate_fpv_motor.py` (NumPy and FFmpeg required). OGG container identifiers may differ between encodes, so byte-identical encoding is not promised; retain the shipped asset hash for checkpoint identity.

Actual decoded asset results and SHA-256 are in `docs/Stabilization/audio-analysis.json`: 384,000 decoded samples; eight seconds; mono; no sample clipping; negligible DC; loop boundary step below the 99th percentile of adjacent sample changes. This numerical check does not establish perceived sound quality, a click-free result in every sound backend, or in-game attenuation. Listening, pilot/observer comparison and gameplay loop continuity remain NOT EXECUTED.
