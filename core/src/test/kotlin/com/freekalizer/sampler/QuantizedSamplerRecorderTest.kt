package com.freekalizer.sampler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuantizedSamplerRecorderTest {
    @Test
    fun targetFramesForOneBarAt120BpmIsTwoSeconds() {
        val frames = QuantizedLoopMath.targetFrames(
            sampleRateHz = 48_000,
            bpm = 120.0,
            bars = QuantizedBars.BAR_1
        )
        assertEquals(96_000, frames)
    }

    @Test
    fun targetFramesScalesByBarCount() {
        val one = QuantizedLoopMath.targetFrames(48_000, 120.0, QuantizedBars.BAR_1)
        val sixteen = QuantizedLoopMath.targetFrames(48_000, 120.0, QuantizedBars.BAR_16)
        assertEquals(one * 16, sixteen)
    }

    @Test
    fun recorderCompletesOnlyAtTargetFrameCount() {
        val recorder = QuantizedSamplerRecorder(sampleRateHz = 100, channels = 1)
        recorder.start(bpm = 120.0, bars = QuantizedBars.BAR_1) // 200 frames

        recorder.appendInterleaved(FloatArray(150) { 0.5f }, frameCount = 150)
        assertFalse(recorder.isComplete())
        assertNull(recorder.completeOrNull())

        recorder.appendInterleaved(FloatArray(100) { 0.25f }, frameCount = 100)
        assertTrue(recorder.isComplete())
        val done = assertNotNull(recorder.completeOrNull())
        assertEquals(200, done.pcm.size)
    }

    @Test
    fun recorderTruncatesExcessFramesAtBoundary() {
        val recorder = QuantizedSamplerRecorder(sampleRateHz = 100, channels = 2)
        recorder.start(bpm = 120.0, bars = QuantizedBars.BAR_1) // 200 frames * 2ch = 400 samples

        recorder.appendInterleaved(FloatArray(1000) { 1.0f }, frameCount = 500)
        val result = assertNotNull(recorder.completeOrNull())
        assertEquals(400, result.pcm.size)
        assertTrue(result.pcm.all { it == 1.0f })
    }
}

