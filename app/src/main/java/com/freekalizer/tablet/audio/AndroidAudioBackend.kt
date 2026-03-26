package com.freekalizer.tablet.audio

import android.util.Log
import com.freekalizer.audio.AudioBackend
import com.freekalizer.audio.AudioEngineConfig
import com.freekalizer.audio.AudioProcessor

/**
 * Android adapter for core AudioBackend.
 *
 * Next step is wiring this to a real low-latency callback path (AAudio/Oboe).
 */
class AndroidAudioBackend : AudioBackend {
    @Volatile
    private var running: Boolean = false

    override fun start(config: AudioEngineConfig, processor: AudioProcessor) {
        check(!running) { "AndroidAudioBackend already running" }

        // Placeholder to keep lifecycle contract testable before native path integration.
        Log.i(TAG, "Starting backend with config=$config")
        running = true
    }

    override fun stop() {
        if (!running) {
            return
        }
        Log.i(TAG, "Stopping backend")
        running = false
    }

    companion object {
        private const val TAG = "AndroidAudioBackend"
    }
}
