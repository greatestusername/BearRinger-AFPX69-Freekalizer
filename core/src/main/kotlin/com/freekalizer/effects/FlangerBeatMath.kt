package com.freekalizer.effects

/**
 * LFO frequency (Hz) for flanger when one full modulation cycle spans [beatsPerCycle] quarter-note beats.
 */
object FlangerBeatMath {
    fun beatsPerCycleToLfoHz(bpm: Double, beatsPerCycle: Double): Double {
        val b = bpm.coerceIn(40.0, 280.0)
        val beats = beatsPerCycle.coerceAtLeast(1.0 / 64.0)
        return b / (60.0 * beats)
    }
}
