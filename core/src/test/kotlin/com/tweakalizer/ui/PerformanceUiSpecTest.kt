package com.tweakalizer.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceUiSpecTest {

    @Test
    fun mvpBlueprintIsPortraitOnly() {
        assertEquals(UiOrientation.PORTRAIT, Dfx69TabletUiBlueprint.mvp.orientation)
    }

    @Test
    fun mvpBlueprintContainsFourPrimaryZones() {
        assertEquals(4, Dfx69TabletUiBlueprint.mvp.zones.size)
    }

    @Test
    fun mvpBlueprintContainsCriticalTransportControls() {
        val samplerZone = Dfx69TabletUiBlueprint.mvp.zones.first {
            it.zone == UiZone.SAMPLER_TRANSPORT_AND_ROUTING
        }

        assertTrue(samplerZone.requiredControls.contains(ManualLabels.REC))
        assertTrue(samplerZone.requiredControls.contains(ManualLabels.PLAY_STOP))
        assertTrue(samplerZone.requiredControls.contains(ManualLabels.SHOT))
        assertTrue(samplerZone.requiredControls.contains(ManualLabels.REV))
    }
}
