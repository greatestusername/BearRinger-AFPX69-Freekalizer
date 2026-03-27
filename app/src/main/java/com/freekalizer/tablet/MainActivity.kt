package com.freekalizer.tablet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.freekalizer.audio.WavPcmIo
import com.freekalizer.sampler.QuantizedBars
import com.freekalizer.sampler.SamplerFxRouteIntent
import com.freekalizer.sampler.SamplerBuffer
import com.freekalizer.sampler.SavedSampleMetadata
import com.freekalizer.tablet.audio.AndroidAudioEngineController
import com.freekalizer.tablet.audio.AndroidAudioEngineController.MainPitchRangeMode
import com.freekalizer.tablet.audio.AudioDeviceRepository
import com.freekalizer.tablet.audio.PresetDrumloopLoader
import com.freekalizer.tablet.audio.SamplerCaptureKind
import com.freekalizer.tablet.audio.SamplerRecordingState
import com.freekalizer.tablet.audio.AndroidAudioEngineController.FxOrderMode
import com.freekalizer.tablet.audio.AndroidAudioEngineController.ScratchFeelPreset
import com.freekalizer.tablet.databinding.ActivityMainBinding
import com.freekalizer.tablet.library.SampleLibraryStore
import com.freekalizer.effects.FilterType
import com.freekalizer.effects.FilterMode
import com.freekalizer.ui.ManualLabels

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: AudioDeviceRepository
    private lateinit var sampleLibrary: SampleLibraryStore
    private var libraryEntries: List<SampleLibraryStore.Entry> = emptyList()

    private var suppressFavoriteToggle: Boolean = false

    private var suppressDelayFeedToggle: Boolean = false

    private var suppressFlangerBypassToggle: Boolean = false

    private var suppressEqSeek: Boolean = false

    private var scratchLastY: Float = 0f
    private var shotHeld: Boolean = false
    private var eqLowCutEnabled: Boolean = false
    private var eqMidCutEnabled: Boolean = false
    private var eqHighCutEnabled: Boolean = false
    private var eqAllCutEnabled: Boolean = false
    /** Created in [onCreate] after [super.onCreate]; [Context.getSystemService] is invalid earlier. */
    private lateinit var engineController: AndroidAudioEngineController

    private val meterHandler = Handler(Looper.getMainLooper())
    private val meterTicker = object : Runnable {
        override fun run() {
            updateMeterUi()
            updateBpmUi()
            updatePitchLabels()
            updateRecordingUi()
            updateEqUi()
            updatePerformanceStateLeds()
            updateHoldInteractionUi()
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

    private val filterLfoRateItems: List<Pair<String, Float>> = listOf(
        "1/4 beat" to 0.25f,
        "1/2 beat" to 0.5f,
        "1 beat" to 1.0f,
        "2 beats" to 2.0f,
        "4 beats" to 4.0f
    )

    private val scratchPresetItems: List<Pair<String, ScratchFeelPreset>> = listOf(
        "Smooth (long throw)" to ScratchFeelPreset.SMOOTH,
        "Classic vinyl" to ScratchFeelPreset.CLASSIC,
        "Cut / aggressive" to ScratchFeelPreset.CUT
    )
    private val fxOrderItems: List<Pair<String, FxOrderMode>> = listOf(
        "Scratch -> Delay/Flanger -> Filter (end)" to FxOrderMode.SCRATCH_THEN_FX,
        "Delay/Flanger -> Scratch -> Filter (end)" to FxOrderMode.FX_THEN_SCRATCH
    )

    private val mainPitchRangeItems: List<Pair<String, MainPitchRangeMode>> = listOf(
        "±12 %" to MainPitchRangeMode.PERCENT_12,
        "±24 %" to MainPitchRangeMode.PERCENT_24,
        "±50 %" to MainPitchRangeMode.PERCENT_50
    )

    private var suppressMasterPitchRangeSpinner: Boolean = false

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, R.layout.spinner_item_dark, items).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineController = AndroidAudioEngineController(this)
        sampleLibrary = SampleLibraryStore(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (!restoreLastLibrarySample()) {
            PresetDrumloopLoader.tryLoad(engineController, assets)
        }

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
                    shotHeld = true
                    engineController.setShotPressed(true)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    shotHeld = false
                    engineController.setShotPressed(false)
                }
            }
            true
        }

        updatePermissionUi()
        if (isMicPermissionGranted()) {
            engineController.setPreferredDevices(repo.selectedInputId, repo.selectedOutputId)
            engineController.startMonitoring()
        }
        binding.barSpinner.adapter = spinnerAdapter(barItems.map { it.first })
        binding.freeCapSpinner.adapter = spinnerAdapter(freeCapSeconds.map { it.first })
        bindSpinners()
        bindPitchControls()
        bindMainMixControl()
        bindMasterPitchRangeSpinner()
        updatePitchLabels()
        bindFilterControls()
        updateFilterUi()
        bindDelayControls()
        updateDelayUi()
        bindFlangerControls()
        updateFlangerUi()
        bindInputGainControl()
        bindScratchPresetControls()
        bindFxOrderControls()
        bindEqControls()
        updateEqUi()
        bindScratchSurface()
        bindDoubleTapResets()
        bindSubmenuControls()
        bindSampleLibraryUi()
        refreshLibrarySpinner()

        binding.bpmAutoFollow.isChecked = engineController.isBpmAutoFollowEnabled()
        binding.bpmAutoFollow.setOnCheckedChangeListener { _, checked ->
            engineController.setBpmAutoFollowEnabled(checked)
            updateBpmUi()
        }
        binding.bpmTap.setOnClickListener {
            val sec = SystemClock.elapsedRealtime() / 1000.0
            engineController.tapTempoNow(sec)
            updateBpmUi()
        }
        binding.bpmDefault.setOnClickListener {
            engineController.resetBpmToDefault()
            updateBpmUi()
        }
        updateBpmUi()
    }

    private fun bindSubmenuControls() {
        binding.menuAudio.setOnClickListener { showSubmenu(binding.panelAudio) }
        binding.menuBpm.setOnClickListener { showSubmenu(binding.panelBpm) }
        binding.menuSampler.setOnClickListener { showSubmenu(binding.panelSampler) }
        binding.menuFx.setOnClickListener { showSubmenu(binding.panelFx) }
        binding.menuLibrary.setOnClickListener { showSubmenu(binding.panelLibrary) }
        binding.menuSystem.setOnClickListener { showSubmenu(binding.panelSystem) }
        binding.submenuClose.setOnClickListener { hideSubmenu() }
        binding.submenuScrim.setOnClickListener { hideSubmenu() }
    }

    private fun showSubmenu(target: View) {
        binding.submenuContainer.visibility = View.VISIBLE
        binding.submenuScrim.visibility = View.VISIBLE
        binding.panelAudio.visibility = View.GONE
        binding.panelBpm.visibility = View.GONE
        binding.panelSampler.visibility = View.GONE
        binding.panelFx.visibility = View.GONE
        binding.panelLibrary.visibility = View.GONE
        binding.panelSystem.visibility = View.GONE
        target.visibility = View.VISIBLE
    }

    private fun hideSubmenu() {
        binding.submenuContainer.visibility = View.GONE
        binding.submenuScrim.visibility = View.GONE
        binding.panelAudio.visibility = View.GONE
        binding.panelBpm.visibility = View.GONE
        binding.panelSampler.visibility = View.GONE
        binding.panelFx.visibility = View.GONE
        binding.panelLibrary.visibility = View.GONE
        binding.panelSystem.visibility = View.GONE
    }

    private fun bindFilterControls() {
        binding.filterLpButton.setOnClickListener {
            engineController.setFilterType(FilterType.LOW_PASS)
            updateFilterUi()
        }
        binding.filterHpButton.setOnClickListener {
            engineController.setFilterType(FilterType.HIGH_PASS)
            updateFilterUi()
        }
        binding.filterBpButton.setOnClickListener {
            engineController.setFilterType(FilterType.BAND_PASS)
            updateFilterUi()
        }

        binding.filterModeManual.setOnClickListener {
            engineController.setFilterMode(FilterMode.MANUAL)
            updateFilterUi()
        }
        binding.filterModeLfo.setOnClickListener {
            engineController.setFilterMode(FilterMode.LFO)
            updateFilterUi()
        }
        binding.filterModeAuto.setOnClickListener {
            engineController.setFilterMode(FilterMode.AUTO)
            updateFilterUi()
        }

        binding.filterLfoRateSpinner.adapter = spinnerAdapter(filterLfoRateItems.map { it.first })
        binding.filterLfoRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val beats = filterLfoRateItems.getOrNull(position)?.second ?: 1.0f
                engineController.setFilterLfoPeriodBeats(beats)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.filterCutoffSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFilterCutoffNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.filterResonanceSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFilterResonanceNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.filterLfoDepthSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFilterLfoDepth(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.filterAutoDepthSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFilterAutoDepth(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
    }

    private fun updateFilterUi() {
        // Sync slider positions (best-effort; user movement will drive controller live).
        binding.filterCutoffSeek.progress = (engineController.filterCutoffNorm() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.filterResonanceSeek.progress = (engineController.filterResonanceNorm() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.filterLfoDepthSeek.progress = (engineController.filterLfoDepth() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.filterAutoDepthSeek.progress = (engineController.filterAutoDepth() * 1000f).roundToInt().coerceIn(0, 1000)
        val lfoIdx = filterLfoRateItems.indexOfFirst { it.second == engineController.filterLfoPeriodBeats() }
        if (lfoIdx >= 0) binding.filterLfoRateSpinner.setSelection(lfoIdx, false)

        val selected = engineController.filterType()
        val mode = engineController.filterMode()
        val cyan = ContextCompat.getColor(this, R.color.accent_led_cyan)
        val muted = ContextCompat.getColor(this, R.color.text_muted)
        binding.filterLpButton.setTextColor(if (selected == FilterType.LOW_PASS) cyan else muted)
        binding.filterHpButton.setTextColor(if (selected == FilterType.HIGH_PASS) cyan else muted)
        binding.filterBpButton.setTextColor(if (selected == FilterType.BAND_PASS) cyan else muted)

        binding.filterModeManual.setTextColor(if (mode == FilterMode.MANUAL) cyan else muted)
        binding.filterModeLfo.setTextColor(if (mode == FilterMode.LFO) cyan else muted)
        binding.filterModeAuto.setTextColor(if (mode == FilterMode.AUTO) cyan else muted)
    }

    private fun bindDelayControls() {
        binding.delayBeatsSpinner.adapter = spinnerAdapter(filterLfoRateItems.map { it.first })
        binding.delayBeatsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val beats = filterLfoRateItems.getOrNull(position)?.second ?: 0.5f
                engineController.setDelayBeats(beats)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.delayFeedToggle.setOnCheckedChangeListener { _, checked ->
            if (suppressDelayFeedToggle) return@setOnCheckedChangeListener
            engineController.setDelayFeedInput(checked)
        }

        binding.delayFeedbackSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setDelayFeedbackNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.delayWetSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setDelayWetNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
    }

    private fun updateDelayUi() {
        suppressDelayFeedToggle = true
        binding.delayFeedToggle.isChecked = engineController.delayFeedInput()
        suppressDelayFeedToggle = false
        val delayIdx = filterLfoRateItems.indexOfFirst { it.second == engineController.delayBeats() }
        if (delayIdx >= 0) binding.delayBeatsSpinner.setSelection(delayIdx, false)
        binding.delayFeedbackSeek.progress =
            (engineController.delayFeedbackNorm() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.delayWetSeek.progress =
            (engineController.delayWetNorm() * 1000f).roundToInt().coerceIn(0, 1000)
    }

    private fun bindFlangerControls() {
        binding.flangerLfoSpinner.adapter = spinnerAdapter(filterLfoRateItems.map { it.first })
        binding.flangerLfoSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val beats = filterLfoRateItems.getOrNull(position)?.second ?: 0.5f
                engineController.setFlangerLfoBeats(beats)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.flangerBypassToggle.setOnCheckedChangeListener { _, checked ->
            if (suppressFlangerBypassToggle) return@setOnCheckedChangeListener
            // Checked = effect engaged → bypass off
            engineController.setFlangerBypass(!checked)
        }

        binding.flangerBaseSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFlangerBaseNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.flangerSweepSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFlangerSweepNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.flangerManualSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFlangerManualNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.flangerWetSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setFlangerWetNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
    }

    private fun updateFlangerUi() {
        suppressFlangerBypassToggle = true
        binding.flangerBypassToggle.isChecked = !engineController.flangerBypass()
        suppressFlangerBypassToggle = false
        val flIdx = filterLfoRateItems.indexOfFirst { it.second == engineController.flangerLfoBeats() }
        if (flIdx >= 0) binding.flangerLfoSpinner.setSelection(flIdx, false)
        binding.flangerBaseSeek.progress =
            (engineController.flangerBaseNorm() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.flangerSweepSeek.progress =
            (engineController.flangerSweepNorm() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.flangerManualSeek.progress =
            (engineController.flangerManualNorm() * 1000f).roundToInt().coerceIn(0, 1000)
        binding.flangerWetSeek.progress =
            (engineController.flangerWetNorm() * 1000f).roundToInt().coerceIn(0, 1000)
    }

    private fun bindScratchPresetControls() {
        binding.scratchPresetSpinner.adapter = spinnerAdapter(scratchPresetItems.map { it.first })
        binding.scratchPresetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = scratchPresetItems.getOrNull(position)?.second ?: ScratchFeelPreset.CLASSIC
                engineController.setScratchFeelPreset(preset)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val selected = engineController.scratchFeelPreset()
        val idx = scratchPresetItems.indexOfFirst { it.second == selected }
        if (idx >= 0) binding.scratchPresetSpinner.setSelection(idx, false)
    }

    private fun bindFxOrderControls() {
        binding.fxOrderSpinner.adapter = spinnerAdapter(fxOrderItems.map { it.first })
        binding.fxOrderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val mode = fxOrderItems.getOrNull(position)?.second ?: FxOrderMode.SCRATCH_THEN_FX
                engineController.setFxOrderMode(mode)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val selected = engineController.fxOrderMode()
        val idx = fxOrderItems.indexOfFirst { it.second == selected }
        if (idx >= 0) binding.fxOrderSpinner.setSelection(idx, false)
    }

    private fun eqProgressToDb(progress: Int): Float =
        (progress - 500) / 500f * 36f

    private fun eqDbToProgress(db: Float): Int =
        ((db / 36f * 500f) + 500f).roundToInt().coerceIn(0, 1000)

    private fun bindEqControls() {
        binding.eqLowSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressEqSeek || !fromUser) return
                if (eqLowCutEnabled) eqLowCutEnabled = false
                engineController.setEqLowDb(eqProgressToDb(progress))
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.eqMidSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressEqSeek || !fromUser) return
                if (eqMidCutEnabled) eqMidCutEnabled = false
                engineController.setEqMidDb(eqProgressToDb(progress))
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.eqHighSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressEqSeek || !fromUser) return
                if (eqHighCutEnabled) eqHighCutEnabled = false
                engineController.setEqHighDb(eqProgressToDb(progress))
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })

        binding.eqLowKill.setOnClickListener {
            toggleEqBandCut(Band.LOW)
        }
        binding.eqMidKill.setOnClickListener {
            toggleEqBandCut(Band.MID)
        }
        binding.eqHighKill.setOnClickListener {
            toggleEqBandCut(Band.HIGH)
        }
        binding.eqKillAll.setOnClickListener {
            toggleEqAllCuts()
        }
    }

    private fun bindInputGainControl() {
        binding.inputGainFrontSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val gain = progress / 1000f
                engineController.setInputMonitorGain(gain)
                updateInputGainLabel(gain)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        val current = engineController.inputMonitorGain()
        binding.inputGainFrontSeek.progress = (current * 1000f).roundToInt().coerceIn(0, 2000)
        updateInputGainLabel(current)
    }

    private fun updateInputGainLabel(gain: Float) {
        val pct = (gain * 100f).roundToInt()
        binding.inputGainFrontLabel.text = "IN GAIN: ${pct}%"
    }

    private fun updateEqUi() {
        suppressEqSeek = true
        binding.eqLowSeek.progress = eqDbToProgress(engineController.eqLowDb())
        binding.eqMidSeek.progress = eqDbToProgress(engineController.eqMidDb())
        binding.eqHighSeek.progress = eqDbToProgress(engineController.eqHighDb())
        suppressEqSeek = false
    }

    private fun bindScratchSurface() {
        binding.scratchSurface.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    scratchLastY = e.y
                    engineController.setScratchTouchActive(true)
                    pushScratchAxes(e, v, 0f)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dyNorm = ((e.y - scratchLastY) / v.height.toFloat()).coerceIn(-1f, 1f)
                    scratchLastY = e.y
                    pushScratchAxes(e, v, dyNorm)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    engineController.setScratchTouchActive(false)
                }
            }
            true
        }
    }

    private fun bindDoubleTapResets() {
        installDoubleTapReset(binding.masterPitchSeek) {
            binding.masterPitchSeek.progress = 50
            engineController.setMainPitchPercent(0f)
            updatePitchLabels()
        }
        installDoubleTapReset(binding.samplePitchSeek) {
            binding.samplePitchSeek.progress = 50
            engineController.setSamplePitchPercent(0f)
            updatePitchLabels()
        }
        installDoubleTapReset(binding.inputGainFrontSeek) {
            binding.inputGainFrontSeek.progress = 1000
            engineController.setInputMonitorGain(1f)
            updateInputGainLabel(1f)
        }
        installDoubleTapReset(binding.filterCutoffSeek) {
            binding.filterCutoffSeek.progress = 550
            engineController.setFilterCutoffNorm(0.55f)
        }
        installDoubleTapReset(binding.filterResonanceSeek) {
            binding.filterResonanceSeek.progress = 250
            engineController.setFilterResonanceNorm(0.25f)
        }
        installDoubleTapReset(binding.eqLowSeek) {
            eqLowCutEnabled = false
            binding.eqLowSeek.progress = 500
            engineController.setEqLowDb(0f)
        }
        installDoubleTapReset(binding.eqMidSeek) {
            eqMidCutEnabled = false
            binding.eqMidSeek.progress = 500
            engineController.setEqMidDb(0f)
        }
        installDoubleTapReset(binding.eqHighSeek) {
            eqHighCutEnabled = false
            binding.eqHighSeek.progress = 500
            engineController.setEqHighDb(0f)
        }
        installDoubleTapReset(binding.delayFeedbackSeek) {
            binding.delayFeedbackSeek.progress = 600
            engineController.setDelayFeedbackNorm(0.60f)
        }
        installDoubleTapReset(binding.delayWetSeek) {
            binding.delayWetSeek.progress = 350
            engineController.setDelayWetNorm(0.35f)
        }
        installDoubleTapReset(binding.flangerBaseSeek) {
            binding.flangerBaseSeek.progress = 400
            engineController.setFlangerBaseNorm(0.4f)
        }
        installDoubleTapReset(binding.flangerSweepSeek) {
            binding.flangerSweepSeek.progress = 350
            engineController.setFlangerSweepNorm(0.35f)
        }
        installDoubleTapReset(binding.flangerManualSeek) {
            binding.flangerManualSeek.progress = 500
            engineController.setFlangerManualNorm(0.5f)
        }
        installDoubleTapReset(binding.flangerWetSeek) {
            binding.flangerWetSeek.progress = 450
            engineController.setFlangerWetNorm(0.45f)
        }
    }

    private fun installDoubleTapReset(view: View, onReset: () -> Unit) {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onReset()
                return true
            }
        })
        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    override fun onStart() {
        super.onStart()
        refreshLibrarySpinner()
        repo.start { _ ->
            updateDeviceUi()
            binding.root.removeCallbacks(deferredDeviceRouteSync)
            binding.root.post(deferredDeviceRouteSync)
        }
        meterHandler.post(meterTicker)
    }

    override fun onStop() {
        super.onStop()
        hideSubmenu()
        binding.root.removeCallbacks(deferredDeviceRouteSync)
        shotHeld = false
        eqLowCutEnabled = false
        eqMidCutEnabled = false
        eqHighCutEnabled = false
        eqAllCutEnabled = false
        engineController.setShotPressed(false)
        engineController.setEqKillLowHeld(false)
        engineController.setEqKillMidHeld(false)
        engineController.setEqKillHighHeld(false)
        engineController.setEqKillAllHeld(false)
        engineController.setScratchTouchActive(false)
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
            binding.inputSpinner.adapter = spinnerAdapter(inputItems.map { it.first })
            binding.outputSpinner.adapter = spinnerAdapter(outputItems.map { it.first })
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

    private fun updateBpmUi() {
        val bpm = engineController.currentBpm()
        val auto = engineController.autoBpmReading()
        val autoOn = engineController.isBpmAutoFollowEnabled()
        val confPct = (auto.confidence * 100f).toInt().coerceIn(0, 100)
        val mode = if (autoOn) "AUTO" else "MANUAL"
        val lockHint = when {
            auto.confidence >= 0.30f -> "beat lock: strong"
            auto.confidence >= 0.12f -> "beat lock: weak"
            else -> "beat lock: none"
        }
        binding.bpmValue.text = bpm.toInt().toString()
        binding.bpmAutoIndicator.text = mode
        val autoColor = if (autoOn) R.color.accent_led_cyan else R.color.overload
        binding.bpmAutoIndicator.setTextColor(ContextCompat.getColor(this, autoColor))
        binding.bpmStatus.text = "auto ${auto.bpm.toInt()} | confidence $confPct% | $lockHint"
    }

    private fun updateMeterUi() {
        val meter = engineController.meterSnapshot()
        binding.meterStatus.text = "IN ${toPercent(meter.inputPeak)}%  OUT ${toPercent(meter.outputPeak)}%"
        renderLed(binding.ledInputState, meter.inputPeak, meter.inputClipping)
        renderLed(binding.ledOutputState, meter.outputPeak, meter.outputClipping)
    }

    private fun updatePerformanceStateLeds() {
        val state = engineController.recordingState()
        val meter = engineController.meterSnapshot()
        val auto = engineController.autoBpmReading()
        val bpmLockStrength = when {
            auto.confidence >= 0.30f -> 1.0f
            auto.confidence >= 0.12f -> 0.45f
            else -> 0f
        }
        renderLed(
            view = binding.ledRecState,
            peak = if (state.isRecording) 1f else 0f,
            clipping = state.isRecording
        )
        renderLed(
            view = binding.ledClipState,
            peak = if (meter.inputClipping || meter.outputClipping) 1f else 0f,
            clipping = meter.inputClipping || meter.outputClipping
        )
        renderLed(
            view = binding.ledBpmLockState,
            peak = bpmLockStrength,
            clipping = false
        )
        renderLed(
            view = binding.ledFxRouteState,
            peak = if (state.samplerFxRoute == SamplerFxRouteIntent.THROUGH_EFFECTS_PATH) 1f else 0f,
            clipping = false
        )
    }

    private fun updateHoldInteractionUi() {
        val active = ContextCompat.getColor(this, R.color.accent_led_cyan)
        val idle = ContextCompat.getColor(this, R.color.text_muted)
        binding.shotButton.setTextColor(if (shotHeld) active else idle)
        binding.eqLowKill.setTextColor(if (eqLowCutEnabled || eqAllCutEnabled) active else idle)
        binding.eqMidKill.setTextColor(if (eqMidCutEnabled || eqAllCutEnabled) active else idle)
        binding.eqHighKill.setTextColor(if (eqHighCutEnabled || eqAllCutEnabled) active else idle)
        binding.eqKillAll.setTextColor(if (eqAllCutEnabled) active else idle)
    }

    private enum class Band { LOW, MID, HIGH }

    private fun toggleEqBandCut(band: Band) {
        val cutDb = -36f
        when (band) {
            Band.LOW -> {
                if (!eqLowCutEnabled) {
                    eqLowCutEnabled = true
                    engineController.setEqLowDb(cutDb)
                } else {
                    eqLowCutEnabled = false
                    engineController.setEqLowDb(0f)
                }
            }
            Band.MID -> {
                if (!eqMidCutEnabled) {
                    eqMidCutEnabled = true
                    engineController.setEqMidDb(cutDb)
                } else {
                    eqMidCutEnabled = false
                    engineController.setEqMidDb(0f)
                }
            }
            Band.HIGH -> {
                if (!eqHighCutEnabled) {
                    eqHighCutEnabled = true
                    engineController.setEqHighDb(cutDb)
                } else {
                    eqHighCutEnabled = false
                    engineController.setEqHighDb(0f)
                }
            }
        }
        eqAllCutEnabled = eqLowCutEnabled && eqMidCutEnabled && eqHighCutEnabled
        updateEqUi()
    }

    private fun toggleEqAllCuts() {
        eqAllCutEnabled = !eqAllCutEnabled
        if (eqAllCutEnabled) {
            engineController.setEqLowDb(-36f)
            engineController.setEqMidDb(-36f)
            engineController.setEqHighDb(-36f)
            eqLowCutEnabled = true
            eqMidCutEnabled = true
            eqHighCutEnabled = true
        } else {
            engineController.setEqLowDb(0f)
            engineController.setEqMidDb(0f)
            engineController.setEqHighDb(0f)
            eqLowCutEnabled = false
            eqMidCutEnabled = false
            eqHighCutEnabled = false
        }
        updateEqUi()
    }

    private fun pushScratchAxes(e: MotionEvent, v: View, dyNorm: Float) {
        val xn = (e.x / v.width.toFloat()).coerceIn(0f, 1f)
        engineController.setScratchTouchAxes(xn, dyNorm)
    }

    private fun renderLed(view: View, peak: Float, clipping: Boolean) {
        val color = when {
            clipping -> R.color.overload
            peak >= 0.5f -> R.color.led_green
            peak > 0.05f -> R.color.accent_led_cyan
            else -> R.color.led_off
        }
        view.setBackgroundColor(ContextCompat.getColor(this, color))
    }

    private fun updateRecordingUi() {
        val state = engineController.recordingState()
        val hasSample = state.loadedFrameCount > 0
        val sr = state.loadedSampleRateHz
        binding.revToggle.isEnabled = hasSample
        if (!hasSample && binding.revToggle.isChecked) {
            binding.revToggle.isChecked = false
        }

        val modeLine = buildSamplerModeLine(state)
        val elapsedRecSec = framesToSeconds(state.progressFrames, sr)
        val totalRecSec = framesToSeconds(state.targetFrames, sr)
        val remainRecSec = (totalRecSec - elapsedRecSec).coerceAtLeast(0.0)
        val loadedDurSec = framesToSeconds(state.loadedFrameCount, sr)
        val playheadSec = when {
            state.loopPlayheadFrame >= 0 && state.loadedFrameCount > 0 ->
                framesToSeconds(state.loopPlayheadFrame, sr)
            state.shotPlayheadFrame >= 0 && state.loadedFrameCount > 0 ->
                framesToSeconds(state.shotPlayheadFrame, sr)
            else -> null
        }

        val pct = if (state.targetFrames > 0) {
            ((state.progressFrames.coerceAtMost(state.targetFrames).toFloat() / state.targetFrames) * 100f)
                .roundToInt()
        } else {
            0
        }

        val recColor = ContextCompat.getColor(this, R.color.overload)
        val normalColor = ContextCompat.getColor(this, R.color.text_primary)
        when {
            state.isRecording && state.targetFrames > 0 -> {
                binding.recQuantized.setTextColor(recColor)
                binding.recQuantized.text = "REC ${pct}%"
            }
            else -> {
                binding.recQuantized.setTextColor(normalColor)
                binding.recQuantized.setText(R.string.rec_quantized)
            }
        }

        updateTransportProgress(state, hasSample, sr)

        binding.recordingStatus.text = buildString {
            append("${ManualLabels.DISPLAY} — $modeLine")
            append("\n")
            if (state.isRecording && state.targetFrames > 0) {
                append(
                    "Progress: $pct%  (${formatMmSs(elapsedRecSec)} / ${formatMmSs(totalRecSec)})  "
                )
                append("remaining ${formatMmSs(remainRecSec)}")
                if (state.captureKind == SamplerCaptureKind.QUANTIZED) {
                    append("  @ ${state.recordingBpm.toInt()} BPM")
                }
            } else if (hasSample) {
                append("Sample length: ${formatMmSs(loadedDurSec)}")
                if (state.lastQuantizedBars != null) {
                    append("  (last ${ManualLabels.REC}: ${state.lastQuantizedBars.bars} bar)")
                }
                if (playheadSec != null && (state.isPlaybackLooping || state.isShotActive)) {
                    append("  playhead ${formatMmSs(playheadSec)}")
                }
            } else {
                append("No sample loaded — use ${ManualLabels.REC}")
            }
            append("\nLoaded frames: ${state.loadedFrameCount}  (${sr} Hz)")
            append("\n${ManualLabels.PLAY_STOP}: ${if (state.isPlaybackLooping) "ON (loop)" else "OFF"}")
            append("\n${ManualLabels.SHOT}: ${if (state.isShotActive) "ON (held)" else "OFF"}")
            val routeLabel = when (state.samplerFxRoute) {
                SamplerFxRouteIntent.THROUGH_EFFECTS_PATH -> "effects bus (E4 hook)"
                SamplerFxRouteIntent.DIRECT_TO_MONITOR_MIX -> "direct (bypass FX/master pitch)"
            }
            append("\n${ManualLabels.FX_ROUTE}: $routeLabel")
            append("\n${ManualLabels.REV}: ${if (state.isReversePlayback) "backward" else "forward"}")
            append("\nMASTER ${ManualLabels.PITCH}: ${formatPitchPercent(state.mainPitchPercent)}")
            append("\nSAMPLE ${ManualLabels.PITCH}: ${formatPitchPercent(state.samplePitchPercent)}")
        }
    }

    private fun mainPitchMaxAbsPercentForUi(): Float = when (engineController.mainPitchRangeMode()) {
        MainPitchRangeMode.PERCENT_12 -> 12f
        MainPitchRangeMode.PERCENT_24 -> 24f
        MainPitchRangeMode.PERCENT_50 -> 50f
    }

    private fun progressToMainPitchPercent(progress: Int): Float {
        val max = mainPitchMaxAbsPercentForUi()
        return (progress - 50) / 50f * max
    }

    private fun mainPitchPercentToProgress(percent: Float): Int {
        val max = mainPitchMaxAbsPercentForUi()
        if (max <= 1e-6f) return 50
        val p = 50f + (percent / max) * 50f
        return p.roundToInt().coerceIn(0, 100)
    }

    private fun syncMasterPitchSeekFromEngine() {
        binding.masterPitchSeek.progress = mainPitchPercentToProgress(engineController.mainPitchPercent())
    }

    private fun bindMainMixControl() {
        binding.mainMixSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setMainMixNorm(progress / 1000f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        binding.mainMixSeek.progress =
            (engineController.mainMixNorm() * 1000f).roundToInt().coerceIn(0, 1000)
    }

    private fun bindMasterPitchRangeSpinner() {
        binding.masterPitchRangeSpinner.adapter = spinnerAdapter(mainPitchRangeItems.map { it.first })
        binding.masterPitchRangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressMasterPitchRangeSpinner) return
                val mode = mainPitchRangeItems.getOrNull(position)?.second ?: return
                engineController.setMainPitchRangeMode(mode)
                syncMasterPitchSeekFromEngine()
                updatePitchLabels()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        suppressMasterPitchRangeSpinner = true
        val sel = mainPitchRangeItems.indexOfFirst { it.second == engineController.mainPitchRangeMode() }
            .let { if (it >= 0) it else 0 }
        binding.masterPitchRangeSpinner.setSelection(sel, false)
        suppressMasterPitchRangeSpinner = false
    }

    private fun updateTransportProgress(state: SamplerRecordingState, hasSample: Boolean, sr: Int) {
        val loopBars = state.lastQuantizedBars?.bars ?: selectedBars().bars
        val bpmLive = engineController.currentBpm()
        when {
            state.isRecording && state.targetFrames > 0 -> {
                val n = (state.progressFrames.coerceAtMost(state.targetFrames).toFloat() / state.targetFrames)
                    .coerceIn(0f, 1f)
                binding.transportGridProgress.visibility = View.VISIBLE
                binding.transportGridProgress.setTransportState(
                    progressNormalized = n,
                    recordingProgress = true,
                    showBeatGrid = false,
                    bpmValue = state.recordingBpm,
                    sampleDurationSec = framesToSeconds(state.targetFrames, sr),
                    loopBarsSelection = loopBars
                )
            }
            (state.isPlaybackLooping || state.isShotActive) && hasSample && state.loadedFrameCount > 0 -> {
                val ph = when {
                    state.isPlaybackLooping && state.loopPlayheadFrame >= 0 -> state.loopPlayheadFrame
                    state.shotPlayheadFrame >= 0 -> state.shotPlayheadFrame
                    else -> 0
                }
                val n = (ph.toFloat() / state.loadedFrameCount).coerceIn(0f, 1f)
                binding.transportGridProgress.visibility = View.VISIBLE
                binding.transportGridProgress.setTransportState(
                    progressNormalized = n,
                    recordingProgress = false,
                    showBeatGrid = true,
                    bpmValue = bpmLive,
                    sampleDurationSec = framesToSeconds(state.loadedFrameCount, sr),
                    loopBarsSelection = loopBars
                )
            }
            else -> binding.transportGridProgress.visibility = View.GONE
        }
    }

    private fun bindPitchControls() {
        val masterListener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setMainPitchPercent(progressToMainPitchPercent(progress))
                updatePitchLabels()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        }
        val sampleListener = object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                engineController.setSamplePitchPercent(progress - 50f)
                updatePitchLabels()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        }
        binding.masterPitchSeek.setOnSeekBarChangeListener(masterListener)
        binding.samplePitchSeek.setOnSeekBarChangeListener(sampleListener)
        binding.masterPitchReset.setOnClickListener {
            binding.masterPitchSeek.progress = 50
            engineController.setMainPitchPercent(0f)
            updatePitchLabels()
        }
        syncMasterPitchSeekFromEngine()
        binding.samplePitchReset.setOnClickListener {
            binding.samplePitchSeek.progress = 50
            engineController.setSamplePitchPercent(0f)
            updatePitchLabels()
        }
    }

    private fun updatePitchLabels() {
        val mp = engineController.mainPitchPercent()
        val sp = engineController.samplePitchPercent()
        val span = mainPitchMaxAbsPercentForUi().roundToInt()
        binding.masterPitchValue.text =
            "MASTER ${ManualLabels.PITCH}: ${formatPitchPercent(mp)} (±${span}%, pitch-only)"
        binding.samplePitchValue.text =
            "SAMPLE ${ManualLabels.PITCH}: ${formatPitchPercent(sp)} (pitch+tempo)"
    }

    private fun formatPitchPercent(p: Float): String {
        val v = p.roundToInt()
        val sign = if (v > 0) "+" else ""
        return "$sign$v%"
    }

    private fun buildSamplerModeLine(state: SamplerRecordingState): String {
        return when {
            state.isRecording && state.captureKind == SamplerCaptureKind.QUANTIZED ->
                "${ManualLabels.REC} quantized (${state.bars?.bars ?: "?"} bar @ ${state.recordingBpm.toInt()} BPM)"
            state.isRecording && state.captureKind == SamplerCaptureKind.FREE ->
                "${ManualLabels.REC} free (capped)"
            state.isShotActive -> "SHOT (momentary)"
            state.isPlaybackLooping -> "${ManualLabels.PLAY_STOP} loop"
            hasLoadedSample(state) -> "Sample loaded (idle)"
            else -> "Idle (monitor only)"
        }
    }

    private fun hasLoadedSample(state: SamplerRecordingState): Boolean =
        state.loadedFrameCount > 0

    private fun framesToSeconds(frames: Int, sampleRateHz: Int): Double {
        if (sampleRateHz <= 0 || frames <= 0) return 0.0
        return frames.toDouble() / sampleRateHz
    }

    /** Whole seconds shown as m:ss (sufficient for tablet status; sub-second via progress bar). */
    private fun formatMmSs(seconds: Double): String {
        if (seconds.isNaN() || seconds < 0) return "0:00"
        val total = seconds.roundToInt().coerceAtMost(359_999)
        val m = total / 60
        val s = total % 60
        return String.format("%d:%02d", m, s)
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

    private fun bindSampleLibraryUi() {
        binding.saveSampleLibrary.setOnClickListener { saveCurrentSampleToLibrary() }
        binding.loadSampleLibrary.setOnClickListener { loadSelectedLibrarySample() }
        binding.libraryFavoriteToggle.setOnCheckedChangeListener { _, checked ->
            if (suppressFavoriteToggle) return@setOnCheckedChangeListener
            val id = selectedLibraryIdOrNull() ?: return@setOnCheckedChangeListener
            sampleLibrary.setFavorite(id, checked).fold(
                onSuccess = {
                    refreshLibrarySpinner()
                    selectLibraryById(id)
                    binding.libraryStatus.text =
                        if (checked) "Marked favorite" else "Removed favorite"
                },
                onFailure = { e ->
                    binding.libraryStatus.text =
                        "Favorite failed: ${e.message ?: e.javaClass.simpleName}"
                    syncLibraryFavoriteToggleFromSelection()
                }
            )
        }
        binding.libraryRenameButton.setOnClickListener {
            val id = selectedLibraryIdOrNull() ?: run {
                binding.libraryStatus.text = "Select a saved clip first."
                return@setOnClickListener
            }
            val raw = binding.libraryRenameField.text?.toString()?.trim().orEmpty()
            if (raw.isEmpty()) {
                binding.libraryStatus.text = "Enter a new name to rename."
                return@setOnClickListener
            }
            sampleLibrary.renameClip(id, raw).fold(
                onSuccess = {
                    binding.libraryRenameField.text?.clear()
                    refreshLibrarySpinner()
                    selectLibraryById(id)
                    binding.libraryStatus.text = "Renamed clip"
                },
                onFailure = { e ->
                    binding.libraryStatus.text =
                        "Rename failed: ${e.message ?: e.javaClass.simpleName}"
                }
            )
        }
        binding.libraryDeleteButton.setOnClickListener {
            val id = selectedLibraryIdOrNull() ?: run {
                binding.libraryStatus.text = "Select a saved clip first."
                return@setOnClickListener
            }
            sampleLibrary.deleteClip(id).fold(
                onSuccess = {
                    clearLastLibrarySampleIdIfMatches(id)
                    refreshLibrarySpinner()
                    binding.libraryStatus.text = "Deleted clip"
                },
                onFailure = { e ->
                    binding.libraryStatus.text =
                        "Delete failed: ${e.message ?: e.javaClass.simpleName}"
                }
            )
        }
    }

    private fun selectedLibraryIdOrNull(): String? {
        if (libraryEntries.isEmpty()) return null
        val idx = binding.librarySpinner.selectedItemPosition
        return libraryEntries.getOrNull(idx)?.id
    }

    private fun selectLibraryById(id: String) {
        val idx = libraryEntries.indexOfFirst { it.id == id }
        if (idx >= 0) {
            binding.librarySpinner.setSelection(idx, false)
        }
        syncLibraryFavoriteToggleFromSelection()
    }

    private fun syncLibraryFavoriteToggleFromSelection() {
        val e = libraryEntries.getOrNull(binding.librarySpinner.selectedItemPosition)
        suppressFavoriteToggle = true
        binding.libraryFavoriteToggle.isChecked = e?.favorite == true
        suppressFavoriteToggle = false
    }

    private fun clearLastLibrarySampleIdIfMatches(deletedId: String) {
        val p = getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        if (p.getString(KEY_LAST_LIBRARY_ID, null) == deletedId) {
            p.edit().remove(KEY_LAST_LIBRARY_ID).apply()
        }
    }

    private fun refreshLibrarySpinner() {
        libraryEntries = sampleLibrary.listEntries()
        val labels = if (libraryEntries.isEmpty()) {
            listOf(getString(R.string.library_empty_spinner))
        } else {
            libraryEntries.map { e ->
                if (e.favorite) "★ ${e.displayName}" else e.displayName
            }
        }
        binding.librarySpinner.onItemSelectedListener = null
        binding.librarySpinner.adapter = spinnerAdapter(labels)
        binding.librarySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                syncLibraryFavoriteToggleFromSelection()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.loadSampleLibrary.isEnabled = libraryEntries.isNotEmpty()
        val hasClips = libraryEntries.isNotEmpty()
        binding.libraryFavoriteToggle.isEnabled = hasClips
        binding.libraryRenameButton.isEnabled = hasClips
        binding.libraryDeleteButton.isEnabled = hasClips
        syncLibraryFavoriteToggleFromSelection()
    }

    private fun defaultCaptureName(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return "Capture ${fmt.format(Date())}"
    }

    private fun saveCurrentSampleToLibrary() {
        val snap = engineController.snapshotLoadedSampleForExport()
        if (snap == null) {
            binding.libraryStatus.text = "Nothing to save — record or load a sample first."
            return
        }
        val state = engineController.recordingState()
        val rawName = binding.saveSampleName.text?.toString()?.trim().orEmpty()
        val name = rawName.ifEmpty { defaultCaptureName() }
        val sr = snap.sampleRateHz
        val dur = if (sr > 0) snap.frameCount.toDouble() / sr else 0.0
        val meta = SavedSampleMetadata(
            name = name,
            createdAtEpochMillis = System.currentTimeMillis(),
            bpmAtRecord = state.bpmAtLastCapture ?: state.recordingBpm,
            durationSeconds = dur,
            reverse = state.isReversePlayback,
            mainPitchPercent = state.mainPitchPercent,
            samplePitchPercent = state.samplePitchPercent,
            quantizedBars = state.lastQuantizedBars?.bars
        )
        val wav = WavPcmIo.encodePcm16LeWav(snap)
        sampleLibrary.save(wav, meta).fold(
            onSuccess = {
                persistLastLibrarySampleId(it.id)
                refreshLibrarySpinner()
                binding.libraryStatus.text = "Saved: ${it.displayName}"
            },
            onFailure = { e ->
                binding.libraryStatus.text = "Save failed: ${e.message ?: e.javaClass.simpleName}"
            }
        )
    }

    private fun loadSelectedLibrarySample() {
        if (libraryEntries.isEmpty()) return
        val idx = binding.librarySpinner.selectedItemPosition
        if (idx !in libraryEntries.indices) return
        val id = libraryEntries[idx].id
        try {
            val (buf, meta) = sampleLibrary.loadBufferAndMetadata(id)
            applyLoadedSampleFromMetadata(buf, meta)
            persistLastLibrarySampleId(id)
            binding.libraryStatus.text = "Loaded: ${meta.name}"
            updatePitchLabels()
        } catch (e: Exception) {
            binding.libraryStatus.text = "Load failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun applyLoadedSampleFromMetadata(buf: SamplerBuffer, meta: SavedSampleMetadata) {
        val bars = meta.quantizedBars?.let { q -> QuantizedBars.fromBarCount(q) }
        engineController.loadPresetSample(
            buffer = buf,
            lastQuantizedBars = bars,
            bpmAtCapture = meta.bpmAtRecord
        )
        engineController.setReversePlayback(meta.reverse)
        engineController.setMainPitchPercent(meta.mainPitchPercent)
        engineController.setSamplePitchPercent(meta.samplePitchPercent)
        syncMasterPitchSeekFromEngine()
        binding.samplePitchSeek.progress =
            (50 + meta.samplePitchPercent.roundToInt()).coerceIn(0, 100)
        engineController.setLoopPlayback(false)
        binding.revToggle.isChecked = meta.reverse
    }

    private fun persistLastLibrarySampleId(id: String) {
        getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_LIBRARY_ID, id)
            .apply()
    }

    /** E6-S4: cold start restores last loaded/saved library clip if files still exist. */
    private fun restoreLastLibrarySample(): Boolean {
        val id = getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LIBRARY_ID, null) ?: return false
        return try {
            val (buf, meta) = sampleLibrary.loadBufferAndMetadata(id)
            applyLoadedSampleFromMetadata(buf, meta)
            binding.libraryStatus.text = "Restored: ${meta.name}"
            refreshLibrarySpinner()
            val idx = libraryEntries.indexOfFirst { it.id == id }
            if (idx >= 0) binding.librarySpinner.setSelection(idx, false)
            updatePitchLabels()
            true
        } catch (_: Exception) {
            getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_LAST_LIBRARY_ID)
                .apply()
            false
        }
    }

    companion object {
        private const val REQ_MIC = 1001
        private const val SESSION_PREFS = "freekalizer_session"
        private const val KEY_LAST_LIBRARY_ID = "last_library_sample_id"
    }
}
