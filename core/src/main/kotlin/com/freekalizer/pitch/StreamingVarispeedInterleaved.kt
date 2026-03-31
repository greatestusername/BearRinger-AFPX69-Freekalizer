package com.freekalizer.pitch

import kotlin.math.floor

/**
 * Real-time **varispeed**: pitch and tempo scale together (linear interpolation over a ring buffer).
 * Same I/O length per callback as the phase-vocoder path; no allocations in [processInterleaved].
 *
 * [pitchRatio] is playback-speed multiplier vs dry (1 = unity): higher → brighter and faster through the stream.
 */
class StreamingVarispeedInterleaved(
    private val maxBurstFrames: Int = 512,
    private val ringCapFrames: Int = 32768
) {
    init {
        require(maxBurstFrames > 0 && ringCapFrames >= 4)
    }

    private val ring = FloatArray(ringCapFrames * 2)
    private var headFrame = 0
    private var nFrames = 0
    private var readPhase = 0.0
    private var holdL = 0f
    private var holdR = 0f

    fun reset() {
        headFrame = 0
        nFrames = 0
        readPhase = 0.0
        holdL = 0f
        holdR = 0f
        ring.fill(0f)
    }

    private fun frameOffset(indexFromHead: Int): Int {
        val idx = (headFrame + indexFromHead) % ringCapFrames
        return idx * 2
    }

    private fun enqueueFrame(L: Float, R: Float) {
        if (nFrames >= ringCapFrames) {
            headFrame = (headFrame + 1) % ringCapFrames
            nFrames--
            readPhase -= 1.0
            if (readPhase < 0.0) readPhase = 0.0
        }
        val tail = (headFrame + nFrames) % ringCapFrames
        val t = tail * 2
        ring[t] = L
        ring[t + 1] = R
        nFrames++
    }

    private fun popHeadFrame() {
        if (nFrames <= 0) return
        headFrame = (headFrame + 1) % ringCapFrames
        nFrames--
    }

    fun processInterleaved(
        src: FloatArray,
        dst: FloatArray,
        frameCount: Int,
        channels: Int,
        pitchRatio: Float
    ) {
        require(frameCount <= maxBurstFrames)
        val ch = channels.coerceIn(1, 2)
        val ratio = pitchRatio.toDouble().coerceIn(0.5, 1.5)

        for (f in 0 until frameCount) {
            val si = f * ch
            val L = src[si]
            val R = if (ch >= 2) src[si + 1] else L
            enqueueFrame(L, R)
        }

        for (f in 0 until frameCount) {
            val di = f * ch
            if (nFrames < 2) {
                val si = f * ch
                if (ch >= 2) {
                    holdL = src[si]
                    holdR = src[si + 1]
                    dst[di] = holdL
                    dst[di + 1] = holdR
                } else {
                    holdL = src[si]
                    dst[di] = holdL
                }
                continue
            }

            readPhase = readPhase.coerceIn(0.0, (nFrames - 1).toDouble() - 1e-7)
            val i0 = floor(readPhase).toInt()
            val frac = (readPhase - i0).toFloat()
            val b0 = frameOffset(i0)
            val b1 = frameOffset(i0 + 1)
            val l0 = ring[b0]
            val l1 = ring[b1]
            val r0 = ring[b0 + 1]
            val r1 = ring[b1 + 1]
            val oL = l0 + (l1 - l0) * frac
            val oR = r0 + (r1 - r0) * frac
            holdL = oL
            holdR = oR
            if (ch >= 2) {
                dst[di] = oL
                dst[di + 1] = oR
            } else {
                dst[di] = oL
            }

            readPhase += ratio
            while (readPhase >= 1.0 && nFrames > 0) {
                readPhase -= 1.0
                popHeadFrame()
            }
        }
    }
}
