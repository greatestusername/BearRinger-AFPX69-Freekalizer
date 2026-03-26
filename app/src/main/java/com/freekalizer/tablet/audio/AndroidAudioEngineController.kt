package com.freekalizer.tablet.audio

import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.AudioEngine
import com.freekalizer.audio.AudioEngineConfig
import com.freekalizer.audio.AudioEngineState

class AndroidAudioEngineController(
    private val engine: AudioEngine = AudioEngine(AndroidAudioBackend())
) {
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

        engine.start(config) { input, output, frameCount ->
            val safeFrames = minOf(frameCount, input.size, output.size)
            for (i in 0 until safeFrames) {
                output[i] = input[i]
            }
        }
    }

    fun stopMonitoring() {
        engine.stop()
    }
}
