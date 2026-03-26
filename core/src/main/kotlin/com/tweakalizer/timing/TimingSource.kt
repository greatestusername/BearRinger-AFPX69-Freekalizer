package com.tweakalizer.timing

enum class TimingSourceType {
    INTERNAL_BPM,
    ABLETON_LINK
}

data class TimingSnapshot(
    val bpm: Double,
    val beat: Double,
    val seconds: Double
)

interface TimingSource {
    val type: TimingSourceType
    fun snapshotAt(monotonicSeconds: Double): TimingSnapshot
}

class TimingSourceRegistry(internalBpmSource: TimingSource) {
    private val sources = mutableMapOf<TimingSourceType, TimingSource>()
    var activeType: TimingSourceType = TimingSourceType.INTERNAL_BPM
        private set

    init {
        require(internalBpmSource.type == TimingSourceType.INTERNAL_BPM) {
            "Internal source must expose INTERNAL_BPM type"
        }
        register(internalBpmSource)
    }

    @Synchronized
    fun register(source: TimingSource) {
        sources[source.type] = source
    }

    @Synchronized
    fun unregister(type: TimingSourceType) {
        if (type == TimingSourceType.INTERNAL_BPM) {
            return
        }
        sources.remove(type)
        if (activeType == type) {
            activeType = TimingSourceType.INTERNAL_BPM
        }
    }

    @Synchronized
    fun setActive(type: TimingSourceType): Boolean {
        val sourceExists = sources[type] != null
        if (!sourceExists) {
            return false
        }
        activeType = type
        return true
    }

    @Synchronized
    fun snapshot(monotonicSeconds: Double): TimingSnapshot {
        val source = sources[activeType] ?: sources[TimingSourceType.INTERNAL_BPM]
        ?: error("Internal timing source is required")
        return source.snapshotAt(monotonicSeconds)
    }
}
