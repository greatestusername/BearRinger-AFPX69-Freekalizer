package com.freekalizer.tablet.audio

data class AudioMeterSnapshot(
    val inputPeak: Float,
    val outputPeak: Float,
    val inputClipping: Boolean,
    val outputClipping: Boolean
)

