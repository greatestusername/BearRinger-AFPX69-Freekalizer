package com.freekalizer.audio

/**
 * Linear interpolation resample for interleaved PCM (same [channels] before/after).
 * Allocates a new buffer; intended for **offline** load paths, not the audio callback.
 */
object PcmResampler {
    fun resampleInterleavedLinear(
        pcm: FloatArray,
        channels: Int,
        sourceRateHz: Int,
        targetRateHz: Int
    ): FloatArray {
        require(channels > 0) { "channels must be > 0" }
        require(sourceRateHz > 0 && targetRateHz > 0) { "rates must be > 0" }
        if (sourceRateHz == targetRateHz) return pcm.copyOf()
        val inFrames = pcm.size / channels
        if (inFrames <= 0) return FloatArray(0)
        val outFrames = (inFrames.toLong() * targetRateHz / sourceRateHz).toInt().coerceAtLeast(1)
        val out = FloatArray(outFrames * channels)
        val scale = sourceRateHz.toDouble() / targetRateHz.toDouble()
        for (of in 0 until outFrames) {
            val srcPos = of * scale
            val i0 = kotlin.math.floor(srcPos).toInt().coerceIn(0, inFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(inFrames - 1)
            val frac = (srcPos - i0).toFloat()
            val base0 = i0 * channels
            val base1 = i1 * channels
            val ob = of * channels
            for (c in 0 until channels) {
                val s0 = pcm[base0 + c]
                val s1 = pcm[base1 + c]
                out[ob + c] = s0 + (s1 - s0) * frac
            }
        }
        return out
    }
}
