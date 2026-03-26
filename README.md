# Freekalizer Tablet

Android-first, low-latency audio app foundation inspired by the Behringer Freekalizer DFX69 workflow.

Current repo status:

- Portable Kotlin `core` module is in place.
- Audio engine lifecycle foundation is implemented.
- Timing source abstraction (`Internal BPM` vs `Ableton Link`) is implemented.
- UI blueprint contract for DFX69-style tablet layout and labels is implemented.
- Sampler: quantized bar recording, capped free-length recording, loop playback (`PLAY/STOP`), `REV` (backward loop/SHOT), and an `FX ROUTE` toggle (effects bus vs direct monitor mix, ready for E3/E4 DSP) are wired through `core` and the Android monitoring path.
- Tablet UI (`E7-S1`): scrollable four-zone layout (`activity_main.xml`) aligned to the DFX69 blueprint—dark panels, cyan section headers, live meters and sampler controls; placeholders for EQ/effects; **SHOT** is live (hold-to-play, `E2-S4`); **REV** toggle (`E2-S5`).

The product requirements and backlog live in:

- `FREEKALIZER_ANDROID_PRODUCT_BACKLOG copy.md`

## Quick Start

1. Install dependencies (macOS):
   - Java 17 JDK
2. Verify tools:
   - `java -version`
3. Run tests:
   - `./gradlew :core:test`

### Run in Android Studio

The app module is ready to install on an **API 29+** emulator or device.

1. Open the **repository root** (`tweakalizer-tablet`) in Android Studio, not only the `app` folder.
2. **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**: choose **JDK 17**.
3. Let **Gradle sync** finish. If you see **SDK location not found**, either:
   - **File → Settings → Languages & Frameworks → Android SDK** and set the SDK path (Studio will write `local.properties`), or
   - Copy `local.properties.example` to `local.properties` and set `sdk.dir` (see `docs/BUILD_TEST_RUN.md`).
4. Install **Android SDK Platform 34** (and build-tools) via the SDK Manager if prompted.
5. Create or select a **virtual device** (or plug in hardware with USB debugging).
6. Choose the **app** run configuration and click **Run**. Grant **microphone** when prompted; tap **Start Monitoring** to hear input.

### Emulator system image (new or updated AVD)

To install or switch the **Android system image** used by a virtual device:

1. Open **Device Manager** (toolbar device icon, or **View → Tool Windows → Device Manager**).
2. **Create Device** for a new AVD, or use the **pencil (Edit)** icon on an existing one.
3. In the **system image** list, pick an API level compatible with this project (**minSdk 29**, **compile/target 34**). If the image shows **Download**, click it and let the SDK Manager finish.
4. You can also install platform packages under **Settings → Languages & Frameworks → Android SDK → SDK Platforms** (e.g. Android 14) so those images appear when creating or editing AVDs.

Physical hardware does not use this flow; use the OEM’s flashing/OTA tools if you need a different OS build on a real tablet.

### Verify from the command line (no Studio Run button)

Use the same SDK as `local.properties` (`sdk.dir`), typically `~/Library/Android/sdk` on macOS.

```bash
./gradlew :app:assembleDebug
export ADB="$HOME/Library/Android/sdk/platform-tools/adb"
$ADB devices
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB logcat -c
$ADB shell am start -n com.freekalizer.tablet/.MainActivity
sleep 2
$ADB shell pidof com.freekalizer.tablet   # should print one PID if the process stayed up
$ADB logcat -d | grep -E "AndroidRuntime|FATAL"
```

If the app dies immediately, the last command shows the Java stack trace (e.g. missing permission vs logic bug).

Detailed install and workflow docs:

- `docs/SETUP.md`
- `docs/BUILD_TEST_RUN.md`
- `docs/ARCHITECTURE.md`

## Project Layout

- `app/` - Android app module scaffold (`com.freekalizer.tablet`)
- `core/` - portable logic (audio engine contracts, timing model, UI blueprint contract)
- `docs/` - setup and build/test/run documentation
- `FREEKALIZER_ANDROID_PRODUCT_BACKLOG copy.md` - living requirements and backlog

## Notes

- Minimum Android API target is planned as API 29+.
- App/package ID target for Android scaffolding: `com.freekalizer.tablet`.
- Use Gradle wrapper commands (`./gradlew ...`) for consistent builds.
