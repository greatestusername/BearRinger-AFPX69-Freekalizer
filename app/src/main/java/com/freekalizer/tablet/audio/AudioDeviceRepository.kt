package com.freekalizer.tablet.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AudioDeviceRepository(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var devices: List<AudioDevice> = emptyList()

    @Volatile
    var selectedInputId: Int? = null
        private set

    @Volatile
    var selectedOutputId: Int? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callback: ((List<AudioDevice>) -> Unit)? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val removedIds = removedDevices.map { it.id }.toSet()
            val hadInputRemoved = selectedInputId?.let { removedIds.contains(it) } == true
            val hadOutputRemoved = selectedOutputId?.let { removedIds.contains(it) } == true

            refresh()

            if (hadInputRemoved) {
                selectedInputId = pickDefaultInputId()
            }
            if (hadOutputRemoved) {
                selectedOutputId = pickDefaultOutputId()
            }
            emit()
        }
    }

    fun start(onDevicesChanged: (List<AudioDevice>) -> Unit) {
        callback = onDevicesChanged
        refresh()
        if (selectedInputId == null) selectedInputId = pickDefaultInputId()
        if (selectedOutputId == null) selectedOutputId = pickDefaultOutputId()

        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        emit()
    }

    fun stop() {
        callback = null
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    fun setSelectedInput(id: Int?) {
        selectedInputId = id
        emit()
    }

    fun setSelectedOutput(id: Int?) {
        selectedOutputId = id
        emit()
    }

    fun snapshotDevices(): List<AudioDevice> = devices

    private fun emit() {
        callback?.invoke(devices)
    }

    private fun refresh() {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val map = linkedMapOf<Int, AudioDevice>()

        fun labelFor(info: AudioDeviceInfo): String {
            val typeLabel = when (info.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
                else -> "Device ${info.type}"
            }
            val product = info.productName?.toString()?.takeIf { it.isNotBlank() }
            return if (product != null) "$typeLabel ($product)" else typeLabel
        }

        inputs.forEach { info ->
            map[info.id] = AudioDevice(
                id = info.id,
                label = labelFor(info),
                isInput = true,
                isOutput = false
            )
        }
        outputs.forEach { info ->
            val existing = map[info.id]
            map[info.id] = if (existing == null) {
                AudioDevice(
                    id = info.id,
                    label = labelFor(info),
                    isInput = false,
                    isOutput = true
                )
            } else {
                existing.copy(isOutput = true)
            }
        }

        devices = map.values.toList()
    }

    private fun pickDefaultInputId(): Int? =
        devices.firstOrNull { it.isInput && it.label.contains("Built-in Mic") }?.id
            ?: devices.firstOrNull { it.isInput }?.id

    private fun pickDefaultOutputId(): Int? =
        devices.firstOrNull { it.isOutput && it.label.contains("Speaker") }?.id
            ?: devices.firstOrNull { it.isOutput }?.id
}

