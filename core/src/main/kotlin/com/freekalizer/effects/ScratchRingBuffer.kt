package com.freekalizer.effects

import kotlin.math.floor

/**
 * Stereo rolling buffer fed after delay on the FX bus. Supports fractional read lag in frames for scratch.
 * [capacityFrames] must be a power of at least 4.
 */
class ScratchRingBuffer(
    capacityFrames: Int = DEFAULT_CAP_FRAMES
) {
    private val cap = capacityFrames
    private val buf = FloatArray(cap * 2)
    private var writeCount: Long = 0L

    init {
        require(cap >= 4)
        require(cap and (cap - 1) == 0) { "capacityFrames must be a power of 2" }
    }

    fun reset() {
        buf.fill(0f)
        writeCount = 0L
    }

    fun capacityFrames(): Int = cap

    /** Maximum meaningful lag (frames) for interpolation. */
    fun maxLagFrames(): Float = (cap - 2).toFloat()

    fun writeStereoFrame(l: Float, r: Float) {
        val idx = (writeCount % cap.toLong()).toInt() * 2
        buf[idx] = l
        buf[idx + 1] = r
        writeCount++
    }

    fun readStereoAtLagFrames(lagFrames: Float): Pair<Float, Float> {
        val lag = lagFrames.coerceIn(0f, maxLagFrames())
        if (writeCount < 2L) return 0f to 0f
        val readPos = (writeCount - 1L).toDouble() - lag.toDouble()
        val rp = readPos.coerceAtLeast(0.0)
        val i0 = floor(rp).toLong()
        val frac = (rp - i0).toFloat().coerceIn(0f, 1f)
        val l0 = getL(i0)
        val l1 = getL(i0 + 1L)
        val r0 = getR(i0)
        val r1 = getR(i0 + 1L)
        val l = l0 * (1f - frac) + l1 * frac
        val r = r0 * (1f - frac) + r1 * frac
        return l to r
    }

    private fun frameIndex(f: Long): Int {
        var m = (f % cap.toLong()).toInt()
        if (m < 0) m += cap
        return m
    }

    private fun getL(f: Long): Float = buf[frameIndex(f) * 2]
    private fun getR(f: Long): Float = buf[frameIndex(f) * 2 + 1]

    companion object {
        const val DEFAULT_CAP_FRAMES: Int = 32768
    }
}
