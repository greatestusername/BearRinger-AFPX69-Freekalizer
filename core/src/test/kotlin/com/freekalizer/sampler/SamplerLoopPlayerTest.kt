package com.freekalizer.sampler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.abs

class SamplerLoopPlayerTest {
    @Test
    fun loopsMonoIntoStereo() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(0.25f, -0.25f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setLooping(true)
        val out = FloatArray(4) // 2 frames stereo
        p.mixInto(out, outputChannels = 2, frameCount = 2)
        assertEquals(0.25f, out[0])
        assertEquals(0.25f, out[1])
        assertEquals(-0.25f, out[2])
        assertEquals(-0.25f, out[3])
    }

    @Test
    fun mixesOntoExistingOutput() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(0.5f, 0.5f),
            sampleRateHz = 48_000,
            channels = 2
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setLooping(true)
        val out = floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f)
        p.mixInto(out, outputChannels = 2, frameCount = 2)
        assertEquals(0.6f, out[0])
        assertEquals(0.6f, out[1])
        assertEquals(0.6f, out[2])
        assertEquals(0.6f, out[3])
    }

    @Test
    fun setLoopingFalseWhenEmpty() {
        val p = SamplerLoopPlayer()
        p.setLooping(true)
        assertFalse(p.isLooping())
    }

    @Test
    fun reverseLoopReadsBackwardWithWrap() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(0.1f, 0.2f, 0.3f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setReversePlayback(true)
        p.setLooping(true)
        val out = FloatArray(3)
        p.mixInto(out, outputChannels = 1, frameCount = 3)
        assertEquals(0.1f, out[0])
        assertEquals(0.3f, out[1])
        assertEquals(0.2f, out[2])
    }

    @Test
    fun shotMixesWhilePressedFromStartEachPress() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(1f, 0f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setShotPressed(true)
        val out = FloatArray(2)
        p.mixShotInto(out, outputChannels = 1, frameCount = 2)
        assertEquals(1f, out[0])
        assertEquals(0f, out[1])
        p.setShotPressed(false)
        out[0] = 0f
        out[1] = 0f
        p.mixShotInto(out, 1, 2)
        assertEquals(0f, out[0])
        p.setShotPressed(true)
        p.mixShotInto(out, 1, 1)
        assertEquals(1f, out[0])
    }

    @Test
    fun samplePitchHalfSpeedInterpolatesMidpoint() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(1f, 0f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setSamplePitchPercent(-50f)
        p.setLooping(true)
        val out = FloatArray(2)
        p.mixInto(out, outputChannels = 1, frameCount = 2)
        assertEquals(1f, out[0])
        assertTrue(abs(out[1] - 0.5f) < 1e-4f)
    }

    @Test
    fun samplePitchClampsAtPlusFiftyPercent() {
        val p = SamplerLoopPlayer()
        p.setSamplePitchPercent(500f)
        assertEquals(50f, p.samplePitchPercent(), 1e-4f)
    }

    @Test
    fun reverseShotStartsFromEndOfBuffer() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(0f, 0.5f, 1f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setReversePlayback(true)
        p.setShotPressed(true)
        val out = FloatArray(1)
        p.mixShotInto(out, outputChannels = 1, frameCount = 1)
        assertEquals(1f, out[0], 1e-4f)
    }

    @Test
    fun shotPressedInterruptsLoopPlayback() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(0.7f, 0.1f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setLooping(true)
        assertTrue(p.isLooping())
        p.setShotPressed(true)
        assertFalse(p.isLooping())
        val out = FloatArray(2)
        p.mixInto(out, outputChannels = 1, frameCount = 2)
        assertEquals(0f, out[0])
        assertEquals(0f, out[1])
        p.mixShotInto(out, outputChannels = 1, frameCount = 2)
        assertEquals(0.7f, out[0])
        assertTrue(abs(out[1] - 0.1f) < 1e-3f)
    }

    @Test
    fun clearStopsLoopAndShotImmediately() {
        val buf = SamplerBuffer(
            pcm = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f),
            sampleRateHz = 48_000,
            channels = 1
        )
        val p = SamplerLoopPlayer()
        p.load(buf)
        p.setLooping(true)
        p.setShotPressed(true)
        p.clear()
        val out = FloatArray(8)
        p.mixInto(out, outputChannels = 1, frameCount = 8)
        p.mixShotInto(out, outputChannels = 1, frameCount = 8)
        assertTrue(out.all { abs(it) < 1e-9f })
        assertFalse(p.isLooping())
        assertFalse(p.isShotActive())
    }
}
