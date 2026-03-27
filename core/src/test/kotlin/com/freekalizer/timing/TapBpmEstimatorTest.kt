package com.freekalizer.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TapBpmEstimatorTest {
    @Test
    fun needsThreeTaps() {
        val e = TapBpmEstimator()
        assertNull(e.tap(0.0))
        assertNull(e.tap(0.5))
        val bpm = e.tap(1.0)
        assertEquals(120.0, bpm!!, 0.5)
    }

    @Test
    fun halfSecondIntervalIs120Bpm() {
        val e = TapBpmEstimator()
        e.tap(0.0)
        e.tap(0.5)
        assertEquals(120.0, e.tap(1.0)!!, 0.01)
    }
}
