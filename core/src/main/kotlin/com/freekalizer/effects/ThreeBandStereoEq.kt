package com.freekalizer.effects

/**
 * Three-band **peaking** EQ on stereo interleaved audio (low / mid / high), per channel.
 * No shelving filters — each band is parametric peaking only, so cuts stay localized.
 * [syncCoefficients] is allocation-free; [processInterleavedStereo] is allocation-free.
 */
class ThreeBandStereoEq(
    private val deepCutDbThreshold: Float = -28f,
) {
    private val lowL = ShelfPeakingBiquad()
    private val lowR = ShelfPeakingBiquad()
    private val midL = ShelfPeakingBiquad()
    private val midR = ShelfPeakingBiquad()
    private val highL = ShelfPeakingBiquad()
    private val highR = ShelfPeakingBiquad()

    private var prevLowDb: Float = 0f
    private var prevMidDb: Float = 0f
    private var prevHighDb: Float = 0f

    fun reset() {
        lowL.reset()
        lowR.reset()
        midL.reset()
        midR.reset()
        highL.reset()
        highR.reset()
        prevLowDb = 0f
        prevMidDb = 0f
        prevHighDb = 0f
    }

    fun syncCoefficients(
        sampleRateHz: Int,
        lowDb: Float,
        midDb: Float,
        highDb: Float,
    ) {
        val largeGainStep =
            kotlin.math.abs(lowDb - prevLowDb) > COEFF_STEP_RESET_DB ||
                kotlin.math.abs(midDb - prevMidDb) > COEFF_STEP_RESET_DB ||
                kotlin.math.abs(highDb - prevHighDb) > COEFF_STEP_RESET_DB
        if (largeGainStep ||
            crossedDeepCut(prevLowDb, lowDb) ||
            crossedDeepCut(prevMidDb, midDb) ||
            crossedDeepCut(prevHighDb, highDb)
        ) {
            lowL.reset()
            lowR.reset()
            midL.reset()
            midR.reset()
            highL.reset()
            highR.reset()
        }
        prevLowDb = lowDb
        prevMidDb = midDb
        prevHighDb = highDb
        lowL.setPeakingDb(sampleRateHz, LOW_HZ, LOW_Q, lowDb)
        lowR.setPeakingDb(sampleRateHz, LOW_HZ, LOW_Q, lowDb)
        midL.setPeakingDb(sampleRateHz, MID_HZ, MID_Q, midDb)
        midR.setPeakingDb(sampleRateHz, MID_HZ, MID_Q, midDb)
        highL.setPeakingDb(sampleRateHz, HIGH_HZ, HIGH_Q, highDb)
        highR.setPeakingDb(sampleRateHz, HIGH_HZ, HIGH_Q, highDb)
    }

    private fun crossedDeepCut(prev: Float, next: Float): Boolean {
        val t = deepCutDbThreshold
        return (prev > t && next <= t) || (prev <= t && next > t)
    }

    /** [buffer] is interleaved L,R; [frameCount] stereo frames. */
    fun processInterleavedStereo(buffer: FloatArray, frameCount: Int) {
        for (f in 0 until frameCount) {
            val b = f * 2
            var l = buffer[b]
            var r = buffer[b + 1]
            l = lowL.process(l)
            r = lowR.process(r)
            l = midL.process(l)
            r = midR.process(r)
            l = highL.process(l)
            r = highR.process(r)
            buffer[b] = l
            buffer[b + 1] = r
        }
    }

    companion object {
        /** Fader minimum / kill “bottom” (matches UI and engine clamp). */
        const val MIN_DB: Float = -36f

        /** Fader maximum boost. */
        const val BOOST_MAX_DB: Float = 24f

        /** Alias: kill buttons snap to fader bottom [MIN_DB]. */
        const val KILL_DB: Float = MIN_DB

        private const val COEFF_STEP_RESET_DB: Float = 12f

        private const val LOW_HZ: Float = 280f
        private const val LOW_Q: Float = 0.85f
        private const val MID_HZ: Float = 1800f
        private const val MID_Q: Float = 1.2f
        private const val HIGH_HZ: Float = 6000f
        private const val HIGH_Q: Float = 1.0f
    }
}
