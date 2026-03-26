package com.freekalizer.sampler

/**
 * Interleaved PCM suitable for playback or further processing.
 * Frame count is [pcm.size] / [channels].
 */
data class SamplerBuffer(
    val pcm: FloatArray,
    val sampleRateHz: Int,
    val channels: Int
) {
    init {
        require(channels > 0) { "channels must be > 0" }
        require(pcm.size % channels == 0) { "pcm length must be multiple of channels" }
    }

    val frameCount: Int get() = pcm.size / channels
}

fun QuantizedRecordingResult.toSamplerBuffer(): SamplerBuffer =
    SamplerBuffer(pcm = pcm, sampleRateHz = sampleRateHz, channels = channels)
