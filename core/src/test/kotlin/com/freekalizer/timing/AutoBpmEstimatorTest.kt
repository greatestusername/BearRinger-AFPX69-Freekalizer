package com.freekalizer.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoBpmEstimatorTest {

    @Test
    fun `detects 120 BPM from periodic impulses`() {
        val sr = 48_000
        val periodFrames = sr / 2 // 0.5 s → 120 BPM
        val est = AutoBpmEstimator()
        est.configure(sr)
        est.reset()

        val channels = 1
        val burst = 512
        val input = FloatArray(burst * channels)
        var global = 0
        val total = sr * 6 // 6 seconds
        while (global < total) {
            input.fill(0f)
            val end = minOf(burst, total - global)
            for (f in 0 until end) {
                val frameIndex = global + f
                val phase = frameIndex % periodFrames
                if (phase < 120) {
                    input[f * channels] = 0.95f
                }
            }
            est.ingestInterleavedInput(input, channels, end)
            global += end
        }

        val r = est.reading()
        assertTrue(r.confidence >= 0.25f, "confidence=${r.confidence}")
        assertEquals(120.0, r.bpm, 12.0)
    }

    @Test
    fun `silence yields low confidence`() {
        val est = AutoBpmEstimator()
        est.configure(48_000)
        est.reset()
        val buf = FloatArray(2048)
        repeat(50) {
            est.ingestInterleavedInput(buf, 1, buf.size)
        }
        assertTrue(est.reading().confidence < 0.2f)
    }

    @Test
    fun `stereo loop with kicks in right channel only still builds confidence`() {
        val sr = 48_000
        val periodFrames = sr / 2
        val channels = 2
        val burst = 512
        val input = FloatArray(burst * channels)
        val est = AutoBpmEstimator()
        est.configure(sr)
        est.reset()
        var global = 0
        val total = sr * 6
        while (global < total) {
            input.fill(0f)
            val end = minOf(burst, total - global)
            for (f in 0 until end) {
                val frameIndex = global + f
                val phase = frameIndex % periodFrames
                if (phase < 120) {
                    input[f * channels + 1] = 0.95f
                }
            }
            est.ingestInterleavedInput(input, channels, end)
            global += end
        }
        val r = est.reading()
        assertTrue(r.confidence >= 0.12f, "confidence=${r.confidence}")
        assertEquals(120.0, r.bpm, 15.0)
    }

    @Test
    fun `estimateFromInterleavedBuffer matches periodic impulse train`() {
        val sr = 48_000
        val periodFrames = sr / 2
        val channels = 1
        val total = sr * 6
        val pcm = FloatArray(total * channels)
        for (f in 0 until total) {
            val phase = f % periodFrames
            pcm[f] = if (phase < 120) 0.95f else 0f
        }
        val r = AutoBpmEstimator.estimateFromInterleavedBuffer(pcm, channels, sr)
        assertTrue(r.confidence >= 0.10f, "confidence=${r.confidence}")
        assertEquals(120.0, r.bpm, 12.0)
    }
}
