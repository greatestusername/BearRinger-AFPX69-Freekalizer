package com.freekalizer.timing

import java.util.ArrayDeque

/**
 * Tap-tempo: pass strictly increasing **seconds** (e.g. [SystemClock.elapsedRealtime]/1000f).
 * After enough taps, returns BPM from the **mean** of recent inter-tap intervals; `null` until
 * at least [minTapsForEstimate] taps. BPM is clamped to [[minBpm], [maxBpm]].
 */
class TapBpmEstimator(
    private val minBpm: Double = 40.0,
    private val maxBpm: Double = 280.0,
    private val maxStoredTaps: Int = 16,
    private val minTapsForEstimate: Int = 3,
    /** Longer gap (seconds) starts a new tap run. */
    private val staleGapSeconds: Double = 3.0
) {
    private val times = ArrayDeque<Double>()

    fun reset() {
        times.clear()
    }

    /**
     * Records a tap at [nowSeconds]. Returns updated BPM estimate, or `null` if not enough taps yet.
     */
    fun tap(nowSeconds: Double): Double? {
        require(nowSeconds >= 0.0) { "nowSeconds must be >= 0" }
        if (times.isNotEmpty() && nowSeconds < times.last()) {
            times.clear()
        }
        if (times.isNotEmpty() && nowSeconds - times.last() > staleGapSeconds) {
            times.clear()
        }
        times.addLast(nowSeconds)
        while (times.size > maxStoredTaps) {
            times.removeFirst()
        }
        if (times.size < minTapsForEstimate) return null
        var sum = 0.0
        var count = 0
        val iter = times.iterator()
        var prev = iter.next()
        while (iter.hasNext()) {
            val cur = iter.next()
            val dt = cur - prev
            if (dt > 1e-6) {
                sum += dt
                count++
            }
            prev = cur
        }
        if (count == 0) return null
        val avg = sum / count
        val bpm = 60.0 / avg
        return bpm.coerceIn(minBpm, maxBpm)
    }
}
