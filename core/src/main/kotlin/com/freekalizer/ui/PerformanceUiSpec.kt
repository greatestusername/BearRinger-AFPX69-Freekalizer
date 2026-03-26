package com.freekalizer.ui

enum class UiOrientation {
    PORTRAIT
}

enum class UiZone {
    TOP_STATUS_AND_MASTER,
    SAMPLER_TRANSPORT_AND_ROUTING,
    EQ_AND_KILL,
    PERFORMANCE_SURFACE
}

/**
 * Manual-parity labels should be centralized here to keep terms consistent
 * across future Compose screens and docs.
 */
object ManualLabels {
    const val IN_LEVEL = "IN LEVEL"
    const val OVERLOAD = "OVERLOAD"
    const val DISPLAY = "DISPLAY"
    const val MIX = "MIX"
    const val DRY = "DRY"
    const val WET = "WET"
    const val PITCH = "PITCH"
    const val REC = "REC"
    const val PLAY_STOP = "PLAY/STOP"
    const val SHOT = "SHOT"
    const val REV = "REV"
    const val FILTER = "FILTER"
    const val FLANGER = "FLANGER"
    const val DELAY = "DELAY"
    const val SCRATCH = "SCRATCH"
}

data class ZoneSpec(
    val zone: UiZone,
    val requiredControls: List<String>
)

data class PerformanceUiSpec(
    val orientation: UiOrientation,
    val zones: List<ZoneSpec>,
    val noDeepMenusExceptSampleLibrary: Boolean,
    val largePerformanceControls: Boolean
)

object Dfx69TabletUiBlueprint {
    val mvp: PerformanceUiSpec = PerformanceUiSpec(
        orientation = UiOrientation.PORTRAIT,
        zones = listOf(
            ZoneSpec(
                zone = UiZone.TOP_STATUS_AND_MASTER,
                requiredControls = listOf(
                    ManualLabels.IN_LEVEL,
                    ManualLabels.OVERLOAD,
                    "BPM",
                    "MASTER ${ManualLabels.PITCH}"
                )
            ),
            ZoneSpec(
                zone = UiZone.SAMPLER_TRANSPORT_AND_ROUTING,
                requiredControls = listOf(
                    ManualLabels.REC,
                    ManualLabels.PLAY_STOP,
                    ManualLabels.SHOT,
                    ManualLabels.REV,
                    "LOOP LENGTH",
                    "FX ROUTE",
                    "SAMPLE ${ManualLabels.PITCH}"
                )
            ),
            ZoneSpec(
                zone = UiZone.EQ_AND_KILL,
                requiredControls = listOf("LOW", "MID", "HIGH", "KILL")
            ),
            ZoneSpec(
                zone = UiZone.PERFORMANCE_SURFACE,
                requiredControls = listOf(
                    "SCRATCH/DATA WHEEL",
                    ManualLabels.FILTER,
                    ManualLabels.FLANGER,
                    ManualLabels.DELAY,
                    ManualLabels.MIX
                )
            )
        ),
        noDeepMenusExceptSampleLibrary = true,
        largePerformanceControls = true
    )
}
