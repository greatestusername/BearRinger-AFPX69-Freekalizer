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

Current verification flow is test-based:

- Audio engine lifecycle tests
- Timing source abstraction tests
- UI blueprint contract tests

## Troubleshooting

- `SDK location not found` or Android task failures
  - Install Android Studio + SDK (API 29+), then set `ANDROID_HOME` if needed.
- `Unsupported class file major version` or Java mismatch
  - Ensure Java 17 is active.
- Build fails after adding new modules
  - Re-sync Gradle and verify module include lines in `settings.gradle.kts`.
