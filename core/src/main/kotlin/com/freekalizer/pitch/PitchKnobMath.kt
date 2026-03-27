package com.freekalizer.pitch

/**
 * Shared mapping for DFX-style pitch knobs: **-50% .. +50%** (product requirement).
 */
object PitchKnobMath {
    const val MIN_PERCENT: Float = -50f
    const val MAX_PERCENT: Float = 50f

    fun clampPercent(percent: Float): Float = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /** Master pitch knob may use a smaller span (e.g. ±12) while sample pitch stays ±50. */
    fun clampMainPitchPercent(percent: Float, maxAbsPercent: Float): Float =
        percent.coerceIn(-maxAbsPercent, maxAbsPercent)

    /**
     * **Sample pitch** (pitch + tempo): linear playback-speed multiplier.
     * -50% → 0.5×, 0% → 1×, +50% → 1.5×.
     */
    fun toPlaybackSpeedMultiplier(percent: Float): Float {
        val c = clampPercent(percent)
        return 1f + c / 100f
    }

    /**
     * **Main pitch** (pitch-only path): frequency-domain shift ratio (same numeric endpoints as sample pitch).
     * 1.0 = bypass; 0.5 / 1.5 = one octave down/up at the extremes of this knob range.
     */
    fun toPitchShiftRatio(percent: Float): Float = toPlaybackSpeedMultiplier(percent)
}
