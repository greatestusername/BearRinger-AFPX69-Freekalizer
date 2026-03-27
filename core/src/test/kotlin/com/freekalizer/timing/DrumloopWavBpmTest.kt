package com.freekalizer.timing

import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.PcmResampler
import com.freekalizer.audio.WavPcmIo
import com.freekalizer.sampler.LoopBpmMath
import com.freekalizer.sampler.QuantizedBars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [drumloop.wav] is one 4/4 bar; bar-length BPM must be **120** at any sample rate (ground truth).
 * Onset-based figures are ancillary (see [AutoBpmEstimatorTest]).
 */
class DrumloopWavBpmTest {

    @Test
    fun `bar math on decoded drumloop is 120 at 44100`() {
        val decoded = WavPcmIo.decode(readDrumloopBytes())
        val frames = decoded.interleavedFloat.size / decoded.channels
        assertEquals(88_200, frames)
        assertEquals(
            120.0,
            LoopBpmMath.bpmFromLoopFrames(decoded.sampleRateHz, frames, QuantizedBars.BAR_1),
            1e-6
        )
    }

    @Test
    fun `bar math after PresetDrumloopLoader style resample is still 120`() {
        val decoded = WavPcmIo.decode(readDrumloopBytes())
        val pcm48 = PcmResampler.resampleInterleavedLinear(
            decoded.interleavedFloat,
            decoded.channels,
            decoded.sampleRateHz,
            AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ
        )
        val sr = AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ
        val frames = pcm48.size / decoded.channels
        assertEquals(
            120.0,
            LoopBpmMath.bpmFromLoopFrames(sr, frames, QuantizedBars.BAR_1),
            0.06
        )
    }

    @Test
    fun `onset estimator on drumloop is non degenerate`() {
        val decoded = WavPcmIo.decode(readDrumloopBytes())
        val r = AutoBpmEstimator.estimateFromInterleavedBuffer(
            decoded.interleavedFloat,
            decoded.channels,
            decoded.sampleRateHz
        )
        assertTrue(r.confidence >= 0.05f, "onset confidence=${r.confidence}")
    }

    private fun readDrumloopBytes(): ByteArray {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("drumloop.wav")) {
            "Put app/src/main/assets/drumloop.wav copy at core/src/test/resources/drumloop.wav"
        }
        return stream.use { it.readBytes() }
    }
}
