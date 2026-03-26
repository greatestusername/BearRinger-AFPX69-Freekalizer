package com.freekalizer.sampler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameCappedSamplerRecorderTest {
    @Test
    fun completesAtCap() {
        val r = FrameCappedSamplerRecorder(sampleRateHz = 100, channels = 1)
        r.start(maxFrames = 50)
        r.appendInterleaved(FloatArray(60) { 0.3f }, frameCount = 60)
        assertTrue(r.isComplete())
        val buf = assertNotNull(r.completeOrNull())
        assertEquals(50, buf.frameCount)
        assertEquals(50, buf.pcm.size)
    }

    @Test
    fun finishEarlyProducesPartialBuffer() {
        val r = FrameCappedSamplerRecorder(sampleRateHz = 80, channels = 2)
        r.start(maxFrames = 100)
        r.appendInterleaved(FloatArray(40) { 1f }, frameCount = 20)
        assertFalse(r.isComplete())
        r.finishEarly()
        assertTrue(r.isComplete())
        val buf = assertNotNull(r.completeOrNull())
        assertEquals(20, buf.frameCount)
        assertEquals(40, buf.pcm.size)
    }

    @Test
    fun freeRecordMathSeconds() {
        assertEquals(48_000, FreeRecordMath.maxFramesForSeconds(48_000, 1.0))
    }
}
