Implement ASCII Hardware Layout (Android Tablet)

Outcome

Create a hardware-faithful portrait UI that matches UI-Layout-Draft-Ascii-Art.txt while preserving existing audio control behavior in MainActivity. The surface keeps only performance-critical controls visible; detailed/secondary controls move to submenu panels launched from dedicated buttons in empty board areas. NO SCROLLING TO USE THE INTERFACE!

Current Baseline (what to leverage)





Existing control bindings are centralized in MainActivity and already implement sampler/effects/BPM/EQ/scratch behavior.



Existing blueprint contract in PerformanceUiSpec already models four top-level zones; keep this as source-of-truth for zone semantics.



Existing layout in activity_main.xml is functionally complete but visually linear; replace with board-style composition.

ASCII-to-UI Mapping Contract (LLM-friendly)

Define this mapping explicitly in docs and implement 1:1 in view IDs/names:





Top strip: IN LEVEL, OVERLOAD, BPM readout, MASTER PITCH knob/value, DRY/WET knob, beat-division indicator LEDs.



Sampler strip (upper-mid): sample PITCH knob/value, FX ROUTE, REV, PLAY/STOP, SHOT, large REC button, loop-length indicators (1/2/4/8/16/FREE).



EQ strip (mid): LOW/MID/HIGH EQ knobs/sliders + LOW/MID/HIGH KILL buttons + TAP BPM.



Filter strip (lower-mid): filter type LEDs (LP/BP/HP), mode LEDs (AUTO/LFO/MANUAL), cutoff knob, resonance knob, filter on/off button, modulation select button.



Bottom zone: dominant scratch wheel, with DELAY ON (and momentary) and FLANGER ON (and momentary) buttons near wheel.



Empty-area buttons: MENU AUDIO, MENU BPM, MENU SAMPLER, MENU FX, MENU LIBRARY, MENU SYSTEM placed at board margins/corners.

Information Architecture Rule (critical)

Use this deterministic rule during implementation:





If control exists in ASCII drawing -> keep visible on main board.



If required by backlog but not visibly represented in ASCII -> move into a submenu.



No hidden deep stack: submenu depth max = 1 panel from board.



Submenu close returns to same board state; no navigation away from performance screen.

Submenu assignments for “not in ASCII” details





Audio menu: device input/output spinners, mic permission action, route diagnostics text.



BPM menu: AUTO follow toggle, confidence/lock diagnostics, default BPM reset.



Sampler menu: free-rec cap picker, capture status text, progress details, reverse metadata display.



FX menu: advanced delay/flanger/filter numeric controls (beats/depth/feedback/wet/manual params).



Library menu: save/load/rename/delete/favorite flows and sample metadata management.



System menu: build/debug labels and non-performance status lines.

UI Architecture and File Plan





Replace linear ScrollView board with layered board layout in activity_main.xml:





Root ConstraintLayout sized to tablet portrait.



Main board background panel.



Zone containers anchored to ASCII-relative positions.



Submenu launch buttons anchored into whitespace/corners.



Add reusable hardware widgets:





KnobView (or styled circular ImageButton + indicator ring) for pitch/dry-wet/filter knobs.



LED indicator component for beat divisions and mode/type indicators.



Large circular REC and scratch wheel visuals.



Add submenu panel containers in same screen (right/left slide-up panel or modal sheet), each mapped to one menu button.



Keep all existing behavior in MainActivity bindings; only rewire view IDs and section visibility toggles.



Extract UI-only state handling (submenu open/close, active panel) into a small controller/helper to avoid further bloating MainActivity.

Implementation Phases

Phase 1: Surface Skeleton and Placement





Build board scaffold with all ASCII-visible controls in correct relative positions.



Ensure touch targets are tablet-large and one-hand reachable per side.



Keep old controls temporarily hidden behind debug flag until parity validation.

Phase 2: Submenu Migration





Move non-ASCII controls into submenu panels by domain (Audio/BPM/Sampler/FX/Library/System).



Add dedicated launch buttons in empty board areas.



Ensure no control is duplicated across main surface and submenu except read-only mirrored status when needed.

Phase 3: Wiring and Parity





Rebind all moved controls to current MainActivity logic.



Validate every FR item remains reachable (main or submenu).



Preserve existing touch-and-hold behaviors (SHOT, EQ kills, scratch gesture).

Phase 4: Visual/Usability Hardening





Tune spacing/color/contrast to silver+black with LED accents.



Verify layout stability on target tablet resolutions.



Add concise implementation notes for future LLM sessions in backlog/docs.

Validation Checklist (acceptance)





Board visually matches ASCII zone composition and dominant scratch-wheel hierarchy.



All backlog-required controls are reachable in <= 1 tap from board surface.



Every non-ASCII requirement is accessible through one of the empty-area menu buttons.



No audio interaction regressions (REC/PLAY/SHOT/REV, filter/delay/flanger, BPM, EQ kill, scratch).



Portrait-only behavior remains stable and touch latency acceptable.

LLM Execution Guardrails





Reuse existing IDs/handlers where possible; avoid changing audio engine semantics.



Prioritize view hierarchy and wiring changes in activity_main.xml and MainActivity.



Keep naming aligned with ManualLabels in PerformanceUiSpec.



Update [FREEKALIZER_ANDROID_PRODUCT_BACKLOG copy.md]( /Users/macbearpig/repos/greatestusername/tweakalizer-tablet/FREEKALIZER_ANDROID_PRODUCT_BACKLOG copy.md ) with any scope mapping decisions while implementing.

flowchart TD
    asciiLayout[ASCIIBoardSpec] --> boardControls[MainBoardVisibleControls]
    backlogReqs[BacklogRequirements] --> reqCheck{VisibleInASCII}
    reqCheck -->|yes| boardControls
    reqCheck -->|no| submenuPanels[SubmenuPanels]
    emptyButtons[EmptyAreaMenuButtons] --> submenuPanels
    boardControls --> mainActivityBindings[ExistingMainActivityBindings]
    submenuPanels --> mainActivityBindings
    mainActivityBindings --> audioEngine[CurrentAudioEngineController]

