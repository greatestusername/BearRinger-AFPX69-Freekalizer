package com.freekalizer.tablet.audio

import android.content.Context
import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.AudioEngine
import com.freekalizer.audio.AudioEngineConfig
import com.freekalizer.audio.AudioEngineState

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
        engine.start(config) { input, output, frameCount ->
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
        }
    }

    fun stopMonitoring() {
        engine.stop()
    }

    fun setPreferredDevices(inputId: Int?, outputId: Int?) {
        preferredInputId = inputId
        preferredOutputId = outputId
        backend.setPreferredDevices(inputId, outputId)
    }

    fun isRunning(): Boolean = engine.state == AudioEngineState.RUNNING

    /**
     * Stops + restarts the core engine to apply new audio device routing safely.
     *
     * This avoids attempting to mutate AudioRecord/AudioTrack parameters mid-stream,
     * which can cause instability on some Android devices.
     */
    fun rebindIfRunning() {
        if (!isRunning()) return
        stopMonitoring()
        startMonitoring()
    }
}
