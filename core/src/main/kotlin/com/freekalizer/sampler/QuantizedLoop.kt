package com.freekalizer.sampler

enum class QuantizedBars(val bars: Int) {
    BAR_1(1),
    BAR_2(2),
    BAR_4(4),
    BAR_8(8),
    BAR_16(16);

    companion object {
        fun fromBarCount(barCount: Int): QuantizedBars? =
            entries.firstOrNull { it.bars == barCount }
    }
}

object QuantizedLoopMath {
    /**
     * Returns exact frame count for a quantized loop at 4/4 time.
     */
    fun targetFrames(
        sampleRateHz: Int,
        bpm: Double,
        bars: QuantizedBars
    ): Int {
        require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
        require(bpm > 0.0) { "bpm must be > 0" }

        val beatsPerBar = 4.0
        val secondsPerBeat = 60.0 / bpm
        val seconds = bars.bars * beatsPerBar * secondsPerBeat
        return (seconds * sampleRateHz).toInt()
    }
}

/**
 * BPM implied by loop **PCM length** when clip is known to span [bars] bars of **4/4**
 * (same convention as [QuantizedLoopMath]). Inverse of [QuantizedLoopMath.targetFrames].
 */
object LoopBpmMath {
    private const val BEATS_PER_BAR_4_4: Double = 4.0

    fun bpmFromLoopFrames(sampleRateHz: Int, frameCount: Int, bars: QuantizedBars): Double {
        require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
        require(frameCount > 0) { "frameCount must be > 0" }
        val beats = bars.bars * BEATS_PER_BAR_4_4
        return 60.0 * beats * sampleRateHz / frameCount
    }
}

data class QuantizedRecordingResult(
    val pcm: FloatArray,
    val sampleRateHz: Int,
    val channels: Int,
    val bars: QuantizedBars,
    val bpm: Double
)

class QuantizedSamplerRecorder(
    private val sampleRateHz: Int,
    private val channels: Int
) {
    private var targetFrames: Int = 0
    private var writeFrames: Int = 0
    private var buffer: FloatArray = FloatArray(0)
    private var activeBars: QuantizedBars = QuantizedBars.BAR_1
    private var activeBpm: Double = 120.0

    fun start(bpm: Double, bars: QuantizedBars) {
        require(channels > 0) { "channels must be > 0" }
        activeBars = bars
        activeBpm = bpm
        targetFrames = QuantizedLoopMath.targetFrames(sampleRateHz, bpm, bars)
        writeFrames = 0
        buffer = FloatArray(targetFrames * channels)
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

    fun isComplete(): Boolean = targetFrames > 0 && writeFrames >= targetFrames

    fun completeOrNull(): QuantizedRecordingResult? {
        if (!isComplete()) return null
        return QuantizedRecordingResult(
            pcm = buffer.copyOf(),
            sampleRateHz = sampleRateHz,
            channels = channels,
            bars = activeBars,
            bpm = activeBpm
        )
    }
}

