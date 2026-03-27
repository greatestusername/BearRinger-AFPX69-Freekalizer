package com.freekalizer.effects

/**
 * Converts musical delay (fractional beats at [bpm]) to an integer delay in frames.
 * Used for BPM-synced echo timing (E4-S3).
 */
object DelayBeatMath {
    fun beatsToDelayFrames(bpm: Double, beats: Double, sampleRateHz: Int): Int {
        require(sampleRateHz > 0)
        val b = bpm.coerceIn(40.0, 280.0)
        val bt = beats.coerceAtLeast(1.0 / 64.0)
        val seconds = (60.0 / b) * bt
        return (seconds * sampleRateHz).toInt().coerceAtLeast(1)
    }
}
