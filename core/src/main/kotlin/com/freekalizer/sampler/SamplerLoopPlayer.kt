package com.freekalizer.sampler

import kotlin.math.min

/**
 * Continuous interleaved PCM loop playback; mixes into an existing output buffer (e.g. after dry monitoring).
 * Real-time safe: no allocations in [mixInto].
 */
class SamplerLoopPlayer {
    private var pcm: FloatArray = FloatArray(0)
    private var sampleChannels: Int = 0
    private var readFrame: Int = 0
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

    private var shotReadFrame: Int = 0

    fun load(buffer: SamplerBuffer) {
        pcm = buffer.pcm
        sampleChannels = buffer.channels
        readFrame = 0
        shotReadFrame = 0
        shotPressed = false
        shotRewind = false
        reversePlayback = reverseRequested
    }

    fun clear() {
        pcm = FloatArray(0)
        sampleChannels = 0
        readFrame = 0
        looping = false
        shotReadFrame = 0
        shotPressed = false
        shotRewind = false
        reverseRequested = false
        reversePlayback = false
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
        }
        shotPressed = down
    }

    fun isShotActive(): Boolean = shotPressed

    fun setReversePlayback(enabled: Boolean) {
        reverseRequested = enabled
        reversePlayback = enabled && hasBuffer()
    }

    fun isReversePlayback(): Boolean = reversePlayback

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

    /**
     * Adds loop samples into [output] for [frameCount] frames. [output] is interleaved with [outputChannels].
     */
    fun mixInto(output: FloatArray, outputChannels: Int, frameCount: Int) {
        if (!looping) return
        val totalFrames = frameCount()
        if (totalFrames <= 0 || outputChannels <= 0) return
        val outNeeded = frameCount * outputChannels
        if (output.size < outNeeded) return

        var pos = normalizeFrameIndex(readFrame, totalFrames)
        val rev = reversePlayback

        for (f in 0 until frameCount) {
            val srcBase = pos * sampleChannels
            val dstBase = f * outputChannels
            for (oc in 0 until outputChannels) {
                val sc = if (sampleChannels == 1) 0 else min(oc, sampleChannels - 1)
                output[dstBase + oc] += pcm[srcBase + sc]
            }
            if (rev) {
                pos--
                if (pos < 0) pos = totalFrames - 1
            } else {
                pos++
                if (pos >= totalFrames) pos = 0
            }
        }
        readFrame = pos
    }

    /**
     * Adds SHOT-gated samples (same buffer as loop; independent read position, wraps while held).
     */
    fun mixShotInto(output: FloatArray, outputChannels: Int, frameCount: Int) {
        if (!shotPressed) return
        val totalFrames = frameCount()
        if (totalFrames <= 0 || outputChannels <= 0) return
        val outNeeded = frameCount * outputChannels
        if (output.size < outNeeded) return

        if (shotRewind) {
            shotReadFrame = if (reversePlayback) totalFrames - 1 else 0
            shotRewind = false
        }

        var pos = normalizeFrameIndex(shotReadFrame, totalFrames)
        val rev = reversePlayback

        for (f in 0 until frameCount) {
            val srcBase = pos * sampleChannels
            val dstBase = f * outputChannels
            for (oc in 0 until outputChannels) {
                val sc = if (sampleChannels == 1) 0 else min(oc, sampleChannels - 1)
                output[dstBase + oc] += pcm[srcBase + sc]
            }
            if (rev) {
                pos--
                if (pos < 0) pos = totalFrames - 1
            } else {
                pos++
                if (pos >= totalFrames) pos = 0
            }
        }
        shotReadFrame = pos
    }
}
