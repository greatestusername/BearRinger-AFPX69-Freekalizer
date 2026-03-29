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
    val loadedSampleRateHz: Int,
    /** Bars from the last quantized capture, if any (for loaded-sample context). */
    val lastQuantizedBars: QuantizedBars?,
    /**
     * BPM at the moment the current buffer was captured (quantized/free), when applicable.
     * Null after load from preset/file without a fresh capture in this session.
     */
    val bpmAtLastCapture: Double?,
    val recordingBpm: Double,
    val isPlaybackLooping: Boolean,
    val isShotActive: Boolean,
    /** Best-effort loop playhead from the audio thread; `-1` when not looping. */
    val loopPlayheadFrame: Int,
    /** Wrapped fractional loop position `0..1` when `>= 0` (smoother than frame index); `-1` when invalid. */
    val loopPlayheadFraction: Float,
    /** Best-effort SHOT playhead while held; `-1` when SHOT is inactive. */
    val shotPlayheadFrame: Int,
    val shotPlayheadFraction: Float,
    val samplerFxRoute: SamplerFxRouteIntent,
    val isReversePlayback: Boolean,
    /** E3 main pitch knob (-50..+50); **pitch-only** on FX bus. */
    val mainPitchPercent: Float,
    /** E3 sample pitch (-50..+50); **pitch+tempo** on sampler playback. */
    val samplePitchPercent: Float
)
