package com.freekalizer.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmResamplerTest {
    @Test
    fun doublesRateDoublesFrameCountMono() {
        val pcm = floatArrayOf(0f, 1f)
        val out = PcmResampler.resampleInterleavedLinear(pcm, channels = 1, 24_000, 48_000)
        assertEquals(4, out.size)
        assertEquals(0f, out[0])
        assertTrue(out[1] in 0.4f..0.6f)
        assertEquals(1f, out[3])
    }

    @Test
    fun sameRateCopies() {
        val pcm = floatArrayOf(0.25f, -0.25f)
        val out = PcmResampler.resampleInterleavedLinear(pcm, 1, 48_000, 48_000)
        assertEquals(0.25f, out[0])
        assertEquals(-0.25f, out[1])
    }
}
