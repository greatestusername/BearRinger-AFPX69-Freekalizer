package com.freekalizer.pitch

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class StreamingVarispeedInterleavedTest {

    @Test
    fun process_staysFinite_forChunkedSine() {
        val v = StreamingVarispeedInterleaved(maxBurstFrames = 256, ringCapFrames = 4096)
        val chunk = 192
        val src = FloatArray(chunk * 2)
        val dst = FloatArray(chunk * 2)
        var phase = 0.0
        val omega = 2.0 * kotlin.math.PI * 440.0 / 48_000.0
        repeat(120) {
            for (f in 0 until chunk) {
                val s = sin(phase).toFloat()
                phase += omega
                src[f * 2] = s
                src[f * 2 + 1] = s * 0.5f
            }
            v.processInterleaved(src, dst, chunk, channels = 2, pitchRatio = 1.12f)
            for (x in dst) assertTrue(x.isFinite())
        }
    }

    @Test
    fun unityRatio_eventuallyTracksInput() {
        val v = StreamingVarispeedInterleaved(maxBurstFrames = 128, ringCapFrames = 2048)
        val n = 128
        val src = FloatArray(n * 2) { i -> (if (i % 2 == 0) 0.3f else -0.3f) }
        val dst = FloatArray(n * 2)
        repeat(30) {
            v.processInterleaved(src, dst, n, channels = 2, pitchRatio = 1f)
        }
        var diff = 0f
        for (i in src.indices) diff += abs(dst[i] - src[i])
        assertTrue(diff < n * 2 * 0.2f)
    }
}
