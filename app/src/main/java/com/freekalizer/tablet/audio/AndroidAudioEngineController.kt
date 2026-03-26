package com.freekalizer.tablet.audio

import android.content.Context
import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.AudioEngine
import com.freekalizer.audio.AudioEngineConfig
import com.freekalizer.audio.AudioEngineState
import com.freekalizer.sampler.FrameCappedSamplerRecorder
import com.freekalizer.sampler.FreeRecordMath
import com.freekalizer.sampler.QuantizedBars
import com.freekalizer.sampler.QuantizedLoopMath
import com.freekalizer.sampler.QuantizedSamplerRecorder
import com.freekalizer.sampler.SamplerBuffer
import com.freekalizer.sampler.SamplerFxRouteIntent
import com.freekalizer.sampler.SamplerLoopPlayer
import com.freekalizer.sampler.toSamplerBuffer

class AndroidAudioEngineController(
    context: Context
) {
    private val backend = AndroidAudioBackend(context)
    private val engine: AudioEngine = AudioEngine(backend)

    private var currentConfig: AudioEngineConfig? = null

    // Stored so UI selection changes can rebind safely without duplicating state logic.
    @Volatile
    private var preferredInputId: Int? = null

    @Volatile
    private var preferredOutputId: Int? = null

    @Volatile
    private var recorder: QuantizedSamplerRecorder? = null

    @Volatile
    private var freeRecorder: FrameCappedSamplerRecorder? = null

    @Volatile
    private var recordingBars: QuantizedBars? = null

    @Volatile
    private var recordingCaptureKind: SamplerCaptureKind? = null

    @Volatile
    private var recordingTargetFrames: Int = 0

    @Volatile
    private var recordingProgressFrames: Int = 0

    @Volatile
    private var loadedSample: SamplerBuffer? = null

    @Volatile
    private var lastQuantizedBars: QuantizedBars? = null

    private val loopPlayer = SamplerLoopPlayer()

    /**
     * Scratch bus for sampler audio when [samplerFxRoute] is [SamplerFxRouteIntent.THROUGH_EFFECTS_PATH].
     * Sized once per [startMonitoring] from burst × channels; no per-callback allocations.
     */
    private var fxBusScratch: FloatArray = FloatArray(0)

    @Volatile
    private var samplerFxRoute: SamplerFxRouteIntent = SamplerFxRouteIntent.THROUGH_EFFECTS_PATH

    /**
     * Opening/closing streams can post AudioManager device callbacks on the main looper; those may
     * call back into UI that invokes [rebindIfRunning] again. Suppress nested rebinds.
     */
    @Volatile
    private var rebindInProgress: Boolean = false

    private val recordingBpm = 120.0

    fun startMonitoring() {
        if (engine.state != AudioEngineState.STOPPED) {
            return
        }

        val config = AudioEngineConfig(
            sampleRateHz = AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ,
            framesPerBurst = AndroidAudioDefaults.LOW_LATENCY_FRAMES_PER_BURST,
            inputChannels = 1,
            outputChannels = 2
        )

        currentConfig = config
        fxBusScratch = FloatArray(config.framesPerBurst * config.outputChannels)

        engine.start(config) { input, output, frameCount ->
            val qRec = recorder
            val fRec = freeRecorder
            if (qRec != null) {
                qRec.appendInterleaved(input, frameCount)
                recordingProgressFrames += frameCount
                if (qRec.isComplete()) {
                    val done = qRec.completeOrNull()
                    recorder = null
                    recordingBars = null
                    recordingCaptureKind = null
                    recordingTargetFrames = 0
                    recordingProgressFrames = 0
                    if (done != null) {
                        lastQuantizedBars = done.bars
                        val buf = done.toSamplerBuffer()
                        loadedSample = buf
                        loopPlayer.load(buf)
                    }
                }
            } else if (fRec != null) {
                fRec.appendInterleaved(input, frameCount)
                recordingProgressFrames += frameCount
                if (fRec.isComplete()) {
                    val buf = fRec.completeOrNull()
                    freeRecorder = null
                    recordingCaptureKind = null
                    recordingTargetFrames = 0
                    recordingProgressFrames = 0
                    if (buf != null) {
                        lastQuantizedBars = null
                        loadedSample = buf
                        loopPlayer.load(buf)
                    }
                }
            }

            // Monitoring path: copy input to output with simple mono->stereo handling.
            val inChannels = config.inputChannels
            val outChannels = config.outputChannels
            if (inChannels <= 0) {
                // No input configured; emit silence.
                val outSamples = frameCount * outChannels
                for (i in 0 until outSamples) output[i] = 0f
            } else {
                for (f in 0 until frameCount) {
                    val inBase = f * inChannels
                    val outBase = f * outChannels
                    val srcLeft = inBase // mono -> use left
                    for (oc in 0 until outChannels) {
                        val srcChannel = if (inChannels == 1) 0 else minOf(oc, inChannels - 1)
                        output[outBase + oc] = input[srcLeft + srcChannel]
                    }
                }
            }

            val outSamples = frameCount * outChannels
            when (samplerFxRoute) {
                SamplerFxRouteIntent.THROUGH_EFFECTS_PATH -> {
                    // E4: apply filter/delay/etc. and E3 main pitch on fxBusScratch in place here.
                    fxBusScratch.fill(0f, fromIndex = 0, toIndex = outSamples)
                    loopPlayer.mixInto(fxBusScratch, outChannels, frameCount)
                    loopPlayer.mixShotInto(fxBusScratch, outChannels, frameCount)
                    for (i in 0 until outSamples) {
                        output[i] += fxBusScratch[i]
                    }
                }
                SamplerFxRouteIntent.DIRECT_TO_MONITOR_MIX -> {
                    loopPlayer.mixInto(output, outChannels, frameCount)
                    loopPlayer.mixShotInto(output, outChannels, frameCount)
                }
            }
            for (i in 0 until outSamples) {
                output[i] = output[i].coerceIn(-1f, 1f)
            }
        }
    }

    fun stopMonitoring() {
        engine.stop()
        recorder = null
        freeRecorder = null
        recordingBars = null
        recordingCaptureKind = null
        recordingProgressFrames = 0
        recordingTargetFrames = 0
        loopPlayer.setLooping(false)
        loopPlayer.setShotPressed(false)
    }

    fun setPreferredDevices(inputId: Int?, outputId: Int?) {
        preferredInputId = inputId
        preferredOutputId = outputId
        backend.setPreferredDevices(inputId, outputId)
    }

    fun isRunning(): Boolean = engine.state == AudioEngineState.RUNNING

    fun meterSnapshot(): AudioMeterSnapshot = backend.meterSnapshot()

    fun startQuantizedRecording(bars: QuantizedBars): Boolean {
        val config = currentConfig ?: return false
        if (!isRunning()) return false
        if (recorder != null || freeRecorder != null) return false

        val next = QuantizedSamplerRecorder(
            sampleRateHz = config.sampleRateHz,
            channels = config.inputChannels
        )
        next.start(bpm = recordingBpm, bars = bars)
        recorder = next
        recordingBars = bars
        recordingCaptureKind = SamplerCaptureKind.QUANTIZED
        recordingTargetFrames = QuantizedLoopMath.targetFrames(
            sampleRateHz = config.sampleRateHz,
            bpm = recordingBpm,
            bars = bars
        )
        recordingProgressFrames = 0
        return true
    }

    /**
     * Records until [maxFrames] interleaved frames are captured, or [stopFreeRecordingEarly] is used.
     */
    fun startFreeRecording(maxFrames: Int): Boolean {
        val config = currentConfig ?: return false
        if (!isRunning()) return false
        if (recorder != null || freeRecorder != null) return false
        if (maxFrames <= 0) return false

        val next = FrameCappedSamplerRecorder(
            sampleRateHz = config.sampleRateHz,
            channels = config.inputChannels
        )
        next.start(maxFrames = maxFrames)
        freeRecorder = next
        recordingBars = null
        recordingCaptureKind = SamplerCaptureKind.FREE
        recordingTargetFrames = maxFrames
        recordingProgressFrames = 0
        return true
    }

    fun startFreeRecordingSeconds(seconds: Double): Boolean {
        val config = currentConfig ?: return false
        val frames = FreeRecordMath.maxFramesForSeconds(config.sampleRateHz, seconds)
        return startFreeRecording(frames)
    }

    fun stopFreeRecordingEarly(): Boolean {
        val active = freeRecorder ?: return false
        active.finishEarly()
        return true
    }

    fun setLoopPlayback(enabled: Boolean): Boolean {
        if (enabled && loadedSample == null && !loopPlayer.hasBuffer()) return false
        loopPlayer.setLooping(enabled)
        return enabled == loopPlayer.isLooping()
    }

    fun isLoopPlaybackRunning(): Boolean = loopPlayer.isLooping()

    fun setShotPressed(down: Boolean) {
        loopPlayer.setShotPressed(down)
    }

    fun isShotActive(): Boolean = loopPlayer.isShotActive()

    fun setReversePlayback(enabled: Boolean) {
        loopPlayer.setReversePlayback(enabled)
    }

    fun isReversePlayback(): Boolean = loopPlayer.isReversePlayback()

    fun samplerFxRouteIntent(): SamplerFxRouteIntent = samplerFxRoute

    fun setSamplerFxRouteIntent(intent: SamplerFxRouteIntent) {
        samplerFxRoute = intent
    }

    fun recordingState(): SamplerRecordingState {
        val loaded = loadedSample
        return SamplerRecordingState(
            isRecording = recorder != null || freeRecorder != null,
            captureKind = recordingCaptureKind,
            bars = recordingBars,
            progressFrames = recordingProgressFrames,
            targetFrames = recordingTargetFrames,
            loadedFrameCount = loaded?.frameCount ?: 0,
            isPlaybackLooping = loopPlayer.isLooping(),
            isShotActive = loopPlayer.isShotActive(),
            samplerFxRoute = samplerFxRoute,
            isReversePlayback = loopPlayer.isReversePlayback()
        )
    }

    /**
     * Stops + restarts the core engine to apply new audio device routing safely.
     *
     * This avoids attempting to mutate AudioRecord/AudioTrack parameters mid-stream,
     * which can cause instability on some Android devices.
     */
    fun rebindIfRunning() {
        if (!isRunning()) return
        if (rebindInProgress) return
        rebindInProgress = true
        try {
            stopMonitoring()
            startMonitoring()
        } finally {
            rebindInProgress = false
        }
    }
}
