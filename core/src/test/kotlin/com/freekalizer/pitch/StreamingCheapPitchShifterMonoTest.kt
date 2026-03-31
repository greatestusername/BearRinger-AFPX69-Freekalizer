package com.freekalizer.pitch

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class StreamingCheapPitchShifterMonoTest {

    @Test
    fun processReplace_staysFiniteForSineAndModerateRatio() {
        val sr = 48_000
        val shifter = StreamingCheapPitchShifterMono(fftSize = 1024, hop = 256)
        val block = 256
        val input = FloatArray(block)
        val output = FloatArray(block)
        var phase = 0.0
        val omega = 2.0 * kotlin.math.PI * 440.0 / sr
        var maxAbs = 0f
        repeat(200) {
            for (i in input.indices) {
                input[i] = sin(phase).toFloat()
                phase += omega
            }
            shifter.processReplace(input, 0, output, 0, block, pitchRatio = 1.08f)
            for (v in output) {
                assertTrue(v.isFinite())
                val a = abs(v)
                if (a > maxAbs) maxAbs = a
            }
        }
        assertTrue(maxAbs > 0.01f)
    }

    @Test
    fun reset_clearsState_nextProcessDoesNotProduceNaN() {
        val shifter = StreamingCheapPitchShifterMono(fftSize = 512, hop = 128)
        val input = FloatArray(128) { i -> sin(i * 0.17).toFloat() * 0.8f }
        val out = FloatArray(128)
        shifter.processReplace(input, 0, out, 0, 128, 1.2f)
        shifter.reset()
        shifter.processReplace(input, 0, out, 0, 128, 1.0f)
        for (v in out) assertTrue(v.isFinite())
    }

    /** Mirrors ~192-frame Android bursts with fftSize > burst (regression: no zero-pad STFT). */
    @Test
    fun chunkedInput_smallerThanFft_staysFiniteAndEventuallyDiffersFromDry() {
        val shifter = StreamingCheapPitchShifterMono(fftSize = 2048, hop = 256)
        val chunk = 192
        val input = FloatArray(chunk)
        val output = FloatArray(chunk)
        var phase = 0.0
        val omega = 2.0 * kotlin.math.PI * 220.0 / 48_000.0
        var diffAccum = 0.0
        repeat(100) {
            for (i in input.indices) {
                input[i] = sin(phase).toFloat()
                phase += omega
            }
            shifter.processReplace(input, 0, output, 0, chunk, pitchRatio = 1.15f)
            for (i in output.indices) {
                assertTrue(output[i].isFinite())
                diffAccum += abs(output[i] - input[i]).toDouble()
            }
        }
        assertTrue(diffAccum > 50.0)
    }
}
