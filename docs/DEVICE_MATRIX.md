# Supported Android Device Matrix (E8-S1)

This matrix defines the minimum validation coverage for Android MVP releases.

## OS / API Support

- **Minimum runtime API:** 29 (Android 10)
- **Target/compile API:** 34
- **Primary test band:** API 29, 31, 33, 34

## Device Classes

- **Tablet (primary target):** 10-13 inch Android tablet with built-in speaker + mic
- **Phone (sanity only):** recent Android handset for non-tablet regressions
- **USB audio path:** one Android device with class-compliant USB interface
- **Headset path:** one device with wired or USB-C headset input/output path

## Required Validation Passes

For each primary matrix row, run:

1. App launch + permission + monitor start/stop
2. Route changes (plug/unplug where applicable) with no crash
3. Sampler workflow: quantized REC, free REC, PLAY/STOP, SHOT, REV
4. FX workflow: filter, delay, flanger, scratch, EQ kill
5. Save/load/rename/delete/favorite sample library flow
6. 10-minute continuous run with no crash/dropout (E8-S3 short pass)

## Matrix Rows

| Row | Android API | Device Type | Audio Path Focus | Status |
|---|---:|---|---|---|
| M1 | 29 | Tablet | Built-in mic + speaker | Planned |
| M2 | 31 | Tablet | Built-in + headset | Planned |
| M3 | 33 | Tablet | USB interface I/O | Planned |
| M4 | 34 | Tablet | Built-in + USB-C headset | Planned |
| M5 | 34 | Phone (sanity) | Built-in path | Planned |

## Exit Criteria for MVP Matrix

- All required validation passes complete for M1-M4
- No open P0 defects across matrix runs
- Latency benchmark captured at least once per M1-M4 (see `docs/LATENCY_BENCHMARK.md`)

