# Freekalizer Android App - Living Requirements and Backlog

## Document Purpose

This is the primary, living product document for the Freekalizer tablet app effort. It captures:

- Original project context and goals
- Product requirements (Android-first, iOS-portable)
- Prioritized backlog (epics, stories, acceptance criteria)
- Ongoing decisions and change log

Update this file as scope, priorities, and technical decisions evolve.

---

## Original Context (Initial Prompt)

Project intent:

- Build an Android tablet app inspired by the Behringer Freekalizer DFX69. Follow all best practices for Audio Latency sensitive apps on tablets!
- App must accept input from Android audio input paths (mic/headset/line-in/USB where available).
- App must play output through Android output paths (speaker/headphone jack/USB output where available).
- Highest priority is the sampling workflow and LOW LATENCY FOR PERFORMANCE
- App must support saving and reloading samples.
- Pitch behavior requirements:
  - Both pitch knobs range from **-50% to +50%**.
  - Main device pitch changes **pitch only** (no tempo change).
  - Sample pitch changes **pitch and tempo** together.
  - Both knobs can affect sample when sample is routed through the effects section.
- Android is primary target; design should make iOS porting easier later.

Reference manual:

- `Behringer-Freekalizer-dfx69-manual.pdf`

---

## UI Design Reference (DFX69 Manual Imagery)

Purpose: define a hardware-faithful tablet UI blueprint derived from `Behringer-Freekalizer-dfx69-manual.pdf`, so implementation choices remain consistent across requirements and epics.

### Hardware-Parity Goals

- Preserve DFX69 control mental model: fast live access, clear section boundaries, and immediate visual state.
- Keep manual terminology as the default naming system in UI labels and docs.

### Naming and Display Parity

- Preferred labels: `IN LEVEL`, `OVERLOAD`, `DISPLAY`, `MIX`, `DRY`, `WET`, `PITCH`, `REC`, `PLAY/STOP`, `SHOT`, `REV`, `FILTER`, `FLANGER`, `DELAY`, `SCRATCH`.
- Filter wording must stay aligned to manual terminology: `LOW PASS (LP)`, `HIGH PASS (HP)`, `BAND PASS (BP)`, `LFO`, `MANUAL`, `AUTO`.
- Display abbreviations for temporary overlays should follow manual-style tokens where applicable: `DLy`, `FLg`, `FLt`, `PIt`, `SCr`, `SEn`.

### Tablet UI Zone Blueprint (Portrait mode always)
Silver and Black colorscheme (rgb leds) 

- Top zone row: Input, BPM, and clip/status indicators, MAIN/MASTER PITCH KNOB
- 2nd Top row: sampler transport, loop length selection, and sampler route/pitch controls.
- Middle zone row (above job wheel): 3 Band EQ + Kill Buttons
- Bottom zone (largest area): primary performance surface (large scratch/data wheel + active effect controls).
- Ensure one-touch access to all performative actions from UI surface (NO DEEP MENUS except for sample loading/saving/etc)

### Interaction and State Expectations

- Press-and-hold interactions must be low-latency and visually obvious (`SHOT`, momentary effect gestures).
- Display should prioritize:
  - active effect and current editable parameter,
  - BPM value and confidence state,
  - overload clipping feedback,
  - recording progress and remaining duration.
- Routing states must be explicit (`FX route` on/off, dry/wet ownership, effect target context).

### Design Constraints (MVP vs Later)

- MVP: implement sectioned layout, large performance controls, clear state indicators, and manual-parity labels.
- Post-MVP: advanced visual polish, optional stage modes, and richer micro-animations.
- Do not change core audio interaction semantics to satisfy visual style preferences.

### Traceability to Requirements and Backlog

- Functional alignment:
  - `FR-2` (dry/wet behavior),
  - `FR-3` (sampler transport, bar lengths, route, pitch semantics),
  - `FR-4` (effects controls and EQ kill),
  - `FR-5` (BPM tap/auto/status).
- Epic alignment:
  - `E2` (Sampler Core),
  - `E4` (Effects Engine),
  - `E5` (BPM and Timing),
  - `E7` (UX and Performance Mode).

---

## Product Vision

Deliver a performance-ready Android app that reproduces the DFX69-style real-time effects and sampler workflow, with mobile-native sample management and a code architecture that can later be ported to iOS.

---

## Scope and Priorities

1. Reliable low-latency Android audio I/O
2. Sampling workflow parity and usability
3. Core effects and BPM workflow parity
4. Sample save/load library
5. iOS-portable architecture decisions

---

## Functional Requirements

### FR-1 Audio Input/Output and Routing

