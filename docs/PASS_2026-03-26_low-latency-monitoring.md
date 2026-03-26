# Pass Plan: Low-Latency Monitoring Path (2026-03-26)

## What this pass is

Complete the next `E1` milestone by replacing the placeholder audio backend with a real Android monitoring loop:

- Microphone input (`AudioRecord`) -> Core `AudioProcessor` -> Speaker/headphones output (`AudioTrack`)
- Route/device selection changes trigger a deterministic rebind (stop/recreate record/track) without app restart

## Goals (this pass)

1. Implement a real `AndroidAudioBackend` in the Android `app` module that drives the core `AudioEngine` callback loop.
2. Make `E1-S1` acceptance criteria meaningful: actual monitoring audio path exists (not just lifecycle scaffolding).
3. Make device routing selection observable: when `E1-S2` selection changes (or falls back on removal), the backend rebinds using the new selections.

## Non-goals (deferred)

- Full sampler/effects/pitch system (`E2+`): this pass only ensures the live I/O foundation is real and stable.
- Ableton Link (`E9`): explicitly deferred by backlog.

## Implementation details (no duplication)

### Android audio backend

- Replace `AndroidAudioBackend` placeholder implementation.
- Keep core DSP contracts untouched; backend only adapts Android streaming into core `AudioProcessor.process(...)`.
- Avoid allocations on the audio thread:
  - allocate PCM buffers once during `start()`
  - reuse buffers on each callback/burst

### Channel semantics

- Clarify the meaning of `frameCount` and FloatArray layout:
  - `input` and `output` are interleaved per frame
  - `input` length is `frameCount * inputChannels`
  - `output` length is `frameCount * outputChannels`
- Update the monitoring `AudioProcessor` to copy/mix channels deterministically (mono in -> stereo out, etc).

### Device rebind trigger

- `AudioDeviceRepository` remains the single source of truth for selected device IDs.
- UI (`MainActivity`) informs the audio controller when input/output selection changes.
- The controller triggers a safe rebind:
  - `engine.stop()`
  - `engine.start(...)` using the updated selected device IDs

## Verification

- `./gradlew :core:test` remains green
- `./gradlew :app:assembleDebug` succeeds
- Manual verification (later, on a tablet):
  - start monitoring
  - confirm audible “mic -> output” loop
  - unplug/replug or change selected devices: loop rebinds without app restart

# RULES FOR CURSOR / CLAUDE / LLM (copied from backlog)
- FOLLOW AUDIO DEVELOPER BEST PRACTICES FOR TABLETS DO NOT SLOP IT UP AND DON'T CREATE A BUNCH OF DUPLICATION!
- Do not make up or lie about solutions. If you are unsure ask or search the web for context
- make sure you create a README.md and Documentation for how to build/test/run the software. 
- Make sure you update the requirements documents and other docs as you go

