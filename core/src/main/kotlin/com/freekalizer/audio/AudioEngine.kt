package com.freekalizer.audio

enum class AudioEngineState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING
}

data class AudioEngineConfig(
    val sampleRateHz: Int,
    val framesPerBurst: Int,
    val inputChannels: Int,
    val outputChannels: Int
)

fun interface AudioProcessor {
    /**
     * Process audio from input to output buffers.
     *
     * Implementations should avoid allocations to reduce GC pressure in callback paths.
     *
     * Buffer layout semantics:
     * - `input` is interleaved per frame using `inputChannels` from the engine config.
     * - `output` is interleaved per frame using `outputChannels` from the engine config.
     * - `input.size >= frameCount * inputChannels`
     * - `output.size >= frameCount * outputChannels`
     */
    fun process(input: FloatArray, output: FloatArray, frameCount: Int)
}

interface AudioBackend {
    fun start(config: AudioEngineConfig, processor: AudioProcessor)
    fun stop()
}

class AudioEngine(private val backend: AudioBackend) {
    var state: AudioEngineState = AudioEngineState.STOPPED
        private set

    @Volatile
    private var currentConfig: AudioEngineConfig? = null

    @Synchronized
    fun start(config: AudioEngineConfig, processor: AudioProcessor) {
        require(state == AudioEngineState.STOPPED) {
            "AudioEngine must be STOPPED to start. Current state: $state"
        }
        validateConfig(config)

        state = AudioEngineState.STARTING
        try {
            backend.start(config, processor)
            currentConfig = config
            state = AudioEngineState.RUNNING
        } catch (t: Throwable) {
            state = AudioEngineState.STOPPED
            currentConfig = null
            throw t
        }
    }

    @Synchronized
    fun stop() {
        if (state == AudioEngineState.STOPPED) {
            return
        }
        require(state == AudioEngineState.RUNNING) {
            "AudioEngine stop is only valid when RUNNING. Current state: $state"
        }

        state = AudioEngineState.STOPPING
        try {
            backend.stop()
        } finally {
            currentConfig = null
            state = AudioEngineState.STOPPED
        }
    }

    fun currentConfigOrNull(): AudioEngineConfig? = currentConfig

    private fun validateConfig(config: AudioEngineConfig) {
        require(config.sampleRateHz in 8_000..192_000) {
            "sampleRateHz must be in 8k..192k, got ${config.sampleRateHz}"
        }
        require(config.framesPerBurst > 0) {
            "framesPerBurst must be > 0"
        }
        require(config.inputChannels in 0..2) {
            "inputChannels must be 0..2"
        }
        require(config.outputChannels in 1..2) {
            "outputChannels must be 1..2"
        }
    }
}
