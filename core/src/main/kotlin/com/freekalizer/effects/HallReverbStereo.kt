package com.freekalizer.effects

/**
 * Schroeder-style stereo reverb (parallel combs → allpasses). Tuned for 48 kHz tablets; scales delays from 44.1k references.
 * **No allocations** in [processReplaceInterleaved].
 */
class HallReverbStereo(
    sampleRateHz: Int
) {
    private val sr = sampleRateHz.coerceAtLeast(8000)
    private val scale = sr / 44100f

    private val combDelaysL = intArrayOf(1557, 1422, 1277, 1116).map { (it * scale).toInt().coerceAtLeast(32) }.toIntArray()
    private val combDelaysR = intArrayOf(1617, 1491, 1356, 1188).map { (it * scale).toInt().coerceAtLeast(32) }.toIntArray()

    private val combBufL = Array(4) { FloatArray(2048) }
    private val combBufR = Array(4) { FloatArray(2048) }
    private val combWL = IntArray(4)
    private val combWR = IntArray(4)

    private val apLenL = intArrayOf((225 * scale).toInt().coerceAtLeast(8), (341 * scale).toInt().coerceAtLeast(8))
    private val apLenR = intArrayOf((277 * scale).toInt().coerceAtLeast(8), (419 * scale).toInt().coerceAtLeast(8))
    private val apBufL = Array(2) { FloatArray(512) }
    private val apBufR = Array(2) { FloatArray(512) }
    private val apWL = IntArray(2)
    private val apWR = IntArray(2)

    fun reset() {
        for (b in combBufL) b.fill(0f)
        for (b in combBufR) b.fill(0f)
        combWL.fill(0)
        combWR.fill(0)
        for (b in apBufL) b.fill(0f)
        for (b in apBufR) b.fill(0f)
        apWL.fill(0)
        apWR.fill(0)
    }

    fun processReplaceInterleaved(
        interleaved: FloatArray,
        frameCount: Int,
        channels: Int,
        enabled: Boolean,
        cathedral: Boolean,
        decayNorm: Float,
        wetNorm: Float
    ) {
        if (!enabled || frameCount <= 0 || channels <= 0) return
        val wet = wetNorm.coerceIn(0f, 1f)
        if (wet < 1e-5f) return
        val fb = (if (cathedral) 0.88f else 0.84f) + decayNorm.coerceIn(0f, 1f) * (if (cathedral) 0.11f else 0.12f)
        val combGain = fb.coerceIn(0.5f, 0.97f)
        val apG = if (cathedral) 0.6f else 0.5f

        val c = channels.coerceIn(1, 2)
        for (f in 0 until frameCount) {
            val base = f * c
            val inL = interleaved[base]
            val inR = if (c >= 2) interleaved[base + 1] else inL
            val monoIn = (inL + inR) * 0.5f

            var combOutL = 0f
            var combOutR = 0f
            for (i in 0 until 4) {
                combOutL += stepComb(combBufL[i], combDelaysL[i], combWL, i, combGain, monoIn * 0.5f)
                combOutR += stepComb(combBufR[i], combDelaysR[i], combWR, i, combGain, monoIn * 0.5f)
            }
            combOutL *= 0.22f
            combOutR *= 0.22f

            var wL = combOutL
            var wR = combOutR
            for (j in 0 until 2) {
                wL = stepAllpass(apBufL[j], apLenL[j], apWL, j, apG, wL)
                wR = stepAllpass(apBufR[j], apLenR[j], apWR, j, apG, wR)
            }
            val cross = (wL + wR) * 0.35f
            wL = wL * 0.85f + cross
            wR = wR * 0.85f + cross

            interleaved[base] = inL + wet * (wL - inL * 0.35f)
            if (c >= 2) {
                interleaved[base + 1] = inR + wet * (wR - inR * 0.35f)
            }
        }
    }

    private fun stepComb(buf: FloatArray, d: Int, wArr: IntArray, idx: Int, g: Float, input: Float): Float {
        val len = buf.size
        var w = wArr[idx]
        val r = (w - d + len) % len
        val out = buf[r]
        buf[w] = input + g * out
        w = (w + 1) % len
        wArr[idx] = w
        return out
    }

    private fun stepAllpass(buf: FloatArray, d: Int, wArr: IntArray, idx: Int, g: Float, input: Float): Float {
        val len = buf.size
        var w = wArr[idx]
        val r = (w - d + len) % len
        val delayed = buf[r]
        val out = -g * input + delayed
        buf[w] = input + g * delayed
        w = (w + 1) % len
        wArr[idx] = w
        return out
    }

}
