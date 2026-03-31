package com.freekalizer.pitch

/**
 * Master-pitch path: **varispeed** (pitch + tempo together) via [StreamingVarispeedInterleaved].
 * Stereo L/R stay locked; mono is duplicated in the ring.
 */
class StreamingStereoMasterPitchShifter(
    maxBurstFrames: Int = 512,
    ringCapFrames: Int = 32768
) {
    private val engine = StreamingVarispeedInterleaved(maxBurstFrames, ringCapFrames)

    fun reset() {
        engine.reset()
    }

    fun processInterleaved(src: FloatArray, dst: FloatArray, frameCount: Int, channels: Int, pitchRatio: Float) {
        engine.processInterleaved(src, dst, frameCount, channels, pitchRatio)
    }
}
