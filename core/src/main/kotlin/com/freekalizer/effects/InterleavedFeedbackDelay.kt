package com.freekalizer.effects

/**
 * Simple feedback delay on an interleaved buffer (1 or 2 channels).
 *
 * - [process] reads each output frame's dry sample, mixes wet from the delay tap, then writes the
 *   comb line: `line[w] = inputGain * dry + feedback * tap`.
 * - When [feedInput] is false (effect "off" / momentary release), `inputGain` is 0 so **new audio
 *   is not injected**, but feedback continues so the **tail decays** (FR-4 echo tail).
 *
 * No per-call allocations.
 */
class InterleavedFeedbackDelay(
    val channels: Int,
    maxDelayFrames: Int
) {
    private val ch: Int = channels.coerceIn(1, 2)
    private val maxD: Int = maxDelayFrames.coerceAtLeast(4)

    /** Ring length in frames; +1 so read index `w - d` is always distinct from `w` before advance. */
    private val cap: Int = maxD + 32
    private val buf: FloatArray = FloatArray(cap * ch)

    private var w: Int = 0
    private var d: Int = 1

    fun setDelayFrames(frames: Int) {
        d = frames.coerceIn(1, maxD)
    }

    fun delayFrames(): Int = d

    fun reset() {
        buf.fill(0f)
        w = 0
    }

    /**
     * @param interleaved in/out; length >= [frameCount] * [channels]
     */
    fun process(
        interleaved: FloatArray,
        frameCount: Int,
        feedInput: Boolean,
        feedback: Float,
        wetMix: Float
    ) {
        if (frameCount <= 0) return
        val fb = feedback.coerceIn(0f, 0.98f)
        val wm = wetMix.coerceIn(0f, 1f)
        val inGain = if (feedInput) 1f else 0f
        val capF = cap
        val c = ch
        for (f in 0 until frameCount) {
            val rf = (w - d + capF) % capF
            for (ci in 0 until c) {
                val io = f * c + ci
                val dry = interleaved[io]
                val tap = buf[rf * c + ci]
                buf[w * c + ci] = dry * inGain + fb * tap
                interleaved[io] = dry + wm * tap
            }
            w = (w + 1) % capF
        }
    }
}
