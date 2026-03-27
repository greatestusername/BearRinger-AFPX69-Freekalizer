# Latency and Callback Jitter Harness (E8-S5)

This project now includes a lightweight callback timing harness in the Android backend:

- `AudioCallbackJitterTracker`
- `AudioLatencySnapshot`
- `AndroidAudioBackend.latencySnapshot()`
- `AndroidAudioEngineController.latencySnapshot()`

The harness is designed to be always-on with low overhead and no per-callback allocations.

## Metrics Captured

- Callback interval average/min/max (ms)
- Callback interval jitter (std dev in ms)
- Blocking read average/max (ms)
- DSP process average/max (ms)
- Blocking write average/max (ms)
- Callback count

## How to Capture

1. Start monitoring and run a representative session (idle + active FX + scratch).
2. After at least 60 seconds, read `latencySnapshot()` from controller in debug instrumentation.
3. Record snapshot values with device/API/build info in your test notes.

## Suggested Initial Thresholds (Tunable)

- Callback jitter std dev: **< 2.0 ms**
- Process max: **< burst duration** (for 48kHz / 256 frames, target under ~5.3 ms)
- Read/write max spikes: investigate sustained spikes > 20 ms

These are engineering guardrails, not hard product limits yet.

