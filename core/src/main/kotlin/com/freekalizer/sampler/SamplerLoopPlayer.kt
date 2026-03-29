package com.freekalizer.sampler

import com.freekalizer.pitch.PitchKnobMath
import kotlin.math.floor
import kotlin.math.min

/**
 * Continuous interleaved PCM loop playback; mixes into an existing output buffer (e.g. after dry monitoring).
 * Real-time safe: no allocations in [mixInto] / [mixShotInto].
 *
 * **Sample pitch** (-50%..+50%) is **pitch+tempo** via fractional read speed and linear interpolation.
 */
class SamplerLoopPlayer {
    private var pcm: FloatArray = FloatArray(0)
    private var sampleChannels: Int = 0
    private var loopPhase: Double = 0.0
    @Volatile
    private var looping: Boolean = false

    /** User intent; applied when a buffer exists and after each [load]. */
    @Volatile
    private var reverseRequested: Boolean = false

    /** When true, loop and SHOT advance through the sample backward (wraps). */
    @Volatile
    private var reversePlayback: Boolean = false

    /** Momentary SHOT: gated playback on a separate read pointer; loops while held. */
    @Volatile
    private var shotPressed: Boolean = false

    @Volatile
    private var shotRewind: Boolean = false

    private var shotPhase: Double = 0.0

    /** Pitch+tempo playback speed; 1 = normal, 0.5..1.5 from knob. */
    @Volatile
    private var samplePitchMultiplier: Float = 1f

    /**
     * Vinyl scratch: while [vinylScratchActive], sample **motor is off**; only hand rate (slewing toward
     * [vinylRateTarget]) moves phase under the playhead. When inactive, normal [samplePitchMultiplier] motor.
     */
    @Volatile
    private var vinylScratchActive: Boolean = false

    @Volatile
    private var vinylRateTarget: Float = 0f

    @Volatile
    private var vinylRateRange: Float = 110f

    @Volatile
    private var vinylSampleRate: Int = 48_000

    @Volatile
    private var vinylMaxSamplesPerSec: Float = 8800f

    /**
     * Published from the audio thread after each [mixInto] mix pass for UI metering (best-effort).
     * `-1` when loop playback is off or there is no buffer.
     */
    @Volatile
    var publishedLoopPlayheadFrame: Int = -1
        private set

    /**
     * Fractional loop position in `[0, 1)` from wrapped phase (smooth vs integer [publishedLoopPlayheadFrame]).
     * `-1f` when invalid / not looping.
     */
    @Volatile
    var publishedLoopPlayheadFraction: Float = -1f
        private set

    /**
     * Published from the audio thread after [mixShotInto] while SHOT is held; `-1` when SHOT is not active.
     */
    @Volatile
    var publishedShotPlayheadFrame: Int = -1
        private set

    @Volatile
    var publishedShotPlayheadFraction: Float = -1f
        private set

    fun load(buffer: SamplerBuffer) {
        pcm = buffer.pcm
        sampleChannels = buffer.channels
        loopPhase = 0.0
        shotPhase = 0.0
        shotPressed = false
        shotRewind = false
        reversePlayback = reverseRequested
        publishedLoopPlayheadFrame = -1
        publishedLoopPlayheadFraction = -1f
        publishedShotPlayheadFrame = -1
        publishedShotPlayheadFraction = -1f
        vinylScratchActive = false
        vinylRateTarget = 0f
    }

    fun clear() {
        pcm = FloatArray(0)
        sampleChannels = 0
        loopPhase = 0.0
        looping = false
        shotPhase = 0.0
        shotPressed = false
        shotRewind = false
        reverseRequested = false
        reversePlayback = false
        samplePitchMultiplier = 1f
        publishedLoopPlayheadFrame = -1
        publishedLoopPlayheadFraction = -1f
        publishedShotPlayheadFrame = -1
        publishedShotPlayheadFraction = -1f
        vinylScratchActive = false
        vinylRateTarget = 0f
    }

    fun hasBuffer(): Boolean = pcm.isNotEmpty() && sampleChannels > 0

    fun setLooping(enabled: Boolean) {
        looping = enabled && hasBuffer()
        if (!looping) return
        if (frameCount() <= 0) looping = false
    }

