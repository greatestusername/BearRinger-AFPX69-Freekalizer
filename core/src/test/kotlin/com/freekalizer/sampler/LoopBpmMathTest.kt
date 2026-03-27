package com.freekalizer.sampler

import kotlin.test.Test
import kotlin.test.assertEquals

class LoopBpmMathTest {

    @Test
    fun `one bar 4-4 at 120 bpm is two seconds at 44100 and 48000`() {
        val f441 = QuantizedLoopMath.targetFrames(44_100, 120.0, QuantizedBars.BAR_1)
        assertEquals(88_200, f441)
        assertEquals(120.0, LoopBpmMath.bpmFromLoopFrames(44_100, f441, QuantizedBars.BAR_1), 1e-9)

        val f48 = QuantizedLoopMath.targetFrames(48_000, 120.0, QuantizedBars.BAR_1)
        assertEquals(96_000, f48)
        assertEquals(120.0, LoopBpmMath.bpmFromLoopFrames(48_000, f48, QuantizedBars.BAR_1), 1e-9)
    }

    @Test
    fun `drumloop asset frame count yields 120 at 44100`() {
        val frames = 88_200
        assertEquals(120.0, LoopBpmMath.bpmFromLoopFrames(44_100, frames, QuantizedBars.BAR_1), 1e-6)
    }
}
