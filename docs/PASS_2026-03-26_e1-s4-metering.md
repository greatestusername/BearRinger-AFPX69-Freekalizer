# Pass Plan: Finish E1 with Overload Metering (2026-03-26)

## Goals (this pass)

- Complete `E1-S4`: overload/clipping detection and visible UI metering.
- Keep implementation lightweight and low-allocation so it is safe for tablet live-audio use.
- Keep non-portable metering implementation in Android `app` layer; avoid duplicating logic across UI and backend.

## Implementation plan

1. Add an audio meter snapshot model in Android audio layer.
2. Update `AndroidAudioBackend` to:
   - compute input and output peak levels each processing burst
   - detect clipping when sample magnitude reaches configured threshold
   - expose a thread-safe meter snapshot API
3. Update `AndroidAudioEngineController` to expose current meter snapshot.
4. Update `MainActivity` to show:
   - input/output peak meters
   - input/output clipping status
   - periodic refresh while app is visible
5. Verify with `./gradlew :core:test :app:assembleDebug`.

## Non-goals (this pass)

- Fancy graphics or custom rendering for meters.
- DSP/effects/sampler changes.

# RULES FOR CURSOR / CLAUDE / LLM (copied from backlog)
- FOLLOW AUDIO DEVELOPER BEST PRACTICES FOR TABLETS DO NOT SLOP IT UP AND DON'T CREATE A BUNCH OF DUPLICATION!
- Do not make up or lie about solutions. If you are unsure ask or search the web for context
- make sure you create a README.md and Documentation for how to build/test/run the software. 
- When you do a set of work or issues/tickets make a document detailing that work and the date/time. MAKE SURE THAT DOC INCLUDES ALL OF THESE RULES
- Make sure you update the requirements documents and other docs as you go

