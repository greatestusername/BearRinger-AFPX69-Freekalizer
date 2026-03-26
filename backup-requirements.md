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
- Allow mix adjustment for selected/active effect, including Scratch mode.

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
- Scratch effect via large touch wheel gesture.
- 3-band EQ + kill style behavior buttons (LOW/MID/HIGH).

### FR-5 BPM Counter

- Auto BPM detection mode.
- Tap BPM manual mode.
- Show BPM confidence/failure state when beat cannot be detected.

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

- `E1-S1` (P0, todo) Build base audio engine initialization and teardown.
- `E1-S2` (P0, todo) Implement input/output device enumeration and selection.
- `E1-S3` (P0, todo) Implement audio route change handling (plug/unplug/fallback).
- `E1-S4` (P1, todo) Add overload/clipping detection and UI meter.

Acceptance criteria:

- Input signal can be monitored through selected output with no app restart.
- Device route changes recover automatically or present a clear user prompt.
- Audio stream continues after app background -> foreground transition.

---

### E2 - Sampler Core (P0)

Goal: production-grade sampler workflow matching core DFX behavior.

Stories:

- `E2-S1` (P0, todo) Record quantized loop lengths (1/2/4/8/16 bars).
- `E2-S2` (P0, todo) Implement free-length recording mode with cap.
- `E2-S3` (P0, todo) Implement loop playback (`PLAY/STOP`).
- `E2-S4` (P0, todo) Implement momentary playback (`SHOT`).
- `E2-S5` (P1, todo) Implement reverse playback (`REV`).
- `E2-S6` (P0, todo) Implement sampler FX routing toggle.
- `E2-S7` (P1, todo) Display sampler progress, mode, and remaining time.

Acceptance criteria:

- User can record from live input and immediately loop playback.
- `SHOT` plays only while pressed and stops on release.
- Reverse mode plays backward without crash or desync.

---

### E3 - Pitch System (P0)

Goal: implement required dual-pitch semantics.

Stories:

- `E3-S1` (P0, todo) Implement Main Pitch knob (`-50%..+50%`, pitch-only).
- `E3-S2` (P0, todo) Implement Sample Pitch knob (`-50%..+50%`, pitch+tempo).
- `E3-S3` (P0, todo) Apply both pitch paths to sample in FX-routed mode.
- `E3-S4` (P1, todo) Add precision display and reset-to-zero actions.

Acceptance criteria:

- Main Pitch changes perceived pitch with tempo held.
- Sample Pitch changes both pitch and playback speed.
- Knob ranges are clamped to exactly `-50%..+50%`.

---

### E4 - Effects Engine (P1)

Goal: deliver core DFX-style effects for live performance.

Stories:

- `E4-S1` (P1, todo) Implement Filter (LP/HP/BP).
- `E4-S2` (P1, todo) Implement Filter modes (LFO/Manual/Auto) + resonance.
- `E4-S3` (P1, todo) Implement Delay with BPM-based timing.
- `E4-S4` (P1, todo) Implement Flanger with timing/modulation controls.
- `E4-S5` (P2, todo) Implement Scratch mode gesture + rolling buffer.
- `E4-S6` (P2, todo) Implement 3-band EQ + kill behavior.

Acceptance criteria:

- Effects can be toggled live without dropouts.
- Timing-based effects can lock to BPM (auto or tapped).
- Filter Auto mode reacts predictably to input level range.

---

### E5 - BPM and Timing System (P1)

Goal: make beat-synced functions reliable.

Stories:

- `E5-S1` (P1, todo) Implement auto BPM detection.
- `E5-S2` (P1, todo) Implement tap BPM averaging.
- `E5-S3` (P2, todo) Implement BPM confidence indicator.
- `E5-S4` (P1, todo) Drive quantized sampler/effects timing from BPM engine.
- `E5-S5` (P1, todo) Add timing source abstraction (`Internal BPM` vs `Ableton Link`) to avoid tightly coupling BPM consumers to one clock.

Acceptance criteria:

- BPM value updates during playback and remains stable on steady material.
- Tap BPM can override auto BPM when selected.
- App can swap timing source without breaking sampler quantization logic.

---

### E6 - Sample Library and Persistence (P0)

Goal: app-native save/load and reusable sample sets.

Stories:

- `E6-S1` (P0, todo) Save sample audio and metadata.
- `E6-S2` (P0, todo) Load sample into active sampler state.
- `E6-S3` (P1, todo) Rename/delete/favorite items.
- `E6-S4` (P1, todo) Persist latest session and restore on launch.

Acceptance criteria:

- Saved samples survive app restarts and device reboot.
- Loading sample restores audible result and core metadata.

---

### E7 - UX and Performance Mode (P1)

Goal: touch-first live performance interface.

Stories:

- `E7-S1` (P1, todo) Build tablet layout with dedicated sections.
- `E7-S2` (P1, todo) Implement large controls and press/hold interactions.
- `E7-S3` (P1, todo) Add clear state indicators (recording, clipping, bpm lock, fx route).
- `E7-S4` (P2, todo) Add optional dark stage mode and high-contrast labels.

Acceptance criteria:

- Core live controls are accessible with one hand per side on tablet.
- State changes are visible in less than 100 ms perceived UI delay.

---

### E8 - Quality, Test, Release Readiness (P1)

Goal: make Android MVP stable and shippable.

Stories:

- `E8-S1` (P1, todo) Define supported Android versions/devices matrix.
- `E8-S2` (P1, todo) Add automated tests for sampler/pitch edge cases.
- `E8-S3` (P1, todo) Add long-run soak test for audio dropout/crash.
- `E8-S4` (P2, todo) Build crash logging + performance telemetry.
- `E8-S5` (P1, todo) Add latency benchmark harness (input-to-output and callback jitter) for target Android tablets.

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
- DSP implementation for pitch-only vs pitch+tempo quality/performance tradeoff
- Minimum Android API level and first supported tablet models
- iOS portability stack choice for shared DSP core
- Ableton Link licensing path and distribution implications for planned app license
- Link quantum strategy: fixed value vs mapped to loop/bar length
- Transport policy in Link mode: local-only start/stop vs Link v3 shared start/stop

---

## Change Log

- `2026-03-25` - Initial living requirements + backlog document created from DFX69 context and project goals.
- `2026-03-25` - Added Ableton Link feasibility assessment, effort estimates, Phase 2 Link epic (E9), timing abstraction ticket updates, and latency benchmark ticket.

# RULES FOR CURSOR / CLAUDE / LLM
- FOLLOW AUDIO DEVELOPER BEST PRACTICES FOR TABLETS DO NOT SLOP IT UP AND DON'T CREATE A BUNCH OF DUPLICATION!
- Do not make up or lie about solutions. If you are unsure ask or search the web for context