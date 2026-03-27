package com.freekalizer.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Realtime biquad (2nd-order IIR) mono filter.
 *
 * - No allocations in [processSample]
 * - Coefficient updates happen via [setCoefficients] (called rarely from audio callback on param changes)
 */
class BiquadMonoFilter {
    // Normalized coefficients (a0 == 1).
    private var b0: Float = 1f
    private var b1: Float = 0f
    private var b2: Float = 0f
    private var a1: Float = 0f
    private var a2: Float = 0f

    // Direct-Form II Transposed state.
    private var s1: Float = 0f
    private var s2: Float = 0f

    fun reset() {
        s1 = 0f
        s2 = 0f
    }

    fun setCoefficients(
        type: FilterType,
        sampleRateHz: Int,
        cutoffHz: Float,
        q: Float
    ) {
        require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
        require(q > 0f) { "q must be > 0" }

        val nyq = sampleRateHz / 2f
        val cutoff = cutoffHz.coerceIn(20f, nyq - 1f)
        val omega = 2.0 * PI * (cutoff.toDouble() / sampleRateHz.toDouble())
        val sinW = sin(omega)
        val cosW = cos(omega)

        val alpha = (sinW / (2.0 * q.toDouble()))

        val b0d: Double
        val b1d: Double
        val b2d: Double
        val a0d: Double
        val a1d: Double
        val a2d: Double

        when (type) {
            FilterType.LOW_PASS -> {
                b0d = (1.0 - cosW) / 2.0
                b1d = 1.0 - cosW
                b2d = (1.0 - cosW) / 2.0
                a0d = 1.0 + alpha
                a1d = -2.0 * cosW
                a2d = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                b0d = (1.0 + cosW) / 2.0
                b1d = -(1.0 + cosW)
                b2d = (1.0 + cosW) / 2.0
                a0d = 1.0 + alpha
                a1d = -2.0 * cosW
                a2d = 1.0 - alpha
            }
            FilterType.BAND_PASS -> {
                // Constant skirt gain, peak gain = Q (RBJ "band-pass").
                b0d = alpha
                b1d = 0.0
                b2d = -alpha
                a0d = 1.0 + alpha
                a1d = -2.0 * cosW
                a2d = 1.0 - alpha
            }
        }

        // Normalize (a0 = 1)
        b0 = (b0d / a0d).toFloat()
        b1 = (b1d / a0d).toFloat()
        b2 = (b2d / a0d).toFloat()
        a1 = (a1d / a0d).toFloat()
        a2 = (a2d / a0d).toFloat()
    }

    fun processSample(x: Float): Float {
        // DF-II Transposed:
        // y[n] = b0*x[n] + s1
        // s1  = b1*x[n] + s2 - a1*y[n]
        // s2  = b2*x[n] - a2*y[n]
        val y = b0 * x + s1
        val nextS1 = b1 * x + s2 - a1 * y
        val nextS2 = b2 * x - a2 * y
        s1 = nextS1
        s2 = nextS2
        return y
    }
}

