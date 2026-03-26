package com.freekalizer.tablet.audio

import com.freekalizer.sampler.QuantizedBars
import com.freekalizer.sampler.SamplerFxRouteIntent

enum class SamplerCaptureKind {
    QUANTIZED,
    FREE
}

data class SamplerRecordingState(
    val isRecording: Boolean,
    val captureKind: SamplerCaptureKind?,
    val bars: QuantizedBars?,
    val progressFrames: Int,
    val targetFrames: Int,
    val loadedFrameCount: Int,
    val isPlaybackLooping: Boolean,
    val isShotActive: Boolean,
    val samplerFxRoute: SamplerFxRouteIntent,
    val isReversePlayback: Boolean
)
