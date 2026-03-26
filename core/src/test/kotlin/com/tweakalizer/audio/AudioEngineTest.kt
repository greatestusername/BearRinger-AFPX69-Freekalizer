package com.tweakalizer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AudioEngineTest {

    @Test
    fun startAndStopTransitionsStates() {
        val backend = FakeAudioBackend()
        val engine = AudioEngine(backend)
        val config = AudioEngineConfig(
            sampleRateHz = 48_000,
            framesPerBurst = 192,
            inputChannels = 1,
            outputChannels = 2
        )

        engine.start(config) { _, _, _ -> }

        assertEquals(AudioEngineState.RUNNING, engine.state)
        assertEquals(1, backend.startCalls)
        assertNotNull(engine.currentConfigOrNull())

        engine.stop()

        assertEquals(AudioEngineState.STOPPED, engine.state)
        assertEquals(1, backend.stopCalls)
        assertNull(engine.currentConfigOrNull())
    }

    @Test
    fun startFailureResetsToStopped() {
        val backend = FakeAudioBackend(shouldFailOnStart = true)
        val engine = AudioEngine(backend)
        val config = AudioEngineConfig(
            sampleRateHz = 48_000,
            framesPerBurst = 192,
            inputChannels = 1,
            outputChannels = 2
        )

        try {
            engine.start(config) { _, _, _ -> }
            fail("Expected start to throw")
        } catch (_: IllegalStateException) {
            assertEquals(AudioEngineState.STOPPED, engine.state)
            assertNull(engine.currentConfigOrNull())
        }
    }

    @Test
    fun invalidConfigThrows() {
        val backend = FakeAudioBackend()
        val engine = AudioEngine(backend)
        val badConfig = AudioEngineConfig(
            sampleRateHz = 100,
            framesPerBurst = 192,
            inputChannels = 1,
            outputChannels = 2
        )

        try {
            engine.start(badConfig) { _, _, _ -> }
            fail("Expected invalid config to throw")
        } catch (t: IllegalArgumentException) {
            assertTrue(t.message?.contains("sampleRateHz") == true)
        }
    }

    private class FakeAudioBackend(
        private val shouldFailOnStart: Boolean = false
    ) : AudioBackend {
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override fun start(config: AudioEngineConfig, processor: AudioProcessor) {
            startCalls++
            if (shouldFailOnStart) {
                throw IllegalStateException("Simulated backend failure")
            }
        }

        override fun stop() {
            stopCalls++
        }
    }
}
