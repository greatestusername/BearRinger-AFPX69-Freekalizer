package com.freekalizer.pitch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Streaming **phase-vocoder** pitch shifter: Hann STFT, per-bin phase propagation, overlap-add.
 * **No allocations** in [processReplace].
 *
 * Input is fed through an internal **ring buffer** so analysis windows always contain **real**
 * consecutive samples. (Padding short callbacks with zeros up to [fftSize] — inevitable with naive
 * STFT — destroys the PV and sounds like noise on typical Android burst sizes ≈ 96–256 frames.)
 *
 * Default **2048 / 256** balances quality vs priming latency (~43 ms @ 48 kHz before first shifted
 * output appears).
 */
class StreamingCheapPitchShifterMono(
    private val fftSize: Int = 2048,
    private val hop: Int = 256
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) { "fftSize must be power of 2" }
        require(hop in 1 until fftSize) { "hop must be in 1..fftSize-1" }
    }

    private val nh = fftSize / 2
    private val twopi = (2.0 * PI).toFloat()

    private val win = FloatArray(fftSize)
    /** Sliding analysis window (always copied from [inputRing] when a frame is formed). */
    private val inFifo = FloatArray(fftSize)

    private val fftTimeRe = FloatArray(fftSize)
    private val fftTimeIm = FloatArray(fftSize)

    private val lastPhaseIn = FloatArray(nh + 1)
    private val sumPhaseOut = FloatArray(nh + 1)
    private val magSmoothed = FloatArray(nh + 1)

    private var pvFrameIndex: Int = 0

    /** Frames fully synthesized (unity or shifted); used to avoid injecting dry mid-stream when the ring dips below [fftSize]. */
    private var synthFramesCompleted: Int = 0

    private val outAccum = FloatArray(fftSize)

    private val pending = FloatArray(hop * 256)
    private var pendingHead = 0
    private var pendingTail = 0
    private var olaScale = 1f

    /** ~1.3 s @ 48 kHz — headroom so bursts never force drops. */
    private val inputRing = FloatArray(64_000)
    private var ringHead = 0
    private var ringSize = 0

    /** Fills output when the PV has not primed yet (avoids zeros / zipper). */
    private var holdOutput = 0f

    init {
        val denom = (fftSize - 1).coerceAtLeast(1)
        for (i in 0 until fftSize) {
            win[i] = (0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / denom))).toFloat()
        }
        val cola = FloatArray(fftSize)
        var offset = 0
        while (offset < fftSize) {
            for (i in 0 until fftSize) {
                val idx = (i + offset) % fftSize
                val w = win[i]
                cola[idx] += (w * w)
            }
            offset += hop
        }
        var maxCola = 0f
        for (v in cola) {
            if (v > maxCola) maxCola = v
        }
        olaScale = if (maxCola > 1e-6f) 1f / maxCola else 1f
    }

    fun reset() {
        ringHead = 0
        ringSize = 0
        fftTimeRe.fill(0f)
        fftTimeIm.fill(0f)
        lastPhaseIn.fill(0f)
        sumPhaseOut.fill(0f)
        magSmoothed.fill(0f)
        pvFrameIndex = 0
        synthFramesCompleted = 0
        outAccum.fill(0f)
        pending.fill(0f)
        pendingHead = 0
        pendingTail = 0
        holdOutput = 0f
    }

    private fun ringEnqueue(samples: FloatArray, offset: Int, length: Int) {
        val cap = inputRing.size
        for (n in 0 until length) {
            if (ringSize >= cap) {
                ringHead = (ringHead + 1) % cap
                ringSize--
            }
            val t = (ringHead + ringSize) % cap
            inputRing[t] = samples[offset + n]
            ringSize++
        }
    }

    private fun ringCopyToFifo(): Boolean {
        if (ringSize < fftSize) return false
        var idx = ringHead
        val cap = inputRing.size
        for (i in 0 until fftSize) {
            inFifo[i] = inputRing[idx]
            idx = (idx + 1) % cap
        }
        return true
    }

    private fun ringAdvanceHop() {
        val cap = inputRing.size
        ringHead = (ringHead + hop) % cap
        ringSize -= hop
    }

    fun processReplace(
        input: FloatArray,
        inputOffset: Int,
        output: FloatArray,
        outputOffset: Int,
        frameCount: Int,
        pitchRatio: Float
    ) {
        val ratio = pitchRatio.coerceIn(0.5f, 1.5f)
        val nf = fftSize.toFloat()
        val hopf = hop.toFloat()

        ringEnqueue(input, inputOffset, frameCount)

        var outCursor = 0
        while (outCursor < frameCount) {
            while (pendingHead != pendingTail && outCursor < frameCount) {
                val s = pending[pendingHead]
                pendingHead = (pendingHead + 1) % pending.size
                holdOutput = s
                output[outputOffset + outCursor++] = s
            }
            if (outCursor >= frameCount) break

            if (ringSize >= fftSize) {
                ringCopyToFifo()
                synthesizeOneFrame(ratio, nf, hopf)
                ringAdvanceHop()

                for (j in 0 until hop) {
                    val next = (pendingTail + 1) % pending.size
                    if (next == pendingHead) break
                    pending[pendingTail] = outAccum[j] * olaScale
                    pendingTail = next
                }
                for (j in 0 until fftSize - hop) {
                    outAccum[j] = outAccum[j + hop]
                }
                for (j in fftSize - hop until fftSize) {
                    outAccum[j] = 0f
                }
            } else {
                // Ring not full: before first synth, follow dry (priming). After that, hold last sample — dry here
                // would splice input against the PV stream and sound like noise when burst+hop leaves ringSize < fftSize.
                if (synthFramesCompleted == 0) {
                    val di = minOf(outCursor, frameCount - 1).coerceAtLeast(0)
                    val dry = input[inputOffset + di]
                    holdOutput = dry
                    output[outputOffset + outCursor++] = dry
                } else {
                    output[outputOffset + outCursor++] = holdOutput
                }
            }
        }
    }

    private companion object {
        private const val STFT_MAG_SMOOTH: Float = 0.72f
    }

    private fun principalArg(phase: Float): Float {
        var p = phase
        while (p > PI.toFloat()) p -= twopi
        while (p < -PI.toFloat()) p += twopi
        return p
    }

    private fun synthesizeOneFrame(pitchRatio: Float, nf: Float, hopf: Float) {
        for (n in 0 until fftSize) {
            fftTimeRe[n] = inFifo[n] * win[n]
            fftTimeIm[n] = 0f
        }
        ComplexFft.transform(fftTimeRe, fftTimeIm, fftSize, inverse = false)

        if (abs(pitchRatio - 1f) < 1e-4f) {
            ComplexFft.transform(fftTimeRe, fftTimeIm, fftSize, inverse = true)
        } else {
            val first = pvFrameIndex == 0
            for (k in 1 until nh) {
                val reK = fftTimeRe[k]
                val imK = fftTimeIm[k]
                val magRaw = hypot(reK, imK)
                val phase = atan2(imK, reK)
                if (first) {
                    lastPhaseIn[k] = phase
                    sumPhaseOut[k] = phase
                    magSmoothed[k] = magRaw
                } else {
                    val expected = twopi * k * hopf / nf
                    var delta = phase - lastPhaseIn[k]
                    lastPhaseIn[k] = phase
                    delta = principalArg(delta - expected)
                    val omegaInst = twopi * k / nf + delta / hopf
                    sumPhaseOut[k] += omegaInst * pitchRatio * hopf
                    magSmoothed[k] = magSmoothed[k] * STFT_MAG_SMOOTH + magRaw * (1f - STFT_MAG_SMOOTH)
                }
                val mag = magSmoothed[k]
                fftTimeRe[k] = mag * cos(sumPhaseOut[k])
                fftTimeIm[k] = mag * sin(sumPhaseOut[k])
            }
            pvFrameIndex++

            val dcMag = hypot(fftTimeRe[0], fftTimeIm[0])
            fftTimeRe[0] = dcMag
            fftTimeIm[0] = 0f
            fftTimeRe[nh] = 0f
            fftTimeIm[nh] = 0f

            for (k in 1 until nh) {
                fftTimeRe[fftSize - k] = fftTimeRe[k]
                fftTimeIm[fftSize - k] = -fftTimeIm[k]
            }

            ComplexFft.transform(fftTimeRe, fftTimeIm, fftSize, inverse = true)
        }

        for (n in 0 until fftSize) {
            outAccum[n] += fftTimeRe[n] * win[n]
        }
        synthFramesCompleted++
    }
}
