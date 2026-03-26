package com.freekalizer.sampler

/**
 * Records interleaved float PCM until [maxFrames] frames are captured or [finishEarly] is called.
 */
class FrameCappedSamplerRecorder(
    private val sampleRateHz: Int,
    private val channels: Int
) {
    private var targetFrames: Int = 0
    private var writeFrames: Int = 0
    private var buffer: FloatArray = FloatArray(0)
    private var finishedEarly: Boolean = false

    fun start(maxFrames: Int) {
        require(channels > 0) { "channels must be > 0" }
        require(maxFrames > 0) { "maxFrames must be > 0" }
        targetFrames = maxFrames
        writeFrames = 0
        finishedEarly = false
        buffer = FloatArray(targetFrames * channels)
    }

    /**
     * Ends capture before the cap is reached; next [appendInterleaved] / completion check yields a partial buffer.
     */
    fun finishEarly() {
        if (targetFrames <= 0) return
        finishedEarly = true
    }

    fun appendInterleaved(input: FloatArray, frameCount: Int) {
        if (targetFrames <= 0) return
        require(frameCount >= 0) { "frameCount must be >= 0" }

        val readableFrames = minOf(frameCount, input.size / channels)
        if (readableFrames <= 0) return

        val framesLeft = targetFrames - writeFrames
        if (framesLeft <= 0) return

        val framesToCopy = minOf(readableFrames, framesLeft)
        val srcSamples = framesToCopy * channels
        val dstOffset = writeFrames * channels
        input.copyInto(
            destination = buffer,
            destinationOffset = dstOffset,
            startIndex = 0,
            endIndex = srcSamples
        )
        writeFrames += framesToCopy
    }

    fun isComplete(): Boolean {
        if (targetFrames <= 0) return false
        if (finishedEarly && writeFrames > 0) return true
        return writeFrames >= targetFrames
    }

    fun completeOrNull(): SamplerBuffer? {
        if (!isComplete()) return null
        val frames = writeFrames.coerceAtMost(targetFrames).coerceAtLeast(0)
        if (frames <= 0) return null
        val samples = frames * channels
        return SamplerBuffer(
            pcm = buffer.copyOf(samples),
            sampleRateHz = sampleRateHz,
            channels = channels
        )
    }
}

object FreeRecordMath {
    fun maxFramesForSeconds(sampleRateHz: Int, seconds: Double): Int {
        require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
        require(seconds > 0.0) { "seconds must be > 0" }
        return (seconds * sampleRateHz).toInt().coerceAtLeast(1)
    }
}
