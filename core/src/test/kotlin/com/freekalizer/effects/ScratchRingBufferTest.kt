package com.freekalizer.effects

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ScratchRingBufferTest {

    @Test
    fun readAtLagReturnsEarlierWrittenFrame() {
        val ring = ScratchRingBuffer(capacityFrames = 1024)
        repeat(100) { i ->
            ring.writeStereoFrame(i.toFloat(), (-i).toFloat())
        }
        val (l, r) = ring.readStereoAtLagFrames(10f)
        assertTrue(abs(l - 89f) < 0.01f, "expected ~89 got $l")
        assertTrue(abs(r - (-89f)) < 0.01f, "expected ~-89 got $r")
    }

    @Test
    fun zeroLagMatchesLastWrittenSample() {
        val ring = ScratchRingBuffer(capacityFrames = 256)
        ring.writeStereoFrame(0.3f, -0.4f)
        ring.writeStereoFrame(0.5f, -0.6f)
        val (l, r) = ring.readStereoAtLagFrames(0f)
        assertTrue(abs(l - 0.5f) < 1e-5f)
        assertTrue(abs(r - (-0.6f)) < 1e-5f)
    }
}
