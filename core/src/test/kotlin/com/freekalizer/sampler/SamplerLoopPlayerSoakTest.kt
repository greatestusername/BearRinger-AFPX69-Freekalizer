package com.freekalizer.sampler

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SamplerLoopPlayerSoakTest {
    @Test
    fun longRunLoopShotReverseAndPitchRemainsFinite() {
        val frames = 4_096
        val pcm = FloatArray(frames) { i ->
            // deterministic non-trivial waveform
            val t = i / 48_000.0
            (kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t) * 0.7).toFloat()
        }
        val player = SamplerLoopPlayer()
        player.load(
            SamplerBuffer(
                pcm = pcm,
                sampleRateHz = 48_000,
                channels = 1
            )
        )
        player.setLooping(true)

        val out = FloatArray(256)
        repeat(8_000) { i ->
            if (i % 300 == 0) player.setReversePlayback(i % 600 == 0)
            if (i % 120 == 0) player.setShotPressed(true)
            if (i % 120 == 40) player.setShotPressed(false)

            val pitch = when (i % 5) {
                0 -> -50f
                1 -> -12f
                2 -> 0f
                3 -> 17f
                else -> 50f
            }
            player.setSamplePitchPercent(pitch)

            out.fill(0f)
            player.mixInto(out, outputChannels = 1, frameCount = out.size)
            player.mixShotInto(out, outputChannels = 1, frameCount = out.size)
            for (s in out) {
                assertTrue(s.isFinite(), "non-finite output in soak at iteration=$i")
                assertTrue(abs(s) < 4f, "runaway output magnitude in soak at iteration=$i: $s")
            }
        }
    }
}

