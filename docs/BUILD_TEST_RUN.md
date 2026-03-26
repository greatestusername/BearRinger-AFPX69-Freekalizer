# Build, Test, Run

## Current Scope

This repository contains:

- `core` Kotlin JVM portable logic module
- `app` Android application scaffold module

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
- Timing source abstraction tests
- UI blueprint contract tests

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
