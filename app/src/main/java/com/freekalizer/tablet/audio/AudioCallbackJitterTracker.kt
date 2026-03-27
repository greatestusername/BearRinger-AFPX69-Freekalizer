package com.freekalizer.tablet.audio

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Low-overhead callback/jitter tracker for Android audio loop benchmarking.
 * All APIs are called from the audio worker thread except [snapshot], which may be read on UI thread.
 */
class AudioCallbackJitterTracker {
    private var callbackCount: Long = 0L
    private var lastCallbackStartNs: Long = 0L

    private var intervalSumMs: Double = 0.0
    private var intervalSumSqMs: Double = 0.0
    private var intervalMinMs: Double = Double.POSITIVE_INFINITY
    private var intervalMaxMs: Double = 0.0

    private var readCount: Long = 0L
    private var readSumMs: Double = 0.0
    private var readMaxMs: Double = 0.0

    private var processCount: Long = 0L
    private var processSumMs: Double = 0.0
    private var processMaxMs: Double = 0.0

    private var writeCount: Long = 0L
    private var writeSumMs: Double = 0.0
    private var writeMaxMs: Double = 0.0

    fun reset() {
        callbackCount = 0L
        lastCallbackStartNs = 0L
        intervalSumMs = 0.0
        intervalSumSqMs = 0.0
        intervalMinMs = Double.POSITIVE_INFINITY
        intervalMaxMs = 0.0
        readCount = 0L
        readSumMs = 0.0
        readMaxMs = 0.0
        processCount = 0L
        processSumMs = 0.0
        processMaxMs = 0.0
        writeCount = 0L
        writeSumMs = 0.0
        writeMaxMs = 0.0
    }

    fun onCallbackStart(nowNs: Long) {
        if (lastCallbackStartNs != 0L) {
            val dtMs = (nowNs - lastCallbackStartNs).toDouble() / 1_000_000.0
            intervalSumMs += dtMs
            intervalSumSqMs += dtMs * dtMs
            intervalMinMs = minOf(intervalMinMs, dtMs)
            intervalMaxMs = max(intervalMaxMs, dtMs)
        }
        callbackCount++
        lastCallbackStartNs = nowNs
    }

    fun onReadDone(durationNs: Long) {
        val ms = durationNs.toDouble() / 1_000_000.0
        readCount++
        readSumMs += ms
        readMaxMs = max(readMaxMs, ms)
    }

    fun onProcessDone(durationNs: Long) {
        val ms = durationNs.toDouble() / 1_000_000.0
        processCount++
        processSumMs += ms
        processMaxMs = max(processMaxMs, ms)
    }

    fun onWriteDone(durationNs: Long) {
        val ms = durationNs.toDouble() / 1_000_000.0
        writeCount++
        writeSumMs += ms
        writeMaxMs = max(writeMaxMs, ms)
    }

    fun snapshot(): AudioLatencySnapshot {
        val intervalSamples = (callbackCount - 1L).coerceAtLeast(0L)
        val intervalAvg = if (intervalSamples > 0) intervalSumMs / intervalSamples else 0.0
        val intervalVar = if (intervalSamples > 1) {
            val meanSq = intervalSumSqMs / intervalSamples
            max(0.0, meanSq - intervalAvg * intervalAvg)
        } else {
            0.0
        }
        return AudioLatencySnapshot(
            callbackCount = callbackCount,
            callbackIntervalAvgMs = intervalAvg,
            callbackIntervalMinMs = if (intervalSamples > 0) intervalMinMs else 0.0,
            callbackIntervalMaxMs = if (intervalSamples > 0) intervalMaxMs else 0.0,
            callbackJitterStdDevMs = sqrt(intervalVar),
            readBlockAvgMs = if (readCount > 0) readSumMs / readCount else 0.0,
            readBlockMaxMs = readMaxMs,
            processAvgMs = if (processCount > 0) processSumMs / processCount else 0.0,
            processMaxMs = processMaxMs,
            writeBlockAvgMs = if (writeCount > 0) writeSumMs / writeCount else 0.0,
            writeBlockMaxMs = writeMaxMs
        )
    }
}

