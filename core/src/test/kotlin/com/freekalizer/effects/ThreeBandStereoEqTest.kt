package com.freekalizer.effects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ThreeBandStereoEqTest {

    @Test
    fun flatEqRoughlyPreservesImpulseEnergy() {
        val eq = ThreeBandStereoEq()
        val sr = 48000
        eq.syncCoefficients(sr, 0f, 0f, 0f, false, false, false, false)
        val buf = floatArrayOf(1f, 1f, 0f, 0f, 0f, 0f)
        eq.processInterleavedStereo(buf, 3)
        val outEnergy = buf[0] * buf[0] + buf[1] * buf[1]
        assertTrue(outEnergy > 0.8f, "expected pass-through-ish gain, got $outEnergy")
    }

    @Test
    fun killMidStronglyAttenuatesMidbandTone() {
        val eq = ThreeBandStereoEq()
        val sr = 48000
        eq.syncCoefficients(sr, 0f, 0f, 0f, false, true, false, false)
        val n = 2048
        val buf = FloatArray(n * 2)
        for (f in 0 until n) {
            val w = 2.0 * kotlin.math.PI * 1800.0 * f / sr
            val s = kotlin.math.sin(w).toFloat()
            buf[f * 2] = s
            buf[f * 2 + 1] = s
        }
        eq.processInterleavedStereo(buf, n)
        var peak = 0f
        for (i in (n - 400) until n) {
            peak = kotlin.math.max(peak, kotlin.math.abs(buf[i * 2]))
        }
        assertTrue(peak < 0.05f, "expected mid kill to cut 1.8 kHz tone, tail peak=$peak")
    }
}
