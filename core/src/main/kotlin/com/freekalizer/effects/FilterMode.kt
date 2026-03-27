package com.freekalizer.effects

/**
 * E4-S2 filter modes (manual terminology).
 *
 * - MANUAL: cutoff controlled directly
 * - LFO: cutoff modulated by an LFO (BPM-related in platform layer)
 * - AUTO: cutoff follows input envelope (auto-wah style)
 */
enum class FilterMode {
    MANUAL,
    LFO,
    AUTO
}

