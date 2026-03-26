# Architecture Notes

## Goals

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
- `com.freekalizer.ui`
  - DFX69 tablet UI blueprint contract and manual-parity naming tokens

## Design Decisions

- API level target baseline: Android API 29+.
- Planned package/app ID for Android module scaffolding: `com.freekalizer.tablet`.
- UI parity terms are centralized in `ManualLabels` to prevent drift and duplicated strings.

## Next Implementation Steps

1. Replace placeholder Android backend with real low-latency callback path (AAudio/Oboe).
2. Implement input/output route enumeration and route change handling (`E1-S2`, `E1-S3`).
3. Add sampler core primitives in `core` and keep Android module focused on I/O and UI.
