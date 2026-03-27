package com.freekalizer.tablet.audio

import android.content.res.AssetManager
import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.PcmResampler
import com.freekalizer.audio.WavPcmIo
import com.freekalizer.sampler.QuantizedBars
import com.freekalizer.sampler.SamplerBuffer

/**
 * Loads bundled [ASSET_NAME] (120 BPM, one bar) into the sampler for instant testing.
 * WAV is resampled to [AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ] to match the engine.
 */
object PresetDrumloopLoader {
    private const val ASSET_NAME = "drumloop.wav"

    fun tryLoad(controller: AndroidAudioEngineController, assets: AssetManager): Boolean {
        return try {
            assets.open(ASSET_NAME).use { input ->
                val bytes = input.readBytes()
                val decoded = WavPcmIo.decode(bytes)
                val pcm = PcmResampler.resampleInterleavedLinear(
                    decoded.interleavedFloat,
                    decoded.channels,
                    decoded.sampleRateHz,
                    AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ
                )
                val buf = SamplerBuffer(
                    pcm = pcm,
                    sampleRateHz = AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ,
                    channels = decoded.channels
                )
                controller.loadPresetSample(
                    buffer = buf,
                    lastQuantizedBars = QuantizedBars.BAR_1,
                    bpmAtCapture = 120.0
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
