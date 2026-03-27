package com.freekalizer.effects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class InterleavedFeedbackDelayTest {
    @Test
    fun impulseAppearsAfterDelayWithZeroFeedback() {
        val d = 64
        val delay = InterleavedFeedbackDelay(channels = 1, maxDelayFrames = 256)
        delay.setDelayFrames(d)
        delay.reset()
        val n = 400
        val buf = FloatArray(n)
        buf[0] = 1f
        delay.process(buf, n, feedInput = true, feedback = 0f, wetMix = 1f)
        assertTrue(abs(buf[d] - 1f) < 1e-5f, "expected echo at d, got ${buf[d]}")
    }

    @Test
    fun tailContinuesWhenFeedInputOffWithFeedback() {
        val delay = InterleavedFeedbackDelay(channels = 1, maxDelayFrames = 32)
        delay.setDelayFrames(8)
        delay.reset()
        val fb = 0.9f
        // One impulse in, then silence with feed off — tap should decay over subsequent blocks.
        val a = FloatArray(16)
        a[0] = 1f
        delay.process(a, 16, feedInput = true, feedback = fb, wetMix = 1f)
        val b = FloatArray(40)
        delay.process(b, 40, feedInput = false, feedback = fb, wetMix = 1f)
        val energy = b.map { it * it }.sum()
        assertTrue(energy > 0.001f, "expected decaying tail, energy=$energy")
    }
}
