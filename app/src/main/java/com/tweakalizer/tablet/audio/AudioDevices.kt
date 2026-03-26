package com.tweakalizer.tablet.audio

data class AudioDevice(
    val id: Int,
    val label: String,
    val isInput: Boolean,
    val isOutput: Boolean
)