    fun isLooping(): Boolean = looping

    /**
     * [down] true while the user holds SHOT. Each new press rewinds the shot voice to the sample start.
     * Real-time safe for the audio thread to read [shotPressed] / [shotRewind].
     */
    fun setShotPressed(down: Boolean) {
        if (!hasBuffer()) {
            shotPressed = false
            return
        }
        if (down) {
            shotRewind = true
            /** One-shot takes over; stop loop playback until PLAY is pressed again. */
            looping = false
        }
        shotPressed = down
    }

    fun isShotActive(): Boolean = shotPressed

    fun setReversePlayback(enabled: Boolean) {
        reverseRequested = enabled
        reversePlayback = enabled && hasBuffer()
    }

    fun isReversePlayback(): Boolean = reversePlayback

    /**
     * **Sample pitch** knob: `-50%..+50%` → playback speed `0.5×..1.5×` (pitch + tempo).
     */
    fun setSamplePitchPercent(percent: Float) {
        samplePitchMultiplier = PitchKnobMath.toPlaybackSpeedMultiplier(percent)
    }

    fun samplePitchPercent(): Float {
        val m = samplePitchMultiplier
        return (m - 1f) * 100f
    }

    /**
     * Audio thread: configure vinyl scratch before [mixInto] / [mixShotInto] each burst.
     * [rateTarget] / [rateRange] define normalized direction/speed (same semantics as engine scratch pad).
     * [slewPerFrame] is ignored (kept for call-site stability); scrub follows [rateTarget] directly.
     */
    fun configureVinylScratch(
        active: Boolean,
        rateTarget: Float,
        rateRange: Float,
        @Suppress("UNUSED_PARAMETER") slewPerFrame: Float,
        sampleRateHz: Int,
        maxSamplesPerSec: Float
    ) {
        vinylScratchActive = active
        vinylRateTarget = rateTarget
        vinylRateRange = rateRange.coerceAtLeast(1f)
        vinylSampleRate = sampleRateHz.coerceAtLeast(1)
        vinylMaxSamplesPerSec = maxSamplesPerSec.coerceAtLeast(1f)
    }

    private fun frameCount(): Int {
        if (sampleChannels <= 0) return 0
        return pcm.size / sampleChannels
    }

    private fun normalizeFrameIndex(idx: Int, totalFrames: Int): Int {
        if (totalFrames <= 0) return 0
        var x = idx % totalFrames
        if (x < 0) x += totalFrames
        return x
    }

    private fun wrapPhase(phase: Double, totalFrames: Int): Double {
        if (totalFrames <= 0) return 0.0
        var p = phase % totalFrames
        if (p < 0) p += totalFrames
        return p
    }

    private fun phaseToInterpIndices(phase: Double, totalFrames: Int): Triple<Int, Int, Float> {
        val p = wrapPhase(phase, totalFrames)
        val fl = floor(p).toInt()
        val i0 = ((fl % totalFrames) + totalFrames) % totalFrames
        val i1 = (i0 + 1) % totalFrames
        val frac = (p - fl).toFloat()
        return Triple(i0, i1, frac)
    }

    private fun readInterpolatedFrame(basePhase: Double, totalFrames: Int, dst: FloatArray, dstChannels: Int) {
        val (i0, i1, frac) = phaseToInterpIndices(basePhase, totalFrames)
        val ch = sampleChannels
        val b0 = i0 * ch
        val b1 = i1 * ch
        for (oc in 0 until dstChannels) {
            val sc = if (ch == 1) 0 else min(oc, ch - 1)
            val s0 = pcm[b0 + sc]
            val s1 = pcm[b1 + sc]
            dst[oc] = s0 + (s1 - s0) * frac
        }
    }

    private fun advancePhase(phase: Double, totalFrames: Int, reverse: Boolean): Double {
        val step = samplePitchMultiplier.toDouble()
        val delta = if (reverse) -step else step
        return wrapPhase(phase + delta, totalFrames)
    }

