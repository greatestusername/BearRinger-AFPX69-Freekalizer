package com.freekalizer.sampler

/**
 * Serializable sample library metadata (FR-6). Platform layers map to JSON or plist.
 *
 * @param quantizedBars If non-null, bar count from last quantized capture (1, 2, 4, 8, or 16).
 */
data class SavedSampleMetadata(
    val name: String,
    val createdAtEpochMillis: Long,
    /** BPM at capture time when known; otherwise BPM context at save (see engine contract). */
    val bpmAtRecord: Double,
    val durationSeconds: Double,
    val reverse: Boolean,
    val mainPitchPercent: Float,
    val samplePitchPercent: Float,
    val quantizedBars: Int?,
    /** Library UI flag (E6-S3); persisted in `.meta.json`. */
    val favorite: Boolean = false
)
