package com.freekalizer.timing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lightweight onset-based BPM estimator for live input (E5-S1).
 *
 * Uses half-wave rectified energy flux + adaptive thresholding; maintains a ring buffer of onset
 * frame indices and derives BPM from median inter-onset interval. Intended for the audio callback:
 * no heap allocations in [ingestInterleavedInput].
 *
 * [confidence] (E5-S3) rises when several recent IOIs agree (low relative spread); it stays low on
 * ambiguous or non-pulsed material.
 */
class AutoBpmEstimator(
    private val minBpm: Double = 40.0,
    private val maxBpm: Double = 280.0
) {
    private var sampleRateHz: Int = 48_000

    private var prevInst: Float = 0f
    private var fluxEnv: Float = 0f
    private var cooldownFrames: Int = 0

    private var globalFrame: Long = 0L

    private val maxOnsets: Int = 32
    private val onsetFrames: LongArray = LongArray(maxOnsets)
    private var onsetWrite: Int = 0
    private var onsetCount: Int = 0

    private var lastBpm: Double = 120.0
    private var lastConfidence: Float = 0f

    fun configure(sampleRateHz: Int) {
        require(sampleRateHz > 0)
        this.sampleRateHz = sampleRateHz
    }

    fun reset() {
        prevInst = 0f
        fluxEnv = 0f
        cooldownFrames = 0
        globalFrame = 0L
        onsetWrite = 0
        onsetCount = 0
        lastBpm = 120.0
        lastConfidence = 0f
    }

    /**
     * Latest estimate; [confidence] is in `[0,1]`. When confidence is near zero, treat BPM as stale.
     */
    fun reading(): AutoBpmReading = AutoBpmReading(bpm = lastBpm, confidence = lastConfidence)

    /**
     * Feed one interleaved input slice (mono uses channel 0). Safe with [channels] in 1..2.
     */
    fun ingestInterleavedInput(input: FloatArray, channels: Int, frameCount: Int) {
        if (channels <= 0 || frameCount <= 0) return

        val sr = sampleRateHz
        val minIoiFrames = max((sr * (60.0 / maxBpm) * 0.45).toInt(), 64)
        val maxIoiFrames = (sr * (60.0 / minBpm) * 1.8).toInt()

        for (f in 0 until frameCount) {
            val x = input[f * channels]
            val inst = x * x
            val rawFlux = if (inst > prevInst) inst - prevInst else 0f
            prevInst = inst

            fluxEnv = FLUX_ENV_ALPHA * fluxEnv + (1f - FLUX_ENV_ALPHA) * rawFlux

            if (cooldownFrames > 0) {
                cooldownFrames--
            } else {
                // Slightly lower ratio than 2.2 so real program material (not just impulses) crosses more often.
                val thresh = max(fluxEnv * 1.45f, 1e-9f)
                if (rawFlux > thresh && rawFlux > 1e-9f) {
                    recordOnset(globalFrame)
                    cooldownFrames = minIoiFrames
                }
            }
            globalFrame++
        }

        recomputeFromOnsets(minIoiFrames = minIoiFrames, maxIoiFrames = maxIoiFrames)
    }

    private fun recordOnset(frame: Long) {
        onsetFrames[onsetWrite] = frame
        onsetWrite = (onsetWrite + 1) % maxOnsets
        if (onsetCount < maxOnsets) onsetCount++
    }

    private fun recomputeFromOnsets(minIoiFrames: Int, maxIoiFrames: Int) {
        if (onsetCount < 3) {
            lastConfidence = 0f
            return
        }

        val n = onsetCount
        val intervals = DoubleArray(n - 1)
        var k = 0
        for (i in 0 until n - 1) {
            val a = onsetChrono(i)
            val b = onsetChrono(i + 1)
            val d = (b - a).toInt()
            if (d in minIoiFrames..maxIoiFrames) {
                intervals[k++] = d.toDouble()
            }
        }
        if (k < 2) {
            lastConfidence = 0f
            return
        }

        val slice = intervals.copyOf(k)
        slice.sort()
        val median = slice[k / 2]
        if (median < 1.0) {
            lastConfidence = 0f
            return
        }

        val bpm = (60.0 * sampleRateHz) / median
        val clamped = bpm.coerceIn(minBpm, maxBpm)

        var spreadSum = 0.0
        for (i in 0 until k) {
            val bi = (60.0 * sampleRateHz) / slice[i].coerceAtLeast(1.0)
            spreadSum += abs(bi - clamped)
        }
        val meanDev = spreadSum / k
        val conf = (1.0 - (meanDev / 40.0).coerceIn(0.0, 1.0)).toFloat()

        lastBpm = clamped
        lastConfidence = (conf * sqrt((k / 8.0).coerceIn(0.0, 1.0))).toFloat().coerceIn(0f, 1f)
    }

    /** Onsets in chronological order (oldest first). */
    private fun onsetChrono(i: Int): Long {
        require(i in 0 until onsetCount)
        return if (onsetCount < maxOnsets) {
            onsetFrames[i]
        } else {
            onsetFrames[(onsetWrite + i) % maxOnsets]
        }
    }

    private companion object {
        /** Faster tracking than 0.985 so the envelope can follow musical transients. */
        private const val FLUX_ENV_ALPHA: Float = 0.968f
    }
}

data class AutoBpmReading(
    val bpm: Double,
    /** 0 = no reliable lock; tablet follow uses ~0.07 once onset stream stabilizes. */
    val confidence: Float
)
