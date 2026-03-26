package com.freekalizer.tablet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.freekalizer.sampler.QuantizedBars
import com.freekalizer.sampler.SamplerFxRouteIntent
import com.freekalizer.tablet.audio.AndroidAudioEngineController
import com.freekalizer.tablet.audio.AudioDeviceRepository
import com.freekalizer.tablet.databinding.ActivityMainBinding
import com.freekalizer.ui.ManualLabels

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: AudioDeviceRepository
    /** Created in [onCreate] after [super.onCreate]; [Context.getSystemService] is invalid earlier. */
    private lateinit var engineController: AndroidAudioEngineController

    private val meterHandler = Handler(Looper.getMainLooper())
    private val meterTicker = object : Runnable {
        override fun run() {
            updateMeterUi()
            updateRecordingUi()
            meterHandler.postDelayed(this, 100L)
        }
    }

    private var inputItems: List<Pair<String, Int?>> = emptyList()
    private var outputItems: List<Pair<String, Int?>> = emptyList()

    private val inputDeviceListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            repo.setSelectedInput(inputItems.getOrNull(position)?.second)
            updateRouteStatus()
            syncEngineControllerToSelection(rebind = true)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private val outputDeviceListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            repo.setSelectedOutput(outputItems.getOrNull(position)?.second)
            updateRouteStatus()
            syncEngineControllerToSelection(rebind = true)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    /**
     * Device enumeration callbacks can fire while streams are opening; defer route/sync + rebind so we
     * are not nested in the same main-thread stack as AudioRecord/AudioTrack setup (API 34+ emulators).
     */
    private val deferredDeviceRouteSync = Runnable {
        if (isFinishing) return@Runnable
        syncEngineControllerToSelection(rebind = true)
    }
    private val barItems: List<Pair<String, QuantizedBars>> = listOf(
        "1 bar" to QuantizedBars.BAR_1,
        "2 bars" to QuantizedBars.BAR_2,
        "4 bars" to QuantizedBars.BAR_4,
        "8 bars" to QuantizedBars.BAR_8,
        "16 bars" to QuantizedBars.BAR_16
    )

    private val freeCapSeconds: List<Pair<String, Double>> = listOf(
        "5 s cap" to 5.0,
        "15 s cap" to 15.0,
        "30 s cap" to 30.0,
        "60 s cap" to 60.0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineController = AndroidAudioEngineController(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = AudioDeviceRepository(this)

        binding.fxRouteToggle.isChecked =
            engineController.samplerFxRouteIntent() == SamplerFxRouteIntent.THROUGH_EFFECTS_PATH

        binding.requestMic.setOnClickListener { requestMicPermission() }

        binding.startMonitoring.setOnClickListener {
            if (!isMicPermissionGranted()) {
                requestMicPermission()
                return@setOnClickListener
            }
            engineController.setPreferredDevices(
                repo.selectedInputId,
                repo.selectedOutputId
            )
            engineController.startMonitoring()
        }
        binding.stopMonitoring.setOnClickListener { engineController.stopMonitoring() }

        binding.recQuantized.setOnClickListener {
            val started = engineController.startQuantizedRecording(selectedBars())
            if (!started) {
                binding.recordingStatus.text =
                    "REC not started (start monitoring first or wait)."
            }
        }
        binding.recFree.setOnClickListener {
            val started = engineController.startFreeRecordingSeconds(selectedFreeCapSeconds())
            if (!started) {
                binding.recordingStatus.text =
                    "Free REC not started (start monitoring first, or other REC active)."
            }
        }
        binding.recFreeStop.setOnClickListener {
            engineController.stopFreeRecordingEarly()
        }
        binding.playStop.setOnClickListener {
            val on = engineController.isLoopPlaybackRunning()
            val ok = engineController.setLoopPlayback(!on)
            if (!ok && !on) {
                binding.recordingStatus.text = "Load a sample via REC first."
            }
        }
        binding.fxRouteToggle.setOnCheckedChangeListener { _, checked ->
            engineController.setSamplerFxRouteIntent(
                if (checked) SamplerFxRouteIntent.THROUGH_EFFECTS_PATH
                else SamplerFxRouteIntent.DIRECT_TO_MONITOR_MIX
            )
        }

        binding.revToggle.isEnabled = false
        binding.revToggle.isChecked = false
        binding.revToggle.setOnCheckedChangeListener { _, checked ->
            engineController.setReversePlayback(checked)
        }

        binding.shotButton.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    engineController.setShotPressed(true)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    engineController.setShotPressed(false)
                }
            }
            true
        }

        updatePermissionUi()
        binding.barSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            barItems.map { it.first }
        )
        binding.freeCapSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            freeCapSeconds.map { it.first }
        )
        bindSpinners()
    }

    override fun onStart() {
        super.onStart()
        repo.start { _ ->
            updateDeviceUi()
            binding.root.removeCallbacks(deferredDeviceRouteSync)
            binding.root.post(deferredDeviceRouteSync)
        }
        meterHandler.post(meterTicker)
    }

    override fun onStop() {
        super.onStop()
        binding.root.removeCallbacks(deferredDeviceRouteSync)
        engineController.setShotPressed(false)
        meterHandler.removeCallbacks(meterTicker)
        repo.stop()
    }

    private fun bindSpinners() {
        binding.inputSpinner.onItemSelectedListener = inputDeviceListener
        binding.outputSpinner.onItemSelectedListener = outputDeviceListener
    }

    private fun updateDeviceUi() {
        val devices = repo.snapshotDevices()

        inputItems = listOf("System default" to null) + devices
            .filter { it.isInput }
            .map { it.label to it.id }
        outputItems = listOf("System default" to null) + devices
            .filter { it.isOutput }
            .map { it.label to it.id }

        binding.inputSpinner.onItemSelectedListener = null
        binding.outputSpinner.onItemSelectedListener = null
        try {
            binding.inputSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                inputItems.map { it.first }
            )
            binding.outputSpinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                outputItems.map { it.first }
            )
            val inIdx = inputItems.indexOfFirst { it.second == repo.selectedInputId }
                .let { if (it >= 0) it else 0 }
            val outIdx = outputItems.indexOfFirst { it.second == repo.selectedOutputId }
                .let { if (it >= 0) it else 0 }
            binding.inputSpinner.setSelection(inIdx, false)
            binding.outputSpinner.setSelection(outIdx, false)
        } finally {
            binding.inputSpinner.onItemSelectedListener = inputDeviceListener
            binding.outputSpinner.onItemSelectedListener = outputDeviceListener
        }

        updateRouteStatus()
    }

    private fun updateRouteStatus() {
        binding.routeStatus.text = buildString {
            append("Selected input id: ${repo.selectedInputId ?: "default"}\n")
            append("Selected output id: ${repo.selectedOutputId ?: "default"}\n")
            append("Device count: ${repo.snapshotDevices().size}")
        }
    }

    private fun updateMeterUi() {
        val meter = engineController.meterSnapshot()
        binding.meterStatus.text = buildString {
            append("${ManualLabels.IN_LEVEL}: ${toPercent(meter.inputPeak)}%  ")
            append("${ManualLabels.OVERLOAD} IN: ${if (meter.inputClipping) "YES" else "NO"}\n")
            append("OUT LEVEL: ${toPercent(meter.outputPeak)}%  ")
            append("${ManualLabels.OVERLOAD} OUT: ${if (meter.outputClipping) "YES" else "NO"}")
        }
    }

    private fun updateRecordingUi() {
        val state = engineController.recordingState()
        val hasSample = state.loadedFrameCount > 0
        binding.revToggle.isEnabled = hasSample
        if (!hasSample && binding.revToggle.isChecked) {
            binding.revToggle.isChecked = false
        }
        val pct = if (state.targetFrames > 0) {
            ((state.progressFrames.coerceAtMost(state.targetFrames).toFloat() / state.targetFrames) * 100f).toInt()
        } else {
            0
        }
        binding.recordingStatus.text = buildString {
            append("REC: ${if (state.isRecording) "ON" else "OFF"}")
            if (state.isRecording && state.captureKind != null) {
                append(" (${state.captureKind.name.lowercase()})")
            }
            if (state.bars != null) append(" (${state.bars.bars} bar)")
            append("\nProgress: $pct%")
            append("\nLoaded sample frames: ${state.loadedFrameCount}")
            append("\n${ManualLabels.PLAY_STOP}: ${if (state.isPlaybackLooping) "ON (loop)" else "OFF"}")
            append("\n${ManualLabels.SHOT}: ${if (state.isShotActive) "ON (held)" else "OFF"}")
            val routeLabel = when (state.samplerFxRoute) {
                SamplerFxRouteIntent.THROUGH_EFFECTS_PATH -> "effects bus (E4 hook)"
                SamplerFxRouteIntent.DIRECT_TO_MONITOR_MIX -> "direct (bypass FX/master pitch)"
            }
            append("\n${ManualLabels.FX_ROUTE}: $routeLabel")
            append("\n${ManualLabels.REV}: ${if (state.isReversePlayback) "backward" else "forward"}")
        }
    }

    private fun updatePermissionUi() {
        val granted = isMicPermissionGranted()
        binding.permissionStatus.text = "Mic permission: ${if (granted) "GRANTED" else "NOT GRANTED"}"
    }

    private fun isMicPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_MIC
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            updatePermissionUi()
        }
    }

    private fun syncEngineControllerToSelection(rebind: Boolean) {
        engineController.setPreferredDevices(repo.selectedInputId, repo.selectedOutputId)
        if (rebind) engineController.rebindIfRunning()
        updateRouteStatus()
    }

    private fun toPercent(v: Float): Int {
        val clamped = v.coerceIn(0f, 1f)
        return (clamped * 100f).toInt()
    }

    private fun selectedBars(): QuantizedBars {
        val idx = binding.barSpinner.selectedItemPosition
        return barItems.getOrNull(idx)?.second ?: QuantizedBars.BAR_1
    }

    private fun selectedFreeCapSeconds(): Double {
        val idx = binding.freeCapSpinner.selectedItemPosition
        return freeCapSeconds.getOrNull(idx)?.second ?: 30.0
    }

    companion object {
        private const val REQ_MIC = 1001
    }
}
