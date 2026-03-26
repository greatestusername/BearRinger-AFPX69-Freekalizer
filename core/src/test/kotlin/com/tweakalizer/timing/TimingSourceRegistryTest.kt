package com.tweakalizer.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimingSourceRegistryTest {

    @Test
    fun defaultsToInternalSource() {
        val internal = InternalBpmTimingSource(initialBpm = 120.0)
        val registry = TimingSourceRegistry(internal)

        val snapshot = registry.snapshot(monotonicSeconds = 1.0)
        assertEquals(TimingSourceType.INTERNAL_BPM, registry.activeType)
        assertEquals(120.0, snapshot.bpm)
        assertEquals(2.0, snapshot.beat)
    }

    @Test
    fun setActiveReturnsFalseWhenSourceMissing() {
        val internal = InternalBpmTimingSource(initialBpm = 120.0)
        val registry = TimingSourceRegistry(internal)

        val changed = registry.setActive(TimingSourceType.ABLETON_LINK)
        assertFalse(changed)
        assertEquals(TimingSourceType.INTERNAL_BPM, registry.activeType)
    }

    @Test
    fun switchesToRegisteredLinkSourceAndFallsBackOnUnregister() {
        val internal = InternalBpmTimingSource(initialBpm = 120.0)
        val registry = TimingSourceRegistry(internal)
        val link = FixedTimingSource(
            type = TimingSourceType.ABLETON_LINK,
            snapshot = TimingSnapshot(
                bpm = 128.0,
                beat = 10.0,
                seconds = 5.0
            )
        )

        registry.register(link)
        val changed = registry.setActive(TimingSourceType.ABLETON_LINK)
        assertTrue(changed)
        assertEquals(128.0, registry.snapshot(monotonicSeconds = 3.0).bpm)

        registry.unregister(TimingSourceType.ABLETON_LINK)
        assertEquals(TimingSourceType.INTERNAL_BPM, registry.activeType)
        assertEquals(120.0, registry.snapshot(monotonicSeconds = 1.0).bpm)
    }

    private class FixedTimingSource(
        override val type: TimingSourceType,
        private val snapshot: TimingSnapshot
    ) : TimingSource {
        override fun snapshotAt(monotonicSeconds: Double): TimingSnapshot = snapshot
    }
}