- Select audio input and output devices on Android.
- Route audio in real time: input -> processing -> output.
- Support at least: built-in mic, headset mic, speaker/headphones.
- Support USB audio interfaces when exposed by Android.
- Support for Ableton LINK (https://ableton.github.io/link/) would be amazing
- Include input gain control and overload/clipping indicator.

### FR-2 Dry/Wet and Main Signal Control

- Provide DRY/WET mix behavior equivalent to DFX workflow.
- Left side = dry signal only; right side = effect signal only.
- Allows mix adjustment for selected/active effect, including Scratch mode. Each sample does not have its own dry/wet.

### FR-3 Sampler (Highest Priority)

#### FR-3.1 Recording

- Record input signal to sampler.
- Bar-quantized lengths: 1, 2, 4, 8, 16 bars (BPM-based).
- Custom/free recording mode with explicit max duration settable by user in seconds or bars (relative to BPM).
- Show recording progress and remaining time.

#### FR-3.2 Playback

- `PLAY/STOP`: continuous loop playback.
- `SHOT`: momentary playback while pressed/held.
- `REV`: reverse playback toggle.
- `FX route`: choose whether sample goes through effects path and Main/Master Pitch.

#### FR-3.3 Pitch Semantics (Project-Defined)

- Main (master) Pitch knob: `-50% .. +50%`, pitch-only (tempo unchanged).
- Sample Pitch knob: `-50% .. +50%`, pitch+tempo linked (resample behavior).
- If sample is routed through effects, both knobs can affect sample as designed.

### FR-4 Effects Section

- Filter with LP/HP/BP types.
- Filter modes: LFO, Manual, Auto. (manual has a bigger knob for cutoff)
  - LFO controllable/syncable to BPM-related timing values bar/beat divisions
  - Knob for LFO Depth on the cutoff modulation
- Filter Resonance control knob
- Flanger with BPM-relative timing and manual modulation.
- Delay/echo with BPM-related timing values. Echo continue when effect is turned off (for momentary triggering). settable bar/beat divisions for delay/echo time
  - NEW: Feedback setting knob for Delay/Echo in sub menu
- Scratch effect via large touch wheel gesture.
- 3-band EQ + kill style behavior buttons (LOW/MID/HIGH); band faders **−50 dB .. +24 dB** (kill uses fader minimum).

### FR-5 BPM Counter

- Auto BPM detection mode (onset-based estimator in `core` with confidence; optional **Follow AUTO BPM** applies to internal clock when confidence is high).
- Tap BPM manual mode.
- Show BPM confidence/failure state when beat cannot be detected (live % + lock hint in UI).

### FR-6 Sample Save/Load Library

- Save sample audio + metadata to local storage.
- Reload saved samples into active sampler.
- Metadata: name, date, BPM at record time, duration, reverse state, pitch values.
- Manage samples: rename/delete/favorite/load wav/mp3.
- Persist library across app restarts.

---

## Non-Functional Requirements

### NFR-1 Latency and Responsiveness (VERY VERY VERY IMPORTANT)

- Deliver live-playable audio latency on supported Android devices.
- Provide compatibility mode for devices that cannot meet low-latency path.

### NFR-2 Stability

- No crashes on route changes, app pause/resume, or screen rotation.
- Graceful handling of unplug/replug and permission changes.

### NFR-3 Performance

- No audible dropouts under normal live use on target tablet hardware. (VERY VERY VERY IMPORTANT)
- CPU/memory usage within sustainable limits during effect + sampler usage.

### NFR-4 Portability

- Keep DSP/sampler logic in portable core layer where possible.
- Keep platform-specific code (audio session, device routing, permissions, storage UI integration) isolated.

---

## Ableton Link Feasibility and Effort (Added 2026-03-25)

### Why Link Matters

Ableton Link synchronizes tempo, beat, phase, and (v3) start/stop intent across apps/devices on a local network, which fits this app's BPM- and loop-centric workflow.

### Integration Notes (from docs)

- Link is cross-platform C++ source and header-driven integration model.
- iOS should use LinkKit; Android can use the Link C++ library through native integration.
- Best practice is to read/commit Link session state on the audio thread for timing accuracy.
- Proper output latency compensation is required for stable "in-time" behavior across peers.
- Quantized launch behavior is strongly recommended for phase/transport UX.

### Estimated Effort (Android)

Assumptions:

- Existing Android audio engine is already working with stable low-latency path.
- Team has at least one engineer comfortable with Android NDK/C++ integration.

Estimated engineering effort:

- **Minimal Link Sync (tempo + beat sync, no transport):** ~1 to 2 weeks
- **Performance-grade Link (tempo/beat/phase + quantized launch + UX + robust testing):** ~3 to 5 weeks
- **With start/stop sync + broad device/network hardening for release confidence:** ~5 to 7 weeks

Risk factors that can increase effort:

- Threading mistakes between UI thread and audio thread state commits
- Android network variability (Wi-Fi quality, multicast discovery edge cases)
- Latency compensation tuning differences across tablet hardware
- Interaction complexity between internal BPM counter and externally synced Link timeline

Recommendation:

- Ship Android MVP without Link in phase 1.
- Build Link as **Phase 2 (high-value enhancement)** after core sampler and low-latency stability are proven.

---

## Backlog Structure

Status values:

- `todo`
- `in_progress`
- `done`
- `blocked`

Priority values:

- `P0` critical
- `P1` high
- `P2` medium
- `P3` low

---

## Epic Backlog

### E1 - Audio Engine and Device Routing (P0)

Goal: stable real-time input/output path on Android tablets.

Stories:

- `E1-S1` (P0, done) Build base audio engine initialization and teardown. (Core lifecycle + tests in `core`; Android monitoring backend now drives `AudioEngine` via real `AudioRecord`/`AudioTrack` loop with PCM_16 conversion and no-audio-thread allocations.)
- `E1-S2` (P0, done) Implement input/output device enumeration and selection. (Android `AudioDeviceRepository` + UI selection spinners.)
- `E1-S3` (P0, done) Implement audio route change handling (plug/unplug/fallback). (Device callback + fallback in `AudioDeviceRepository`; UI-driven safe rebind stop/start in controller; preferred device routing attempted via `setPreferredDevice` when supported.)
- `E1-S4` (P1, done) Add overload/clipping detection and UI meter. (Backend peak + clip hold metering, controller snapshot API, and UI display for IN/OUT level and OVERLOAD status.)

Acceptance criteria:

- Input signal can be monitored through selected output with no app restart.
- Device route changes recover automatically or present a clear user prompt.
- Audio stream continues after app background -> foreground transition.

---

### E2 - Sampler Core (P0)

Goal: production-grade sampler workflow matching core DFX behavior.

Stories:

- `E2-S1` (P0, done) Record quantized loop lengths (1/2/4/8/16 bars). (Portable core quantized recorder + tests implemented; Android `REC` bar-length selector and quantized recording trigger/progress wiring added.)
- `E2-S2` (P0, done) Implement free-length recording mode with cap. (`FrameCappedSamplerRecorder` + `FreeRecordMath` in `core`; Android capped free `REC` with selectable max seconds and “end free REC now”; mutual exclusion with quantized `REC`.)
- `E2-S3` (P0, done) Implement loop playback (`PLAY/STOP`). (`SamplerLoopPlayer` mixes loaded `SamplerBuffer` into the monitoring output; `PLAY/STOP` toggles continuous loop; tests in `core`.)
- `E2-S4` (P0, done) Implement momentary playback (`SHOT`). (Separate shot read pointer + gate in `SamplerLoopPlayer.mixShotInto`; each press rewinds to sample start; loops while held; wired through FX-route and direct paths; `SHOT (hold)` button with touch + `requestDisallowInterceptTouchEvent` so `ScrollView` does not steal the gesture; theme fix `Widget.Freekalizer.SectionTitle` parent to `@android:style/Widget.TextView` to avoid inflation crashes on some builds.)
- `E2-S5` (P1, done) Implement reverse playback (`REV`). (`SamplerLoopPlayer` backward loop/SHOT with wrap; `reverseRequested` survives new captures; Android `ToggleButton` + status line; unit test for backward order.)
- `E2-S6` (P0, done) Implement sampler FX routing toggle. (`SamplerFxRouteIntent` in `core`; Android audio path uses dedicated `fxBusScratch` when routed through effects bus—identity today, ready for E3 main pitch + E4 DSP; `ToggleButton` + status in UI; `ManualLabels.FX_ROUTE` in blueprint.)
- `E2-S7` (P1, done) Display sampler progress, mode, and remaining time. (`SamplerRecordingState` extended with sample rate, last quantized bars, BPM, loop/SHOT playheads; `SamplerLoopPlayer` publishes volatile playheads from the audio thread; sampler zone horizontal `ProgressBar` + `DISPLAY` status: mode line, REC elapsed/remaining as m:ss, loop/SHOT playhead, quantized BPM hint.)

Acceptance criteria:

- User can record from live input and immediately loop playback.
- `SHOT` plays only while pressed and stops on release.
- Reverse mode plays backward without crash or desync.

---

### E3 - Pitch System (P0)

Goal: implement required dual-pitch semantics.

Stories:

- `E3-S1` (P0, done) Implement Main Pitch knob (`-50%..+50%`, pitch-only). (Portable `StreamingCheapPitchShifterMono` **phase-vocoder** STFT 512 / hop 128 + Hann OLA in `core`; stereo pair on FX bus after sampler mix; bypass near ratio 1.0; Android `SeekBar` + live label.)
- `E3-S2` (P0, done) Implement Sample Pitch knob (`-50%..+50%`, pitch+tempo). (`PitchKnobMath` 0.5×..1.5× speed; `SamplerLoopPlayer` fractional phase + linear interpolation; no allocations in mix path.)
- `E3-S3` (P0, done) Apply both pitch paths to sample in FX-routed mode. (FX route: sample pitch in `SamplerLoopPlayer` then main pitch on `fxBusScratch`; direct route: sample pitch only — master pitch N/A per backlog intent.)
- `E3-S4` (P1, done) Add precision display and reset-to-zero actions. (SeekBar maps 0..100 → −50..+50 with signed percent in `DISPLAY`/sampler status + dedicated value lines; **Reset to 0%** buttons.)

Acceptance criteria:

- Main Pitch changes perceived pitch with tempo held.
- Sample Pitch changes both pitch and playback speed.
- Knob ranges are clamped to exactly `-50%..+50%`.

---

### E4 - Effects Engine (P1)

Goal: deliver core DFX-style effects for live performance.

Stories:

- `E4-S1` (P1, done) Implement Filter (LP/HP/BP). (`BiquadMonoFilter` in `core`; Android applies it to `fxBusScratch` before master pitch shifter; UI LP/HP/BP buttons in EQ zone.)
- `E4-S2` (P1, done) Implement Filter modes (LFO/Manual/Auto) + resonance. (Adds `FilterMode` in `core`; Android maps cutoff on a log knob, resonance→Q, implements LFO (BPM-related beat period + depth) and AUTO (input-envelope follower + depth); updates biquad coefficients on the audio callback without per-callback allocations; UI adds mode buttons + cutoff/resonance + LFO rate/depth + AUTO depth.)
- `E4-S3` (P1, done) Implement Delay with BPM-based timing. (`DelayBeatMath` + `InterleavedFeedbackDelay` in `core`; FX-bus echo after filter before master pitch; beat divisions 1/4–4 beats from internal BPM; **send OFF** stops new input while feedback tail decays; UI: send toggle + beats + feedback + wet; tests for beat→frames + line.)
- `E4-S4` (P1, done) Implement Flanger with timing/modulation controls. (`StereoFlanger` variable-delay feed-forward comb in `core`; `FlangerBeatMath` LFO Hz from beats/cycle vs internal BPM; FX order filter→delay→**flanger**→master pitch; **OFF** bypasses comb while ring/LFO advance; UI: ON toggle + LFO period + base/sweep/manual/wet sliders; tests for LFO math + bypass + engaged delta energy.)
- `E4-S5` (P2, done) Implement Scratch mode gesture + rolling buffer. (`ScratchRingBuffer` in `core`; post-delay tap on FX bus; fractional read lag; vertical drag on performance surface; `ScratchRingBufferTest`.)
- `E4-S6` (P2, done) Implement 3-band EQ + kill behavior. (`ShelfPeakingBiquad` + `ThreeBandStereoEq` — low shelf / mid peaking / high shelf; post-sum output path; tap-to-cut LOW/MID/HIGH/KILL to fader minimum; band SeekBars **−50 .. +24 dB**; `ThreeBandStereoEqTest`.)

Acceptance criteria:

- Effects can be toggled live without dropouts.
- Timing-based effects can lock to BPM (auto or tapped).
- Filter Auto mode reacts predictably to input level range.

---

### E5 - BPM and Timing System (P1)

Goal: make beat-synced functions reliable.

Stories:

- `E5-S1` (P1, done) Implement auto BPM detection. (`AutoBpmEstimator` in `core` — energy-flux onsets, median IOI → BPM 40–280, no allocations in ingest path; Android feeds live interleaved input each callback; optional **Follow AUTO BPM** checkbox applies estimate when confidence ≥ threshold; `AutoBpmEstimatorTest` synthetic 120 BPM.)
- `E5-S2` (P1, done) Implement tap BPM averaging. (`TapBpmEstimator` in `core` — mean of recent inter-tap intervals, stale-gap reset, clamp 40–280; `InternalBpmTimingSource` + Android **TAP BPM** / **BPM → 120** buttons; live BPM line in top zone.)
- `E5-S3` (P2, done) Implement BPM confidence indicator. (Reading bundled with auto estimator; UI shows confidence % and strong/weak/none lock hint alongside master BPM and AUTO estimate.)
- `E5-S4` (P1, done) Drive quantized sampler/effects timing from BPM engine. (**Quantized REC** + bar-length frame targets now use `InternalBpmTimingSource.currentBpm()`; effects timing still TBD in E4.)
- `E5-S5` (P1, done) Add timing source abstraction (`Internal BPM` vs `Ableton Link`) to avoid tightly coupling BPM consumers to one clock.

Acceptance criteria:

- BPM value updates during playback and remains stable on steady material.
- Tap BPM can override auto BPM when selected.
- App can swap timing source without breaking sampler quantization logic.

---

### E6 - Sample Library and Persistence (P0)

Goal: app-native save/load and reusable sample sets.

Stories:

- `E6-S1` (P0, done) Save sample audio and metadata. (Portable `SavedSampleMetadata` + `WavPcmIo` PCM16 WAV in `core`; app `SampleLibraryStore` writes `{uuid}.wav` + `{uuid}.meta.json` under `filesDir/sample_library/`; UI **SAVE to library** with optional name; metadata includes BPM-at-capture when available, duration, reverse, both pitch percents, optional quantized bar count.)
- `E6-S2` (P0, done) Load sample into active sampler state. (**LOAD from library** spinner; WAV decode + resample to engine rate if needed; restores `loadPresetSample`, reverse, pitch SeekBars, stops loop; `bpmAtLastCapture` from file for DISPLAY context.)
- `E6-S3` (P1, done) Rename/delete/favorite items. (`SavedSampleMetadata.favorite` + JSON key; `SampleLibraryStore.renameClip` / `deleteClip` / `setFavorite`; spinner shows ★ prefix and favorites-first sort; **Rename** / **Delete** / favorite `ToggleButton`; clearing last-session id when deleted clip was restored target.)
- `E6-S4` (P1, done) Persist latest session and restore on launch. (SharedPreferences `last_library_sample_id`; cold start loads that clip + metadata before falling back to preset drumloop; clears key if files missing.)

Acceptance criteria:

- Saved samples survive app restarts and device reboot.
- Loading sample restores audible result and core metadata.

---

### E7 - UX and Performance Mode (P1)

Goal: touch-first live performance interface.

Stories:

- `E7-S1` (P1, partially-done) Build tablet layout with dedicated sections. (Blueprint + `ManualLabels` in `core`; Android portrait board + modal submenus in `activity_main.xml` with DFX-style dark/silver/cyan styling, view binding, meters + routing + sampler controls grouped; **MENU FX** / other dense panels scroll inside the modal so all controls stay reachable; main performance surface stays non-scrolling.)
- `E7-S2` (P1, done) Implement large controls and press/hold interactions. (Main board now uses larger touch targets for performative controls and explicit press/hold feedback: `SHOT` and EQ kill buttons are highlighted live while held; scratch surface remains dominant and hold-safe.)
- `E7-S3` (P1, todo) Verify Layout UI
- `E7-S4` (P1, done) Add clear state indicators (recording, clipping, bpm lock, fx route). (Dedicated board LED row added for `REC`, `CLIP`, `BPM LOCK`, `FX ROUTE`, refreshed on the existing 100 ms UI ticker from live engine state.)
- `E7-S5` (P2, todo) Add optional dark stage mode and high-contrast labels.

Acceptance criteria:

- Core live controls are accessible with one hand.
- State changes are visible in less than 100 ms perceived UI delay.

---

### E8 - Quality, Test, Release Readiness (P1)

Goal: make Android MVP stable and shippable.

Stories:

- `E8-S1` (P1, done) Define supported Android versions/devices matrix. (Added `docs/DEVICE_MATRIX.md` with API bands, device classes, required validation passes, matrix rows M1-M5, and MVP exit criteria.)
- `E8-S2` (P1, done) Add automated tests for sampler/pitch edge cases. (Expanded `SamplerLoopPlayerTest` and `PitchKnobMathTest` with clamp/mapping/reverse-shot/clear-state edge coverage.)
- `E8-S3` (P1, done) Add long-run soak test for audio dropout/crash. (Added deterministic `SamplerLoopPlayerSoakTest` long-run stress pass with repeated reverse/SHOT/pitch transitions and finite-output assertions.)
- `E8-S4` (P2, todo) Build crash logging + performance telemetry.
- `E8-S5` (P1, done) Add latency benchmark harness (input-to-output and callback jitter) for target Android tablets. (Added low-overhead backend callback/jitter tracker + controller snapshot API and benchmarking doc `docs/LATENCY_BENCHMARK.md`.)

Acceptance criteria:

- 10-minute and 30-minute audio sessions complete without crash on target devices.
- No known P0 defects remain open for MVP.
- Latency metrics are captured and repeatable on target device matrix.

---

### E9 - Ableton Link Integration (Phase 2, P1)

Goal: synchronize app tempo/beat/phase with other Link-enabled apps/devices on local network while preserving low-latency audio performance.

Stories:

- `E9-S1` (P1, todo) Validate licensing/compliance path for Ableton Link usage in this project.
- `E9-S2` (P1, todo) Integrate Link C++ code into Android native build (NDK/CMake) and app packaging.
- `E9-S3` (P1, todo) Implement Link session enable/disable, peer count indicator, and connection status.
- `E9-S4` (P1, todo) Implement tempo + beat sync with audio-thread capture/commit model.
- `E9-S5` (P1, todo) Implement phase/quantum model tied to sampler bar-length and launch behavior.
- `E9-S6` (P1, todo) Add quantized start UX (count-in / pending launch indicator).
- `E9-S7` (P2, todo) Implement Link v3 start/stop sync as optional mode.
- `E9-S8` (P1, todo) Implement conflict policy between internal BPM tap/auto and external Link tempo proposals.
- `E9-S9` (P1, todo) Add latency compensation and timing calibration for Link-aligned playback.
- `E9-S10` (P1, todo) Add multi-device interoperability tests (at least two Link peers, mixed app scenarios).
Acceptance criteria:

- With Link enabled, app tempo converges with external Link peers and remains stable.
- Quantized loop/sampler launches remain phase-aligned at selected quantum.
- User can switch Link on/off without audio engine restart or crash.
- Internal BPM mode and Link mode transitions are deterministic and clearly indicated in UI.
- Interop test pass on at least 2 Android devices and 1 external Link-capable app.

---

## MVP Cut (Android)

Include:

- E1 core stories (`S1-S3`)
- E2 core stories (`S1-S4`, `S6`)
- E3 all core stories (`S1-S3`)
- E5 core stories (`S1`, `S2`, `S4`)
- E6 core stories (`S1`, `S2`)
- E7 core stories (`S1-S3`)
- E8 core stories (`S1`, `S2`, `S3`, `S5`)

Defer:

- Scratch advanced behavior
- Full EQ kill refinements
- Extended telemetry and polish features
- E9 Ableton Link integration (Phase 2)

---

## Open Decisions

- Exact max sample duration for free mode and storage constraints
- Number of simultaneous sample slots in MVP (single active slot vs multiple)
- DSP implementation for pitch-only vs pitch+tempo quality/performance tradeoff (**current:** sample pitch = varispeed; main pitch = 512-point **phase-vocoder** STFT — further upgrade: Rubber Band / higher FFT / native)
- First supported tablet models for initial device matrix (API baseline set to 29+)
- iOS portability stack choice for shared DSP core
- Ableton Link licensing path and distribution implications for planned app license
- Link quantum strategy: fixed value vs mapped to loop/bar length
- Transport policy in Link mode: local-only start/stop vs Link v3 shared start/stop

---

## Change Log

- `2026-03-25` - Initial living requirements + backlog document created from DFX69 context and project goals.
- `2026-03-25` - Added Ableton Link feasibility assessment, effort estimates, Phase 2 Link epic (E9), timing abstraction ticket updates, and latency benchmark ticket.
- `2026-03-25` - Started implementation: added Kotlin `core` module, implemented `E1-S1` foundation (audio engine lifecycle state machine + config validation + tests), and completed `E5-S5` timing source abstraction with internal BPM default and Link-ready registry.
- `2026-03-25` - Added UI design requirements section; implementation aligned by adding shared DFX69 UI blueprint contract/labels in `core` (`E7-S1` moved to `in_progress`), added project `README.md`, and added setup/build/test/run architecture documentation.
- `2026-03-25` - Decision captured: minimum Android API baseline is `29+`; planned Android package/app ID is `com.freekalizer.tablet`.
- `2026-03-25` - Added Android `app` module scaffold (`com.freekalizer.tablet`, minSdk 29), added Android audio backend/controller adapter layer on top of `core` engine contracts, added Gradle wrapper for reproducible builds, and verified `:core:test` passes via `./gradlew`.
- `2026-03-25` - Fixed Android build config by enabling AndroidX in `gradle.properties` and cleaning `local.properties`; verified `./gradlew :app:assembleDebug` succeeds.
- `2026-03-25` - Implemented Android device enumeration/selection UI and route-change callback handling (`E1-S2` done, `E1-S3` in progress), added mic permission plumbing, and verified `./gradlew :app:assembleDebug` succeeds with these changes.
- `2026-03-26` - Replaced Android audio backend placeholder with a real `AudioRecord`/`AudioTrack` monitoring loop (PCM_16 with float conversion into core), wired device selection into safe stop/start rebind for route handling, and verified `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Completed `E1-S4` by adding backend peak/clipping detection with clip hold timing, exposing meter snapshots via controller, adding IN/OUT meter + OVERLOAD indicators in UI, and verifying `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Started `E2-S1` by adding portable quantized sampler recording core (`QuantizedBars`, frame-target math, and `QuantizedSamplerRecorder`) with tests for 1/2/4/8/16-bar behavior; verified `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Completed `E2-S1` by wiring Android `REC` quantized recording controls (bar selector + trigger + progress/last-capture status) to core sampler recording primitives; verified `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Completed `E2-S2` and `E2-S3`: portable `SamplerBuffer`, `FrameCappedSamplerRecorder`, `SamplerLoopPlayer`, and `FreeRecordMath` with unit tests; Android engine mixes loop playback post-monitor with output clamping; UI adds free capped `REC`, early stop, and `PLAY/STOP` loop toggle; verified `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Completed `E2-S4` `SHOT` momentary playback + inflation hardening (`SectionTitle` style parent); verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Completed `E7-S1` Android tablet section layout: `activity_main.xml` + theme/colors + view binding for visible four-zone UI on dark background; verified `./gradlew :app:assembleDebug`.
- `2026-03-26` - Completed `E2-S6` sampler FX route: core `SamplerFxRouteIntent`, effects-bus scratch buffer in the monitoring callback, UI toggle and blueprint label `FX ROUTE`; verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Fixed startup crash from device spinner feedback loop (`updateDeviceUi` → `onItemSelected` → `emit` → repeat): suppress spinner callbacks while rebinding, restore selection to match `AudioDeviceRepository`, emit only on real selection changes, idempotent `AudioDeviceRepository.start` / `emit` on device plug-in; completed `E2-S5` `REV` reverse loop/SHOT; README note on installing emulator system images; verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Hardening for API 34 / emulator: detach input/output spinner listeners while swapping adapters; defer `syncEngineControllerToSelection` to a posted runnable (coalesced) so route/rebind is not synchronous with stream setup; non-reentrant `rebindIfRunning` guard in `AndroidAudioEngineController`.
- `2026-03-26` - **Root startup crash fix:** `AndroidAudioEngineController` must not be constructed as an Activity field (runs before `onCreate`); `Activity.getSystemService` throws `IllegalStateException` — instantiate after `super.onCreate()` in `MainActivity`. Verified with `adb install` + `am start` on API 34 emulator (process stays alive).
- `2026-03-26` - Completed `E2-S7`: sampler `DISPLAY` status (mode, REC progress % and m:ss elapsed/total/remaining, loaded sample duration and playhead during loop/SHOT), horizontal progress bar for record and playback heads, core `SamplerLoopPlayer` volatile playhead fields for UI polling; `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Completed `E3-S1`–`E3-S4`: dual pitch system (`PitchKnobMath`, `StreamingCheapPitchShifterMono`, fractional-phase `SamplerLoopPlayer`), FX-bus main pitch + direct/sample-only path, tablet SeekBars and reset + status text; `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - Upgraded main pitch to **phase-vocoder** STFT (512 / 128 hop); added `PcmResampler` + `WavPcmDecoder` + bundled **`drumloop.wav`** in `assets` (120 BPM, 1 bar) loaded on `MainActivity` startup via `PresetDrumloopLoader` / `loadPresetSample` for instant sampler testing.
- `2026-03-26` - **E5-S2** tap BPM + **E5-S4** wiring: `TapBpmEstimator`, `InternalBpmTimingSource` drives quantized `REC` targets and UI BPM display; `./gradlew :core:test` + `:app:assembleDebug` succeed.
- `2026-03-26` - **E6-S1** / **E6-S2**: sample library save/load — `WavPcmIo` encode/decode in `core` (replaces app-only decoder), `SavedSampleMetadata`, `QuantizedBars.fromBarCount`, `bpmAtLastCapture` on engine for capture-time BPM; Android `SampleLibraryStore` + sampler zone UI; verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - **E6-S4**: last library sample id in SharedPreferences; restore on launch or preset fallback.
- `2026-03-26` - **E5-S1** / **E5-S3**: `AutoBpmEstimator` + confidence reading in `core` (tests); Android **Follow AUTO BPM** + richer BPM status line; **E6-S3** library rename/delete/favorite + metadata persistence; `./gradlew :core:test` + `:app:assembleDebug` verified.
- `2026-03-26` - Completed `E4-S1` filter (LP/HP/BP) on FX bus: portable `BiquadMonoFilter` in `core`, applied in monitoring callback before master pitch, plus EQ zone UI buttons; verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Completed `E4-S2` filter modes + resonance: Manual/LFO/Auto mode controls and resonance + modulation depth controls; BPM-related LFO period; AUTO envelope follower from live input; verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Completed `E4-S3` BPM-synced delay / echo on FX bus with tail-only mode when send is off; core tests + `./gradlew :core:test` + `:app:assembleDebug` verified.
- `2026-03-26` - Completed `E4-S4` BPM-LFO flanger on FX bus (`StereoFlanger` + UI); verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Completed `E4-S5` scratch ring (post-delay FX tap, touch surface) and `E4-S6` three-band monitor EQ + kill + gain sliders (`ThreeBandStereoEq`, `ShelfPeakingBiquad`, `ScratchRingBuffer`); verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-26` - Implemented non-scrolling ASCII-style board layout pass in `activity_main.xml`: fixed portrait hardware surface with persistent performative controls, dedicated empty-area submenu buttons (`MENU AUDIO/BPM/SAMPLER/FX/LIBRARY/SYSTEM`), and one-level submenu panels for non-ASCII controls while preserving `MainActivity` bindings; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - Completed `E7-S2` + `E7-S4`: added explicit main-board live state LEDs (`REC`, `CLIP`, `BPM LOCK`, `FX ROUTE`) driven by engine state/meter confidence at 100 ms update cadence; added clearer hold interaction feedback for `SHOT` and EQ kill buttons (live pressed coloring) with no audio-path behavior changes; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - Scratch feel tuning pass (`E4-S5` quality): replaced abrupt lag jumps with smoothed lag-target slew while touch is active, reduced gesture sensitivity, constrained scratch lag window to a short vinyl-like range, and added deadzone jitter rejection to reduce zipper/glitch artifacts; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - Vinyl scratch + filter safety pass: scratch now uses signed platter-speed control (supports true backward scrub while touching), adds `Smooth / Classic / Cut` scratch-feel presets in `MENU FX`, and tightens high-cutoff filter behavior by capping max cutoff below Nyquist and damping effective resonance near top-end to prevent harsh clipping spikes; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - Completed `E8-S1` / `E8-S2` / `E8-S3` / `E8-S5`: added device matrix doc (`docs/DEVICE_MATRIX.md`), expanded sampler/pitch edge tests, added long-run sampler soak test, and integrated callback/jitter benchmark harness (`AudioCallbackJitterTracker`, `AudioLatencySnapshot`, backend/controller snapshot APIs) with usage guide (`docs/LATENCY_BENCHMARK.md`); verified `./gradlew :core:test` + `:app:assembleDebug`.
- `2026-03-27` - Live-monitor safety + EQ kill reliability pass: input monitor is auto-muted while sample loop/SHOT is active to prevent mic->speaker feedback during performance playback, added input monitor gain control in `MENU AUDIO`, and hardened EQ kill touch handling (move/outside/cancel release) to prevent stuck kill states requiring app restart; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - EQ/input workflow correction pass: moved input gain control to the front panel top strip (`IN GAIN` above former MIX slot), expanded EQ gain range to `±24 dB`, moved EQ processing to post-sum output path so bands/kills affect audible output consistently, and added ticker-level safety release for kill holds if touch-up/cancel is missed; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - Follow-up control regression fix: restored visible front-panel `MIX` control while keeping `IN GAIN` on the top strip, strengthened kill-state synchronization by mirroring actual button pressed state each UI tick, and widened scratch lag/rate behavior to keep continuous bidirectional grab without re-pressing; verified `./gradlew :app:assembleDebug`.
- `2026-03-27` - Performance control polish pass: reduced master-pitch artifacts by moving to a larger STFT shifter window with overlap normalization, remapped scratch pad to explicit axes (`Y` playback position, `X` volume) with on-pad axis labels and gradient cue, switched EQ kill buttons to stable tap-to-cut behavior by snapping bands to the fader minimum, widened EQ fader span toward live-mix needs (see current `ThreeBandStereoEq.MIN_DB` / `BOOST_MAX_DB`), lowered AUTO BPM follow confidence threshold for easier lock-in, added double-tap reset-to-default across primary sliders, and set delay feedback default to `60%`; verification pending local device pass.
- `2026-03-27` - FX routing order update: scratch is now placed before delay/flanger by default so scratch gestures are processed through downstream FX; filter stays after the scratch/delay/flanger block, and `MENU FX` **FX processing order** lists four chains: scratch-first vs Dly/Flg-first **×** Tape/Wow-Flutter before or after Hall reverb. Submenu body shows a persistent vertical scrollbar track + thumb and a short “scroll the panel” hint.
- `2026-03-27` - EQ fader range set to **−50 .. +24 dB** (`ThreeBandStereoEq.MIN_DB`); kill buttons still snap bands to the fader minimum. **MENU FX** submenu body wrapped in a weighted `ScrollView` so long panels (filter/delay/flanger/reverb/tape/scratch preset/**FX processing order**) are not clipped on tablet portrait.

# RULES FOR CURSOR / CLAUDE / LLM
- FOLLOW AUDIO DEVELOPER BEST PRACTICES FOR TABLETS DO NOT SLOP IT UP AND DON'T CREATE A BUNCH OF DUPLICATION!
- Do not make up or lie about solutions. If you are unsure ask or search the web for context
- make sure you create a README.md and Documentation for how to build/test/run the software. 
- Make sure you update the requirements documents and other docs as you go