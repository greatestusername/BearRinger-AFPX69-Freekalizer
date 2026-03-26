# Freekalizer Tablet

Android-first, low-latency audio app foundation inspired by the Behringer Freekalizer DFX69 workflow.

Current repo status:

- Portable Kotlin `core` module is in place.
- Audio engine lifecycle foundation is implemented.
- Timing source abstraction (`Internal BPM` vs `Ableton Link`) is implemented.
- UI blueprint contract for DFX69-style tablet layout and labels is implemented.

The product requirements and backlog live in:

- `FREEKALIZER_ANDROID_PRODUCT_BACKLOG copy.md`

## Quick Start

1. Install dependencies (macOS):
   - Java 17 JDK
2. Verify tools:
   - `java -version`
3. Run tests:
   - `./gradlew :core:test`

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
