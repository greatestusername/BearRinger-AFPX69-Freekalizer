package com.freekalizer.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RBJ biquad for EQ shelves and peaking band (Web Audio / EQ cookbook).
 * Direct Form II Transposed; no allocations in [process].
 */
class ShelfPeakingBiquad {
    private var b0: Float = 1f
    private var b1: Float = 0f
    private var b2: Float = 0f
    private var a1: Float = 0f
    private var a2: Float = 0f
    private var s1: Float = 0f
    private var s2: Float = 0f

    fun reset() {
        s1 = 0f
        s2 = 0f
    }

    fun setPeakingDb(sampleRateHz: Int, freqHz: Float, q: Float, gainDb: Float) {
        if (kotlin.math.abs(gainDb) < 1e-5f) {
            b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
            return
        }
        val A = sqrt(10.0.pow(gainDb / 20.0))
        val w0 = 2.0 * PI * freqHz / sampleRateHz.toDouble()
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / (2.0 * q.toDouble().coerceAtLeast(0.01))

        var b0d = 1.0 + alpha * A
        var b1d = -2.0 * cosW
        var b2d = 1.0 - alpha * A
        var a0d = 1.0 + alpha / A
        val a1d = -2.0 * cosW
        var a2d = 1.0 - alpha / A
        normalize(b0d, b1d, b2d, a0d, a1d, a2d)
    }

    fun setLowShelfDb(sampleRateHz: Int, freqHz: Float, q: Float, gainDb: Float) {
        if (kotlin.math.abs(gainDb) < 1e-5f) {
            b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
            return
        }
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRateHz.toDouble()
        val cosW = cos(w0)
        val sinW = sin(w0)
        val S = (1.0 / q.toDouble().coerceAtLeast(0.1)).coerceIn(0.5, 3.0)
        val alpha = (sinW / 2.0) * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)

        var b0d = A * ((A + 1.0) - (A - 1.0) * cosW + 2.0 * sqrt(A) * alpha)
        var b1d = 2.0 * A * ((A - 1.0) - (A + 1.0) * cosW)
        var b2d = A * ((A + 1.0) - (A - 1.0) * cosW - 2.0 * sqrt(A) * alpha)
        var a0d = (A + 1.0) + (A - 1.0) * cosW + 2.0 * sqrt(A) * alpha
        val a1d = -2.0 * ((A - 1.0) + (A + 1.0) * cosW)
        var a2d = (A + 1.0) + (A - 1.0) * cosW - 2.0 * sqrt(A) * alpha
        normalize(b0d, b1d, b2d, a0d, a1d, a2d)
    }

    fun setHighShelfDb(sampleRateHz: Int, freqHz: Float, q: Float, gainDb: Float) {
        if (kotlin.math.abs(gainDb) < 1e-5f) {
            b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
            return
        }
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRateHz.toDouble()
        val cosW = cos(w0)
        val sinW = sin(w0)
        val S = (1.0 / q.toDouble().coerceAtLeast(0.1)).coerceIn(0.5, 3.0)
        val alpha = (sinW / 2.0) * sqrt((A + 1.0 / A) * (1.0 / S - 1.0) + 2.0)

        var b0d = A * ((A + 1.0) + (A - 1.0) * cosW + 2.0 * sqrt(A) * alpha)
        var b1d = -2.0 * A * ((A - 1.0) + (A + 1.0) * cosW)
        var b2d = A * ((A + 1.0) + (A - 1.0) * cosW - 2.0 * sqrt(A) * alpha)
        var a0d = (A + 1.0) - (A - 1.0) * cosW + 2.0 * sqrt(A) * alpha
        val a1d = 2.0 * ((A - 1.0) - (A + 1.0) * cosW)
        var a2d = (A + 1.0) - (A - 1.0) * cosW - 2.0 * sqrt(A) * alpha
        normalize(b0d, b1d, b2d, a0d, a1d, a2d)
    }

    private fun normalize(b0d: Double, b1d: Double, b2d: Double, a0d: Double, a1d: Double, a2d: Double) {
        b0 = (b0d / a0d).toFloat()
        b1 = (b1d / a0d).toFloat()
        b2 = (b2d / a0d).toFloat()
        a1 = (a1d / a0d).toFloat()
        a2 = (a2d / a0d).toFloat()
    }

    fun process(x: Float): Float {
        val y = b0 * x + s1
        val nextS1 = b1 * x + s2 - a1 * y
        val nextS2 = b2 * x - a2 * y
        s1 = nextS1
        s2 = nextS2
        return y
    }
}