    /**
     * After each output sample: with finger on scratch pad, **motor off** — only hand-slew rate moves
     * phase (up = forward in buffer, down = back). Finger up: varispeed motor only.
     */
    private fun advancePhaseAfterOutputSample(phase: Double, totalFrames: Int, rev: Boolean): Double {
        if (!vinylScratchActive) {
            return advancePhase(phase, totalFrames, rev)
        }
        // Direct mapping: engine [scratchRateTarget] is already in ±rateRange units; no extra slew here
        // (UI uses VelocityTracker so the target is stable between touch events).
        val range = vinylRateRange.toDouble().coerceAtLeast(1.0)
        val norm = (vinylRateTarget.toDouble() / range).coerceIn(-1.0, 1.0)
        val sr = vinylSampleRate.toDouble().coerceAtLeast(1.0)
        val delta = norm * (vinylMaxSamplesPerSec.toDouble() / sr)
        return wrapPhase(phase + delta, totalFrames)
    }

    private fun publishLoopPlayhead(phase: Double, totalFrames: Int) {
        publishedLoopPlayheadFrame = normalizeFrameIndex(floor(phase).toInt(), totalFrames)
        publishedLoopPlayheadFraction =
            (wrapPhase(phase, totalFrames) / totalFrames.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun clearPublishedLoop() {
        publishedLoopPlayheadFrame = -1
        publishedLoopPlayheadFraction = -1f
    }

    private fun publishShotPlayhead(phase: Double, totalFrames: Int) {
        publishedShotPlayheadFrame = normalizeFrameIndex(floor(phase).toInt(), totalFrames)
        publishedShotPlayheadFraction =
            (wrapPhase(phase, totalFrames) / totalFrames.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun clearPublishedShot() {
        publishedShotPlayheadFrame = -1
        publishedShotPlayheadFraction = -1f
    }

    private val frameScratch = FloatArray(8)

    /**
     * Adds loop samples into [output] for [frameCount] frames. [output] is interleaved with [outputChannels].
     */
    fun mixInto(output: FloatArray, outputChannels: Int, frameCount: Int) {
        if (!looping) {
            clearPublishedLoop()
            return
        }
        val totalFrames = frameCount()
        if (totalFrames <= 0 || outputChannels <= 0) {
            clearPublishedLoop()
            return
        }
        val outNeeded = frameCount * outputChannels
        if (output.size < outNeeded) {
            clearPublishedLoop()
            return
        }

        if (frameScratch.size < outputChannels) {
            clearPublishedLoop()
            return
        }

        var phase = loopPhase
        val rev = reversePlayback
        for (f in 0 until frameCount) {
            readInterpolatedFrame(phase, totalFrames, frameScratch, outputChannels)
            val dstBase = f * outputChannels
            for (oc in 0 until outputChannels) {
                output[dstBase + oc] += frameScratch[oc]
            }
            phase = advancePhaseAfterOutputSample(phase, totalFrames, rev)
        }
        loopPhase = phase
        publishLoopPlayhead(loopPhase, totalFrames)
    }

    /**
     * Adds SHOT-gated samples (same buffer as loop; independent read position, wraps while held).
     */
    fun mixShotInto(output: FloatArray, outputChannels: Int, frameCount: Int) {
        if (!shotPressed) {
            clearPublishedShot()
            return
        }
        val totalFrames = frameCount()
        if (totalFrames <= 0 || outputChannels <= 0) {
            clearPublishedShot()
            return
        }
        val outNeeded = frameCount * outputChannels
        if (output.size < outNeeded || frameScratch.size < outputChannels) {
            clearPublishedShot()
            return
        }

        if (shotRewind) {
            shotPhase = if (reversePlayback) (totalFrames - 1).toDouble() else 0.0
            shotRewind = false
        }

        var phase = shotPhase
        val rev = reversePlayback
        for (f in 0 until frameCount) {
            readInterpolatedFrame(phase, totalFrames, frameScratch, outputChannels)
            val dstBase = f * outputChannels
            for (oc in 0 until outputChannels) {
                output[dstBase + oc] += frameScratch[oc]
            }
            phase = advancePhaseAfterOutputSample(phase, totalFrames, rev)
        }
        shotPhase = phase
        publishShotPlayhead(shotPhase, totalFrames)
    }
}
