package com.freekalizer.pitch

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class StreamingStereoMasterPitchShifterTest {

    @Test
    fun stereoInterleaved_staysFinite_panDifferencePreserved() {
        val shifter = StreamingStereoMasterPitchShifter(maxBurstFrames = 256)
        val block = 128
        val src = FloatArray(block * 2)
        val dst = FloatArray(block * 2)
        var phase = 0.0
        val omega = 2.0 * kotlin.math.PI * 330.0 / 48_000.0
        repeat(80) {
            for (f in 0 until block) {
                val s = sin(phase).toFloat()
                phase += omega
                src[f * 2] = s
                src[f * 2 + 1] = -s
            }
            shifter.processInterleaved(src, dst, block, channels = 2, pitchRatio = 1.06f)
            for (v in dst) assertTrue(v.isFinite())
        }
        var anyOppositeSign = false
        for (f in 0 until block) {
            val l = dst[f * 2]
            val r = dst[f * 2 + 1]
            if (l * r < 0f && kotlin.math.abs(l) > 1e-4f && kotlin.math.abs(r) > 1e-4f) {
                anyOppositeSign = true
                break
            }
        }
        assertTrue(anyOppositeSign)
    }
}
