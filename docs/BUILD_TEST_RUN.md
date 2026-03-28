# Build, Test, Run

## Current Scope

This repository contains:

- `core` Kotlin JVM portable logic module
- `app` Android application scaffold module

## CI

On GitHub, pull requests run **PR checks** (same Gradle commands as below). See [GITHUB_GOVERNANCE.md](GITHUB_GOVERNANCE.md) for branch protection and manual APK workflow.

## Build

From repo root:

```bash
./gradlew build
```

## Test

Run all tests:

```bash
./gradlew test
```

Run only core tests:

```bash
./gradlew :core:test
```

Validate Android module compilation:

```bash
./gradlew :app:assembleDebug
```

## Run

The debug build ships a **preset 1-bar drum loop** (`assets/drumloop.wav`, 120 BPM) loaded on startup so you can test sampler + pitch after **Start monitoring** → **PLAY/STOP** without recording.

**Sample library** (`E6`): after capturing or with the preset loaded, use **SAVE to library** (optional name); clips persist under the app’s private files directory (`sample_library/`). **LOAD from library** restores audio and metadata (pitch, reverse, quantized bar hint when saved). Use **Rename** / **Delete** / **favorite** on the selected clip; favorites sort to the top (★ prefix). The **last loaded or saved** library id is restored on the next app launch when the files still exist.

**BPM**: enable **Follow AUTO BPM** to drive the internal tempo from live input when the auto detector’s confidence is high; **TAP BPM** still overrides. The status line shows the AUTO estimate and confidence.

**Delay** (`E4-S3`, FX route on): set **echo time in beats** (vs current BPM), **feedback**, and **wet**; turn **DELAY send** off to stop new audio entering the line while the **echo tail** decays.

**Flanger** (`E4-S4`, FX route on): **LFO period in beats**, **base delay**, **sweep**, **manual** offset, **wet**; turn **FLANGER** off to bypass the effect while the modulator keeps running for clean toggling.

Current runtime options:

- JVM `core`: test-only (no `main` entrypoint)
- Android `app`: run on emulator/device via Android Studio or `adb` install flow

### Android Studio (recommended)

1. Open the **repo root** as the Gradle project (the folder that contains `settings.gradle.kts`).
2. Use **Gradle JDK 17** (Settings → Build Tools → Gradle).
3. After sync, select run configuration **app** and a device with **API 29+**.
4. `local.properties` is **gitignored**. If sync fails, create it in the repo root:

   ```properties
   sdk.dir=/absolute/path/to/Android/sdk
   ```

   Or copy `local.properties.example` → `local.properties` and edit `sdk.dir`. Android Studio usually creates this file automatically once the Android SDK path is configured.

5. Install **SDK Platform 34** (matches `compileSdk`) if the IDE prompts you.

### Command-line install (debug)

With a device connected (`adb devices`):

```bash
./gradlew :app:installDebug
adb shell am start -n com.freekalizer.tablet/.MainActivity
```

Current verification flow is test-based:

- Audio engine lifecycle tests
- Timing source abstraction tests (`TapBpmEstimator`, `AutoBpmEstimator`)
- Effects tests (`BiquadMonoFilter`, delay line + `DelayBeatMath`, flanger + `FlangerBeatMath`)
- Sampler + pitch edge-case tests (`SamplerLoopPlayerTest`, `PitchKnobMathTest`)
- Sampler soak-style long-run stress (`SamplerLoopPlayerSoakTest`)
- UI blueprint contract tests

For device coverage and latency benchmarking guidance:

- `docs/DEVICE_MATRIX.md`
- `docs/LATENCY_BENCHMARK.md`

## Troubleshooting

- `SDK location not found` or Android task failures
  - Install Android Studio + SDK (API 29+ platform, API 34 for compile). Add `sdk.dir` to `local.properties` (see above).
- **Run** button disabled or no module
  - Ensure the opened folder is the project root and Gradle sync succeeded; pick configuration **app**.
- `Unsupported class file major version` or Java mismatch
  - Ensure Java 17 is active for Gradle (IDE setting and `java -version`).
- Build fails after adding new modules
  - Re-sync Gradle and verify module include lines in `settings.gradle.kts`.
- App installs but **no audio** on emulator
  - Enable microphone in AVD extended controls; use **Start Monitoring** after granting `RECORD_AUDIO`.
