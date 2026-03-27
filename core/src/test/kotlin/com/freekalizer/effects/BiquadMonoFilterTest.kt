package com.freekalizer.effects

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class BiquadMonoFilterTest {

    private fun rms(xs: FloatArray, offset: Int, count: Int): Double {
        var sum = 0.0
        val end = offset + count
        for (i in offset until end) {
            val v = xs[i].toDouble()
            sum += v * v
        }
        return kotlin.math.sqrt(sum / count.toDouble())
    }

    @Test
    fun `low pass keeps low tone more than high tone`() {
        val sr = 48_000
        val cutoff = 1_000f
        val q = 0.707f

        val totalFrames = (sr * 0.4).toInt()
        val skipFrames = (sr * 0.1).toInt()
        val measureFrames = totalFrames - skipFrames

        fun estimate(filterType: FilterType, toneHz: Double): Double {
            val f = BiquadMonoFilter()
            f.setCoefficients(filterType, sr, cutoff, q)
            f.reset()

            val out = FloatArray(totalFrames)
            for (n in 0 until totalFrames) {
                val x = sin(2.0 * PI * toneHz * n.toDouble() / sr.toDouble()).toFloat()
                out[n] = f.processSample(x)
            }
            return rms(out, skipFrames, measureFrames)
        }

        val lpLow = estimate(FilterType.LOW_PASS, 200.0)
        val lpHigh = estimate(FilterType.LOW_PASS, 2_000.0)

        assertTrue(lpLow > lpHigh * 2.0, "lpLow=$lpLow lpHigh=$lpHigh")
        assertTrue(lpHigh.isFinite())
    }

    @Test
    fun `high pass keeps high tone more than low tone`() {
        val sr = 48_000
        val cutoff = 1_000f
        val q = 0.707f

        val totalFrames = (sr * 0.4).toInt()
        val skipFrames = (sr * 0.1).toInt()
        val measureFrames = totalFrames - skipFrames

        fun estimate(filterType: FilterType, toneHz: Double): Double {
            val f = BiquadMonoFilter()
            f.setCoefficients(filterType, sr, cutoff, q)
            f.reset()

            val out = FloatArray(totalFrames)
            for (n in 0 until totalFrames) {
                val x = sin(2.0 * PI * toneHz * n.toDouble() / sr.toDouble()).toFloat()
                out[n] = f.processSample(x)
            }
            return rms(out, skipFrames, measureFrames)
        }

        val hpLow = estimate(FilterType.HIGH_PASS, 200.0)
        val hpHigh = estimate(FilterType.HIGH_PASS, 2_000.0)

        assertTrue(hpHigh > hpLow * 2.0, "hpHigh=$hpHigh hpLow=$hpLow")
        assertTrue(hpLow.isFinite())
    }

    @Test
    fun `band pass peaks near cutoff`() {
        val sr = 48_000
        val cutoff = 1_000f
        val q = 0.707f

        val totalFrames = (sr * 0.4).toInt()
        val skipFrames = (sr * 0.1).toInt()
        val measureFrames = totalFrames - skipFrames

        fun estimate(filterType: FilterType, toneHz: Double): Double {
            val f = BiquadMonoFilter()
            f.setCoefficients(filterType, sr, cutoff, q)
            f.reset()

            val out = FloatArray(totalFrames)
            for (n in 0 until totalFrames) {
                val x = sin(2.0 * PI * toneHz * n.toDouble() / sr.toDouble()).toFloat()
                out[n] = f.processSample(x)
            }
            return rms(out, skipFrames, measureFrames)
        }

        val bpAtCutoff = estimate(FilterType.BAND_PASS, 1_000.0)
        val bpLow = estimate(FilterType.BAND_PASS, 200.0)
        val bpHigh = estimate(FilterType.BAND_PASS, 2_000.0)

        // Biquad "band-pass" frequency response shape depends on exact RBJ variant/Q;
        // keep the threshold modest to avoid flakiness across JVM trig implementations.
        assertTrue(bpAtCutoff > bpLow * 1.2, "bpAtCutoff=$bpAtCutoff bpLow=$bpLow")
        assertTrue(bpAtCutoff > bpHigh * 1.1, "bpAtCutoff=$bpAtCutoff bpHigh=$bpHigh")
    }
}

