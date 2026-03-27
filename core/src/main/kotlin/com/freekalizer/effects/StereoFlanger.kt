package com.freekalizer.effects

import kotlin.math.floor
import kotlin.math.sin

/**
 * Short variable-delay (feed-forward) flanger on an interleaved buffer (1 or 2 channels).
 *
 * - LFO sweeps the read delay around [baseDelayMs] ± [sweepMs], plus [manualMs] offset.
 * - [wet] in `[0,1]` mixes `dry + wet * (delayed - dry)`.
 * - [bypass]: output unchanged, but the ring buffer and LFO phase still advance so enabling is glitch-light.
 *
 * No heap allocations in [process].
 */
class StereoFlanger(
    private val channels: Int,
    capFrames: Int
) {
    private val ch: Int = channels.coerceIn(1, 2)
    private val cap: Int = capFrames.coerceAtLeast(64)

    private val buf: FloatArray = FloatArray(cap * ch)
    private var wf: Int = 0
    private var phase: Double = 0.0

    fun reset() {
        buf.fill(0f)
        wf = 0
        phase = 0.0
    }

    private fun wrapFrame(i: Int): Int {
        var v = i % cap
        if (v < 0) v += cap
        return v
    }

    private fun readAtFrame(framePos: Double, ci: Int): Float {
        val i0 = floor(framePos).toInt()
        val frac = (framePos - i0).toFloat()
        val i0w = wrapFrame(i0)
        val i1w = wrapFrame(i0 + 1)
        val s0 = buf[i0w * ch + ci]
        val s1 = buf[i1w * ch + ci]
        return s0 + frac * (s1 - s0)
    }

    /**
     * @param interleaved in/out; length >= frameCount * ch
     */
    fun process(
        interleaved: FloatArray,
        frameCount: Int,
        sampleRateHz: Int,
        lfoHz: Double,
        baseDelayMs: Float,
        sweepMs: Float,
        manualMs: Float,
        wet: Float,
        bypass: Boolean
    ) {
        if (frameCount <= 0 || sampleRateHz <= 0) return
        val sr = sampleRateHz.toFloat()
        val w = wet.coerceIn(0f, 1f)
        val inc = 2.0 * kotlin.math.PI * lfoHz / sampleRateHz.toDouble()

        val baseF = (baseDelayMs.coerceIn(0.5f, 12f) * sr / 1000f).toDouble()
        val sweepF = (sweepMs.coerceIn(0f, 8f) * sr / 1000f).toDouble()
        val manualF = (manualMs.coerceIn(-4f, 4f) * sr / 1000f).toDouble()

        val maxD = (baseF + sweepF + kotlin.math.abs(manualF)).coerceAtMost((cap - 4).toDouble())

        val c = ch
        for (f in 0 until frameCount) {
            val mod = sin(phase) * sweepF
            phase += inc
            if (phase >= kotlin.math.PI * 2.0) phase -= kotlin.math.PI * 2.0
            else if (phase < 0.0) phase += kotlin.math.PI * 2.0

            val delayNow = (baseF + mod + manualF).coerceIn(1.0, maxD.coerceAtLeast(1.0))

            for (ci in 0 until c) {
                val io = f * c + ci
                val dry = interleaved[io]
                buf[wf * c + ci] = dry
                if (bypass) {
                    continue
                }
                val readFrame = wf.toDouble() - delayNow
                val delayed = readAtFrame(readFrame, ci)
                interleaved[io] = dry + w * (delayed - dry)
            }
            wf = (wf + 1) % cap
        }
    }
}
