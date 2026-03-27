package com.freekalizer.effects

/**
 * Three-band ISO-style EQ on stereo interleaved audio: low shelf, mid peaking, high shelf per channel.
 * Coefficients updated via [syncCoefficients]; [processInterleavedStereo] is allocation-free.
 */
class ThreeBandStereoEq {
    private val lowL = ShelfPeakingBiquad()
    private val lowR = ShelfPeakingBiquad()
    private val midL = ShelfPeakingBiquad()
    private val midR = ShelfPeakingBiquad()
    private val highL = ShelfPeakingBiquad()
    private val highR = ShelfPeakingBiquad()

    fun reset() {
        lowL.reset()
        lowR.reset()
        midL.reset()
        midR.reset()
        highL.reset()
        highR.reset()
    }

    fun syncCoefficients(
        sampleRateHz: Int,
        lowDb: Float,
        midDb: Float,
        highDb: Float,
        killLow: Boolean,
        killMid: Boolean,
        killHigh: Boolean,
        killAll: Boolean
    ) {
        val l = if (killAll || killLow) KILL_DB else lowDb
        val m = if (killAll || killMid) KILL_DB else midDb
        val h = if (killAll || killHigh) KILL_DB else highDb
        lowL.setLowShelfDb(sampleRateHz, LOW_HZ, SHELF_Q, l)
        lowR.setLowShelfDb(sampleRateHz, LOW_HZ, SHELF_Q, l)
        midL.setPeakingDb(sampleRateHz, MID_HZ, MID_Q, m)
        midR.setPeakingDb(sampleRateHz, MID_HZ, MID_Q, m)
        highL.setHighShelfDb(sampleRateHz, HIGH_HZ, SHELF_Q, h)
        highR.setHighShelfDb(sampleRateHz, HIGH_HZ, SHELF_Q, h)
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
        const val KILL_DB: Float = -80f
        private const val LOW_HZ: Float = 250f
        private const val MID_HZ: Float = 1800f
        private const val HIGH_HZ: Float = 6400f
        private const val MID_Q: Float = 1.2f
        private const val SHELF_Q: Float = 0.85f
    }
}
