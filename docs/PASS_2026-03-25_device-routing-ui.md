# Pass Plan: Device Routing + UI Scaffold (2026-03-25)

This pass implements the next highest-priority Android MVP work from `FREEKALIZER_ANDROID_PRODUCT_BACKLOG copy.md`, focusing on device routing visibility and correctness on tablets.

## Goals (this pass)

- Implement `E1-S2` input/output device enumeration and selection (Android-side).
- Implement `E1-S3` route change handling (plug/unplug/fallback) (Android-side).
- Add minimal UI scaffolding aligned to the DFX69 tablet blueprint to surface:
  - input device selection
  - output device selection
  - current route/state
  - microphone permission state
- Keep all portable logic in `core` and keep Android-specific concerns in `app`.

## Non-goals (deferred)

- Real low-latency audio I/O backend (AAudio/Oboe) and true monitoring path completion for `E1-S1` acceptance criteria.
- Effects/sampler/pitch UX (will land after the audio path is stable).

## Implementation plan

### 1) Device enumeration + selection (E1-S2)

- Add an `AudioDeviceRepository` (Android) that:
  - lists available input-capable and output-capable devices
  - provides stable IDs + user-readable labels
  - exposes selection state in one place (avoid duplicated selection logic across UI)

### 2) Route change handling (E1-S3)

- Register `AudioDeviceCallback` to detect add/remove changes.
- On device removal:
  - if selected device disappears, fall back deterministically to a best available default
  - surface a clear UI indication of what changed

### 3) UI scaffold (E7 alignment)

- Update `MainActivity` to show a simple “routing” panel consistent with the blueprint’s top rows:
  - “IN LEVEL / OVERLOAD” placeholders
  - device dropdowns
  - “REC / PLAY/STOP / SHOT / REV” placeholders (non-functional this pass)
- Add runtime mic permission request path (`RECORD_AUDIO`).

### 4) Verification

- Ensure `./gradlew :core:test` remains green.
- Ensure `./gradlew :app:assembleDebug` remains green.

## Expected backlog updates

- `E1-S2`: `in_progress` → `done` (if enumeration + selection UI shipped)
- `E1-S3`: `in_progress` or `done` depending on whether fallback behavior is implemented and observable
- Changelog entry added for this pass

---

# RULES FOR CURSOR / CLAUDE / LLM (copied from backlog)
- FOLLOW AUDIO DEVELOPER BEST PRACTICES FOR TABLETS DO NOT SLOP IT UP AND DON'T CREATE A BUNCH OF DUPLICATION!
- Do not make up or lie about solutions. If you are unsure ask or search the web for context
- make sure you create a README.md and Documentation for how to build/test/run the software. 
- Make sure you update this requirements document as you go

