package com.freekalizer.effects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class StereoFlangerTest {
    @Test
    fun bypassPreservesSineWhileAdvancing() {
        val fl = StereoFlanger(channels = 1, capFrames = 512)
        fl.reset()
        val n = 256
        val buf = FloatArray(n)
        for (i in buf.indices) {
            buf[i] = kotlin.math.sin(2.0 * kotlin.math.PI * 10.0 * i / 48_000.0).toFloat()
        }
        val copy = buf.copyOf()
        fl.process(
            buf,
            n,
            sampleRateHz = 48_000,
            lfoHz = 0.5,
            baseDelayMs = 3f,
            sweepMs = 1f,
            manualMs = 0f,
            wet = 1f,
            bypass = true
        )
        for (i in buf.indices) {
            assertTrue(abs(buf[i] - copy[i]) < 1e-5f, "i=$i ${buf[i]} vs ${copy[i]}")
        }
    }

    @Test
    fun engagedChangesOutputFromFeedForwardComb() {
        val fl = StereoFlanger(channels = 1, capFrames = 512)
        fl.reset()
        val n = 2_000
        val dry = FloatArray(n) { 0f }
        for (i in 0 until n step 50) dry[i] = 1f
        val proc = dry.copyOf()
        fl.process(
            proc,
            n,
            sampleRateHz = 48_000,
            lfoHz = 0.25,
            baseDelayMs = 3f,
            sweepMs = 1.5f,
            manualMs = 0f,
            wet = 0.8f,
            bypass = false
        )
        var diff = 0f
        for (i in 0 until n) diff += kotlin.math.abs(proc[i] - dry[i])
        assertTrue(diff > 1f, "expected audible comb difference, diff=$diff")
    }
}
