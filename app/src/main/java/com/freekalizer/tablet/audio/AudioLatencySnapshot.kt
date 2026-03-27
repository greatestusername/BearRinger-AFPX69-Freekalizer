package com.freekalizer.tablet.audio

data class AudioLatencySnapshot(
    val callbackCount: Long,
    val callbackIntervalAvgMs: Double,
    val callbackIntervalMinMs: Double,
    val callbackIntervalMaxMs: Double,
    val callbackJitterStdDevMs: Double,
    val readBlockAvgMs: Double,
    val readBlockMaxMs: Double,
    val processAvgMs: Double,
    val processMaxMs: Double,
    val writeBlockAvgMs: Double,
    val writeBlockMaxMs: Double
)

