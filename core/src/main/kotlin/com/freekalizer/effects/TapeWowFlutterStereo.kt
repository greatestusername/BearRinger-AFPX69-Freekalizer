package com.freekalizer.effects

import kotlin.math.PI
import kotlin.math.sin

/**
 * Stereo delay-line modulation: slow **wow** + faster **flutter** for tape-like pitch wander.
 * **No allocations** in [processReplaceInterleaved].
 */
class TapeWowFlutterStereo(
    sampleRateHz: Int,
    maxDelayMs: Float = 12f
) {
    private val sr = sampleRateHz.coerceAtLeast(8000).toDouble()
    private val cap = (sampleRateHz * maxDelayMs / 1000f).toInt().coerceAtLeast(64) + 8
    private val bufL = FloatArray(cap)
    private val bufR = FloatArray(cap)
    private var w = 0

    private var wowPhase = 0.0
    private var flutterPhase = 0.0

    fun reset() {
        bufL.fill(0f)
        bufR.fill(0f)
        w = 0
        wowPhase = 0.0
        flutterPhase = 0.0
    }

    fun processReplaceInterleaved(
        interleaved: FloatArray,
        frameCount: Int,
        channels: Int,
        enabled: Boolean,
        wowNorm: Float,
        flutterNorm: Float,
        mixNorm: Float
    ) {
        if (!enabled || frameCount <= 0 || channels <= 0) return
        val mix = mixNorm.coerceIn(0f, 1f)
        if (mix < 1e-5f) return
        val wowMs = 1.0f + wowNorm.coerceIn(0f, 1f) * 4.5f
        val flutterMs = flutterNorm.coerceIn(0f, 1f) * 0.9f
        val baseDelaySmps = 2.0 * sr / 1000.0
        val c = channels.coerceIn(1, 2)

        for (f in 0 until frameCount) {
            val base = f * c
            val inL = interleaved[base]
            val inR = if (c >= 2) interleaved[base + 1] else inL

            bufL[w] = inL
            bufR[w] = inR

            wowPhase += 2.0 * PI * 0.42 / sr
            if (wowPhase >= 2.0 * PI) wowPhase -= 2.0 * PI
            flutterPhase += 2.0 * PI * 11.0 / sr
            if (flutterPhase >= 2.0 * PI) flutterPhase -= 2.0 * PI

            val modSmps = baseDelaySmps +
                sin(wowPhase) * (wowMs * sr / 1000.0) +
                sin(flutterPhase) * (flutterMs * sr / 1000.0)
            val d = modSmps.coerceIn(1.0, (cap - 3).toDouble())

            val rf = w.toDouble() - d
            val ri = kotlin.math.floor(rf).toInt()
            val frac = (rf - ri).toFloat()
            val i0 = ((ri % cap) + cap) % cap
            val i1 = (i0 + 1) % cap

            val wetL = bufL[i0] * (1f - frac) + bufL[i1] * frac
            val wetR = bufR[i0] * (1f - frac) + bufR[i1] * frac

            interleaved[base] = inL + mix * (wetL - inL)
            if (c >= 2) {
                interleaved[base + 1] = inR + mix * (wetR - inR)
            }

            w = (w + 1) % cap
        }
    }
}
