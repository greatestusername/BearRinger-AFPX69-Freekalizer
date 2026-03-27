package com.freekalizer.timing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
 *
 * Inter-onset intervals are **octave-folded** into the configured BPM range before taking the median tempo,
 * so dense 16th onsets and quarter-note kicks vote for the same BPM (see [foldBpmFromIoiFrames]).
 */
class AutoBpmEstimator(
    private val minBpm: Double = 40.0,
    private val maxBpm: Double = 280.0
) {
    private var sampleRateHz: Int = 48_000

    private var prevInst: Float = 0f
    /** First-order pre-emphasis state (mono downmix); boosts attacks on dense program material. */
    private var emphasisPrevMono: Float = 0f
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
        emphasisPrevMono = 0f
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
     * Feed one interleaved slice. Uses **mono downmix** when [channels] &gt; 1 so kick transients
     * in either channel (common on stereo loops) drive the flux path, not only “left”.
     */
    fun ingestInterleavedInput(
        input: FloatArray,
        channels: Int,
        frameCount: Int,
        inputStartFrame: Int = 0
    ) {
        if (channels <= 0 || frameCount <= 0) return

        val sr = sampleRateHz
        val minIoiFrames = max((sr * (60.0 / maxBpm) * 0.45).toInt(), 64)
        val maxIoiFrames = (sr * (60.0 / minBpm) * 1.8).toInt()

        for (f in 0 until frameCount) {
            val xMono = monoDownmixFrame(input, (inputStartFrame + f) * channels, channels)
            // Pre-emphasis: helps when RMS stays high but only hits have sharp attacks (compressed loops).
            val x = xMono - PRE_EMPHASIS * emphasisPrevMono
            emphasisPrevMono = xMono
            val inst = x * x
            val rawFlux = if (inst > prevInst) inst - prevInst else 0f
            prevInst = inst

            fluxEnv = FLUX_ENV_ALPHA * fluxEnv + (1f - FLUX_ENV_ALPHA) * rawFlux

            if (cooldownFrames > 0) {
                cooldownFrames--
            } else {
                val thresh = max(fluxEnv * FLUX_THRESH_RATIO, 1e-9f)
                if (rawFlux > thresh && rawFlux > 1e-9f) {
                    recordOnset(globalFrame)
                    cooldownFrames = minIoiFrames
                }
            }
            globalFrame++
        }

        recomputeFromOnsets(minIoiFrames = minIoiFrames, maxIoiFrames = maxIoiFrames)
    }

    /** Mean of interleaved channels for this frame (real-time safe). */
    private fun monoDownmixFrame(input: FloatArray, baseIndex: Int, channels: Int): Float {
        var s = 0f
        for (c in 0 until channels) {
            s += input[baseIndex + c]
        }
        return s / channels
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

        // Fold each IOI into [minBpm,maxBpm] by halving/doubling tempo so 16ths vs quarters agree (real drum loops).
        val foldedBpm = DoubleArray(k)
        var kf = 0
        for (i in 0 until k) {
            val fb = foldBpmFromIoiFrames(intervals[i]) ?: continue
            foldedBpm[kf++] = fb
        }
        if (kf < 2) {
            lastConfidence = 0f
            return
        }
        val bpmSlice = foldedBpm.copyOf(kf)
        bpmSlice.sort()
        val medianBpm = bpmSlice[kf / 2]
        if (!medianBpm.isFinite()) {
            lastConfidence = 0f
            return
        }

        var spreadSum = 0.0
        for (i in 0 until kf) {
            spreadSum += abs(bpmSlice[i] - medianBpm)
        }
        val meanDev = spreadSum / kf
        val conf = (1.0 - (meanDev / 40.0).coerceIn(0.0, 1.0)).toFloat()

        lastBpm = medianBpm.coerceIn(minBpm, maxBpm)
        lastConfidence = (conf * sqrt((kf / 5.0).coerceIn(0.0, 1.0))).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Maps one inter-onset interval (frames) to a musically plausible BPM by octave folding.
     * (Otherwise hat 16ths imply 480 BPM, clamp to 280, and confidence collapses on real loops.)
     */
    private fun foldBpmFromIoiFrames(dFrames: Double): Double? {
        if (dFrames < 1.0) return null
        var bpm = 60.0 * sampleRateHz / dFrames
        if (!bpm.isFinite() || bpm <= 0.0) return null
        var guard = 0
        while (bpm > maxBpm && guard < 16) {
            bpm /= 2.0
            guard++
        }
        guard = 0
        while (bpm < minBpm && guard < 16) {
            bpm *= 2.0
            guard++
        }
        return bpm.coerceIn(minBpm, maxBpm)
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

    companion object {
        /** Faster tracking than 0.985 — envelope follows musical flux quickly enough to adapt threshold. */
        private const val FLUX_ENV_ALPHA: Float = 0.968f

        /** ~0.94 standard first-order emphasis; lifts transients vs steady-state in loud loops. */
        private const val PRE_EMPHASIS: Float = 0.94f

        /** Adaptive threshold = fluxEnv × this (lower → more onsets on real drums/electronic). */
        private const val FLUX_THRESH_RATIO: Float = 1.28f

        /** Chunk size for offline full-buffer passes (UI thread; not used on audio callback hot path). */
        private const val OFFLINE_INGEST_CHUNK_FRAMES: Int = 2048

        /**
         * Runs the same onset logic as live [ingestInterleavedInput] over an entire buffer (e.g. loaded WAV).
         * Call from a non-audio thread for clips; very short or non-rhythmic material may return low [AutoBpmReading.confidence].
         */
        fun estimateFromInterleavedBuffer(
            pcm: FloatArray,
            channels: Int,
            sampleRateHz: Int
        ): AutoBpmReading {
            require(channels > 0)
            require(sampleRateHz > 0)
            val frames = pcm.size / channels
            if (frames <= 0) return AutoBpmReading(bpm = 120.0, confidence = 0f)
            val est = AutoBpmEstimator()
            est.configure(sampleRateHz)
            est.reset()
            var offset = 0
            while (offset < frames) {
                val n = min(OFFLINE_INGEST_CHUNK_FRAMES, frames - offset)
                est.ingestInterleavedInput(pcm, channels, n, offset)
                offset += n
            }
            return est.reading()
        }
    }
}

data class AutoBpmReading(
    val bpm: Double,
    /** 0 = no reliable lock; follow applies in app when this exceeds ~0.035 once onsets stabilize. */
    val confidence: Float
)
