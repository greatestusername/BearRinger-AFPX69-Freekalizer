package com.freekalizer.pitch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PitchKnobMathTest {
    @Test
    fun clampsBeyondRange() {
        assertEquals(-50f, PitchKnobMath.clampPercent(-200f))
        assertEquals(50f, PitchKnobMath.clampPercent(99f))
    }

    @Test
    fun playbackSpeedEndpoints() {
        assertEquals(0.5f, PitchKnobMath.toPlaybackSpeedMultiplier(-50f))
        assertEquals(1f, PitchKnobMath.toPlaybackSpeedMultiplier(0f))
        assertEquals(1.5f, PitchKnobMath.toPlaybackSpeedMultiplier(50f))
    }

    @Test
    fun pitchShiftRatioMatchesPlaybackSpeedMapping() {
        val points = listOf(-50f, -25f, 0f, 25f, 50f)
        for (p in points) {
            assertEquals(
                PitchKnobMath.toPlaybackSpeedMultiplier(p),
                PitchKnobMath.toPitchShiftRatio(p),
                1e-6f
            )
        }
    }

    @Test
    fun mappingIsMonotonicAcrossKnobRange() {
        var prev = PitchKnobMath.toPlaybackSpeedMultiplier(-50f)
        for (p in -49..50) {
            val cur = PitchKnobMath.toPlaybackSpeedMultiplier(p.toFloat())
            assertTrue(cur >= prev, "expected non-decreasing mapping at p=$p")
            prev = cur
        }
    }
}
