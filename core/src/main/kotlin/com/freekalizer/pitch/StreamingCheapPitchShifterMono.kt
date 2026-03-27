package com.freekalizer.pitch

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Streaming **phase-vocoder** pitch shifter: Hann STFT, per-bin phase propagation (instantaneous
 * frequency estimate), overlap-add synthesis. Better behaved than raw spectrum bin-stretching on
 * drum/loop material. **No allocations** in [processReplace].
 *
 * Default frame/hop are 1024/256; the tablet uses 4096/256 for master pitch (high overlap).
 */
class StreamingCheapPitchShifterMono(
    private val fftSize: Int = 1024,
    private val hop: Int = 256
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) { "fftSize must be power of 2" }
        require(hop in 1 until fftSize) { "hop must be in 1..fftSize-1" }
    }

    private val nh = fftSize / 2
    private val twopi = (2.0 * PI).toFloat()

    private val win = FloatArray(fftSize)
    private val inFifo = FloatArray(fftSize)
    private var inFifoCount = 0

    private val fftTimeRe = FloatArray(fftSize)
    private val fftTimeIm = FloatArray(fftSize)

    private val lastPhaseIn = FloatArray(nh + 1)
    private val sumPhaseOut = FloatArray(nh + 1)
    /** Eased magnitude per bin — tames vertical “zipper” / metallic chatter between frames. */
    private val magSmoothed = FloatArray(nh + 1)

    private var pvFrameIndex: Int = 0

    private val outAccum = FloatArray(fftSize)

    private val pending = FloatArray(hop * 128)
    private var pendingHead = 0
    private var pendingTail = 0
    private var olaScale = 1f

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
        inFifo.fill(0f)
        inFifoCount = 0
        fftTimeRe.fill(0f)
        fftTimeIm.fill(0f)
        lastPhaseIn.fill(0f)
        sumPhaseOut.fill(0f)
        magSmoothed.fill(0f)
        pvFrameIndex = 0
        outAccum.fill(0f)
        pending.fill(0f)
        pendingHead = 0
        pendingTail = 0
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
        var inCursor = 0
        var outCursor = 0
        val nf = fftSize.toFloat()
        val hopf = hop.toFloat()
        while (outCursor < frameCount) {
            while (pendingHead != pendingTail && outCursor < frameCount) {
                output[outputOffset + outCursor++] = pending[pendingHead]
                pendingHead = (pendingHead + 1) % pending.size
            }
            if (outCursor >= frameCount) break

            while (inFifoCount < fftSize && inCursor < frameCount) {
                inFifo[inFifoCount++] = input[inputOffset + inCursor++]
            }
            while (inFifoCount < fftSize) {
                inFifo[inFifoCount++] = 0f
            }

            synthesizeOneFrame(ratio, nf, hopf)

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

            for (j in 0 until fftSize - hop) {
                inFifo[j] = inFifo[j + hop]
            }
            inFifoCount = fftSize - hop
            while (inFifoCount < fftSize && inCursor < frameCount) {
                inFifo[inFifoCount++] = input[inputOffset + inCursor++]
            }
            while (inFifoCount < fftSize) {
                inFifo[inFifoCount++] = 0f
            }
        }
    }

    private companion object {
        /** Higher = smoother spectrum, softer transients (more “analog,” less zipper). */
        private const val STFT_MAG_SMOOTH: Float = 0.88f
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
    }
}
