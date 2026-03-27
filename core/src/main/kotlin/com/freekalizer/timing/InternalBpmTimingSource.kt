package com.freekalizer.timing

class InternalBpmTimingSource(
    initialBpm: Double = 120.0
) : TimingSource {
    override val type: TimingSourceType = TimingSourceType.INTERNAL_BPM

    @Volatile
    private var bpm: Double = initialBpm

    init {
        require(initialBpm > 0.0) { "BPM must be > 0" }
    }

    fun updateBpm(nextBpm: Double) {
        require(nextBpm > 0.0) { "BPM must be > 0" }
        bpm = nextBpm
    }

    fun currentBpm(): Double = bpm

    override fun snapshotAt(monotonicSeconds: Double): TimingSnapshot {
        require(monotonicSeconds >= 0.0) { "monotonicSeconds must be >= 0" }
        val currentBpm = bpm
        val beatsPerSecond = currentBpm / 60.0
        return TimingSnapshot(
            bpm = currentBpm,
            beat = monotonicSeconds * beatsPerSecond,
            seconds = monotonicSeconds
        )
    }
}
