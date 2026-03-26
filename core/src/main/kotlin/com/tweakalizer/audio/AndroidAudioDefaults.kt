package com.tweakalizer.audio

/**
 * Conservative defaults for low-latency tablet audio paths.
 *
 * Final values should be selected per device capability from platform APIs.
 */
object AndroidAudioDefaults {
    const val LOW_LATENCY_SAMPLE_RATE_HZ: Int = 48_000
    const val LOW_LATENCY_FRAMES_PER_BURST: Int = 192
}
