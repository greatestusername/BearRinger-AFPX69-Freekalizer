package com.freekalizer.effects

import kotlin.test.Test
import kotlin.test.assertEquals

class DelayBeatMathTest {
    @Test
    fun oneBeatAt120IsHalfSecondAt48k() {
        assertEquals(24_000, DelayBeatMath.beatsToDelayFrames(120.0, 1.0, 48_000))
    }

    @Test
    fun quarterBeatAt60IsQuarterSecondAt48k() {
        assertEquals(12_000, DelayBeatMath.beatsToDelayFrames(60.0, 0.25, 48_000))
    }
}
