package com.freekalizer.audio

import com.freekalizer.sampler.SamplerBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WavPcmIoTest {

    @Test
    fun roundTripMonoPreservesSamplesWithinTolerance() {
        val pcm = floatArrayOf(0f, 0.5f, -0.25f, 1f, -1f)
        val wav = WavPcmIo.encodePcm16LeWav(pcm, channels = 1, sampleRateHz = 48_000)
        val decoded = WavPcmIo.decode(wav)
        assertEquals(48_000, decoded.sampleRateHz)
        assertEquals(1, decoded.channels)
        assertEquals(pcm.size, decoded.interleavedFloat.size)
        for (i in pcm.indices) {
            assertTrue(abs(pcm[i] - decoded.interleavedFloat[i]) < 2e-4f)
        }
    }

    @Test
    fun roundTripStereo() {
        val pcm = floatArrayOf(0.1f, 0.2f, -0.3f, 0.4f)
        val buf = SamplerBuffer(pcm, sampleRateHz = 44_100, channels = 2)
        val decoded = WavPcmIo.decode(WavPcmIo.encodePcm16LeWav(buf))
        assertEquals(44_100, decoded.sampleRateHz)
        assertEquals(2, decoded.channels)
        assertEquals(4, decoded.interleavedFloat.size)
    }
}
