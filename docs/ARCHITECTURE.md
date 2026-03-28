# Architecture Notes

## Goals

- Ship **Freekalizer AFPX69** (user-facing app name on Android; package id remains `com.freekalizer.tablet`).
- Keep low-latency-sensitive logic portable and testable.
- Isolate platform-specific audio/device code from DSP/timing/domain behavior.
- Avoid duplicated rules for UI labels/layout semantics.

## Modules

## `app`

Android-specific layer:

- Package/app ID: `com.freekalizer.tablet`
- Activity scaffold and Android resources
- Adapter layer that implements core `AudioBackend` contract
- Keeps platform lifecycle concerns out of portable core classes

## `core`

Portable Kotlin logic:

- `com.freekalizer.audio`
  - `AudioEngine` lifecycle/state model
  - `AudioBackend` abstraction for platform adapter implementations
  - `AudioProcessor` callback contract
- `com.freekalizer.timing`
  - Timing source abstraction (`Internal BPM` and future `Ableton Link`)
  - Registry for runtime source switching and fallback behavior
  - `TapBpmEstimator` (manual tap tempo) and `AutoBpmEstimator` (onset-based BPM + confidence, audio-thread-safe ingest; Android holds **two** instances for mic vs sampler when needed; bar-quantized clips use `LoopBpmMath` instead when playing)
  - Bar-loop tempo: `LoopBpmMath.bpmFromLoopFrames` in `com.freekalizer.sampler` (inverse of quantized REC `QuantizedLoopMath.targetFrames`, 4/4) × Sample Pitch for exact BPM + varispeed under Follow AUTO
- `com.freekalizer.effects`
  - Filter biquads, BPM→delay frames (`DelayBeatMath`), stereo feedback delay (`InterleavedFeedbackDelay`), BPM LFO rate (`FlangerBeatMath`), stereo flanger (`StereoFlanger`), EQ shelves/peaking (`ShelfPeakingBiquad`, `ThreeBandStereoEq`), scratch ring (`ScratchRingBuffer`) for E4
- `com.freekalizer.ui`
  - DFX69 tablet UI blueprint contract and manual-parity naming tokens

## Design Decisions

- API level target baseline: Android API 29+.
- Planned package/app ID for Android module scaffolding: `com.freekalizer.tablet`.
- UI parity terms are centralized in `ManualLabels` to prevent drift and duplicated strings.

## Next Implementation Steps

1. Replace OpenSLES/legacy path with **AAudio** (or Oboe) where device support allows, keeping the same `AudioProcessor` contract.
2. Effects engine (`E4`): filter / delay / scratch ring / flanger on the FX bus ahead of master pitch; monitor EQ on dry path before FX sum.
3. Broaden BPM auto-detection (e.g. half-time/double-time disambiguation) without adding audio-thread allocations.

The Android app already implements device enumeration, route rebind, sampler + pitch, tap/auto BPM UI, and file-backed sample library with rename/delete/favorite.
