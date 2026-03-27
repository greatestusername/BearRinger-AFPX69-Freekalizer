package com.freekalizer.effects

import kotlin.test.Test
import kotlin.test.assertEquals

class FlangerBeatMathTest {
    @Test
    fun oneBeatAt120IsTwoHertz() {
        // 120 BPM → 2 beats/s → 1 beat = 0.5 s → one LFO cycle per beat = 2 Hz
        assertEquals(2.0, FlangerBeatMath.beatsPerCycleToLfoHz(120.0, 1.0), 1e-6)
    }
}
