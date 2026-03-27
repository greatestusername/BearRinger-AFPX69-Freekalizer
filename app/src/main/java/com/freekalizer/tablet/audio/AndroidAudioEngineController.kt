package com.freekalizer.tablet.audio

import android.content.Context
import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.AudioEngine
import com.freekalizer.audio.AudioEngineConfig
import com.freekalizer.audio.AudioEngineState
import com.freekalizer.sampler.FrameCappedSamplerRecorder
import com.freekalizer.sampler.FreeRecordMath
import com.freekalizer.sampler.LoopBpmMath
import com.freekalizer.sampler.QuantizedBars
import com.freekalizer.sampler.QuantizedLoopMath
import com.freekalizer.sampler.QuantizedSamplerRecorder
import com.freekalizer.sampler.SamplerBuffer
import com.freekalizer.sampler.SamplerFxRouteIntent
import com.freekalizer.sampler.SamplerLoopPlayer
import com.freekalizer.sampler.toSamplerBuffer
import com.freekalizer.effects.BiquadMonoFilter
import com.freekalizer.effects.DelayBeatMath
import com.freekalizer.effects.FilterMode
import com.freekalizer.effects.FilterType
import com.freekalizer.effects.FlangerBeatMath
import com.freekalizer.effects.HallReverbStereo
import com.freekalizer.effects.InterleavedFeedbackDelay
import com.freekalizer.effects.ScratchRingBuffer
import com.freekalizer.effects.TapeWowFlutterStereo
import com.freekalizer.effects.StereoFlanger
import com.freekalizer.effects.ThreeBandStereoEq
import com.freekalizer.pitch.PitchKnobMath
import com.freekalizer.pitch.StreamingCheapPitchShifterMono
import com.freekalizer.timing.AutoBpmEstimator
import com.freekalizer.timing.AutoBpmReading
import com.freekalizer.timing.InternalBpmTimingSource
import com.freekalizer.timing.TapBpmEstimator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.pow

class AndroidAudioEngineController(
    context: Context
) {
    private val backend = AndroidAudioBackend(context)
    private val engine: AudioEngine = AudioEngine(backend)

    private var currentConfig: AudioEngineConfig? = null

    // Stored so UI selection changes can rebind safely without duplicating state logic.
    @Volatile
    private var preferredInputId: Int? = null

    @Volatile
    private var preferredOutputId: Int? = null

    @Volatile
    private var recorder: QuantizedSamplerRecorder? = null

    @Volatile
    private var freeRecorder: FrameCappedSamplerRecorder? = null

    @Volatile
    private var recordingBars: QuantizedBars? = null

    @Volatile
    private var recordingCaptureKind: SamplerCaptureKind? = null

    @Volatile
    private var recordingTargetFrames: Int = 0

    @Volatile
    private var recordingProgressFrames: Int = 0

    @Volatile
    private var loadedSample: SamplerBuffer? = null

    @Volatile
    private var lastQuantizedBars: QuantizedBars? = null

    /** BPM when [loadedSample] was last produced by recording (not preset/library). */
    @Volatile
    private var bpmAtLastCapture: Double? = null

    private val loopPlayer = SamplerLoopPlayer()

    /** High overlap (4096/256) for smoother PV; master pitch is pitch-only (no tempo change). */
    private val mainPitchShifterMonitor = StreamingCheapPitchShifterMono(fftSize = 4096, hop = 256)
    private val mainPitchShifterFxBus = StreamingCheapPitchShifterMono(fftSize = 4096, hop = 256)

    /** E4-S6: output-path EQ (applied after dry + sampler/FX sum). */
    private val monitorEq = ThreeBandStereoEq()

    @Volatile
    private var eqLowDb: Float = 0f

    @Volatile
    private var eqMidDb: Float = 0f

    @Volatile
    private var eqHighDb: Float = 0f

    @Volatile
    private var eqParamsDirty: Boolean = true

    // E4-S1 filter on the FX bus (LP/HP/BP).
    private val filterL = BiquadMonoFilter()
    private val filterR = BiquadMonoFilter()

    @Volatile
    private var filterType: FilterType = FilterType.LOW_PASS

    @Volatile
    private var filterMode: FilterMode = FilterMode.MANUAL

    @Volatile
    private var filterCutoffNorm: Float = 0.55f // 0..1 mapped to log Hz

    @Volatile
    private var filterResonanceNorm: Float = 0.25f // 0..1 mapped to Q

    @Volatile
    private var filterLfoDepth: Float = 0.5f // 0..1

    @Volatile
    private var filterLfoPeriodBeats: Float = 1.0f // 0.25, 0.5, 1, 2, 4

    @Volatile
    private var filterAutoDepth: Float = 1.0f // 0..1

    // Auto envelope follower (input-driven) — updated on audio thread.
    private var filterEnv: Float = 0f

    // LFO state — updated on audio thread.
    private var filterLfoPhaseRad: Double = 0.0

    @Volatile
    private var filterParamsDirty: Boolean = true

    private var monoPitchScratch: FloatArray = FloatArray(0)
    private var monoPitchOutScratch: FloatArray = FloatArray(0)
    /** Pre–master-pitch monitor (input × monitor gain) before PV into [output]. */
    private var monitorBaseScratch: FloatArray = FloatArray(0)

    @Volatile
    private var mainPitchPercent: Float = 0f

    @Volatile
    private var samplePitchPercent: Float = 0f

    @Volatile
    private var mainPitchRangeMode: MainPitchRangeMode = MainPitchRangeMode.PERCENT_12

    /** Sampler + FX path level into main sum (dry monitor unchanged). Default 80%. */
    @Volatile
    private var mainMixNorm: Float = 0.80f

    @Volatile
    private var inputMonitorGain: Float = 0f

    /** Audio-thread slew toward target ratio (reduces zipper/robot on knob moves). */
    private var mainPitchRatioSlewed: Float = 1f

    /** Slow LFO phase for tiny pitch wander (vinyl-ish) when shifted away from unity. */
    private var mainPitchWowPhase: Double = 0.0

    /**
     * Scratch bus for sampler audio when [samplerFxRoute] is [SamplerFxRouteIntent.THROUGH_EFFECTS_PATH].
     * Sized once per [startMonitoring] from burst × channels; no per-callback allocations.
     */
    private var fxBusScratch: FloatArray = FloatArray(0)

    /** E4-S3 BPM-timed echo on FX bus (after filter, before master pitch). Null when stopped. */
    private var fxDelay: InterleavedFeedbackDelay? = null

    @Volatile
    private var delayFeedInput: Boolean = true

    @Volatile
    private var delayBeats: Float = 0.5f

    @Volatile
    private var delayFeedbackNorm: Float = 0.60f

    @Volatile
    private var delayWetNorm: Float = 0.35f

    /** E4-S4 flanger after delay, before master pitch. */
    private var fxFlanger: StereoFlanger? = null

    /** Hall/cathedral style stereo reverb after bus filter (FX chain tail). */
    private var fxReverb: HallReverbStereo? = null

    /** Tape-style wow / flutter before reverb on FX bus. */
    private var fxTapeWow: TapeWowFlutterStereo? = null

    @Volatile
    private var reverbEnabled: Boolean = true

    @Volatile
    private var reverbCathedral: Boolean = false

    @Volatile
    private var reverbDecayNorm: Float = 0.55f

    @Volatile
    private var reverbMixNorm: Float = 0.20f

    @Volatile
    private var tapeWowEnabled: Boolean = false

    @Volatile
    private var tapeWowDepthNorm: Float = 0.40f

    @Volatile
    private var tapeFlutterDepthNorm: Float = 0.45f

    @Volatile
    private var tapeMixNorm: Float = 0.42f

    /** E4-S5 scratch ring (post-delay tap on FX bus). */
    private var scratchRing: ScratchRingBuffer? = null

    @Volatile
    private var scratchTouchActive: Boolean = false

    private var scratchLagFrames: Float = 0f
    private var scratchRateCurrent: Float = 0f
    private var scratchVolumeCurrent: Float = 1f

    @Volatile
    private var scratchRateTarget: Float = 0f

    @Volatile
    private var scratchVolumeTarget: Float = 1f

    @Volatile
    private var scratchFeelPreset: ScratchFeelPreset = ScratchFeelPreset.CLASSIC

    @Volatile
    private var fxOrderMode: FxOrderMode = FxOrderMode.SCRATCH_FX_FILTER_TAPE_REVERB

    /** When true, flanger is bypassed (dry only; LFO/ring still advance). */
    @Volatile
    private var flangerBypass: Boolean = true

    @Volatile
    private var flangerLfoBeats: Float = 0.5f

    /** 0..1 → ~1–6 ms base delay. */
    @Volatile
    private var flangerBaseNorm: Float = 0.4f

    /** 0..1 → up to ~4 ms LFO sweep. */
    @Volatile
    private var flangerSweepNorm: Float = 0.35f

    /** 0..1 → ~−2..+2 ms manual offset (0.5 center). */
    @Volatile
    private var flangerManualNorm: Float = 0.5f

    @Volatile
    private var flangerWetNorm: Float = 0.45f

    @Volatile
    private var samplerFxRoute: SamplerFxRouteIntent = SamplerFxRouteIntent.THROUGH_EFFECTS_PATH

    /**
     * Opening/closing streams can post AudioManager device callbacks on the main looper; those may
     * call back into UI that invokes [rebindIfRunning] again. Suppress nested rebinds.
     */
    @Volatile
    private var rebindInProgress: Boolean = false

    private val internalBpm = InternalBpmTimingSource(initialBpm = 120.0)
    private val tapBpmEstimator = TapBpmEstimator()
    private val autoBpmEstimator = AutoBpmEstimator()

    /** Onset BPM from sampler output (loop/SHOT); live input uses [autoBpmEstimator]. */
    private val autoBpmSampleEstimator = AutoBpmEstimator()

    /**
     * Audio callback only: last ingest source for AUTO BPM edge detection (clear stale onsets when switching).
     */
    private var lastBpmIngestSourceWasSample: Boolean = false

    @Volatile
    private var bpmAutoFollowEnabled: Boolean = false

    /**
     * Smoothed BPM track for AUTO follow (audio thread). Avoids hammering [internalBpm] with noisy estimates.
     */
    @Volatile
    private var autoBpmEma: Double = 120.0

    companion object {
        /**
         * When [bpmAutoFollowEnabled], apply smoothed auto BPM at or above this confidence.
         * Kept low so real program material (not only test impulses) can move the clock; see BPM status %.
         */
        private const val AUTO_BPM_APPLY_CONFIDENCE: Float = 0.035f

        /**
         * After loading a WAV/sample buffer on the UI thread, apply detected BPM when the offline
         * onset analysis is confident enough; otherwise use [loadPresetSample]'s fallback BPM when provided.
         */
        private const val SAMPLE_LOAD_BPM_MIN_CONFIDENCE: Float = 0.08f

        /** Max delay line length (covers e.g. 4 beats @ 40 BPM ≈ 6 s). */
        private const val DELAY_MAX_SECONDS: Int = 6

        /** Flanger ring buffer length in frames (short delay + sweep + margin). */
        private const val FLANGER_CAP_FRAMES: Int = 2048

        /** Limit scratch lag range to a vinyl-like window with enough bidirectional headroom. */
        private const val SCRATCH_MAX_LAG_SECONDS: Float = 1.5f
        /** Stability margin under Nyquist for cutoff calculations at the top end. */
        private const val FILTER_MAX_NYQUIST_RATIO: Float = 0.82f

        /** Master pitch ratio slew ~63% settle time (seconds). */
        private const val MAIN_PITCH_SLEW_TAU_SEC: Float = 0.034f

        /** Very slow wow when master pitch is active (Hz). */
        private const val MAIN_PITCH_WOW_HZ: Double = 0.38

        /** Subtle pitch wander vs unity (scaled by how much shift is applied). */
        private const val MAIN_PITCH_WOW_DEPTH: Float = 0.0032f
    }

    private fun mainPitchMaxAbsPercent(): Float = when (mainPitchRangeMode) {
        MainPitchRangeMode.PERCENT_12 -> 12f
        MainPitchRangeMode.PERCENT_24 -> 24f
        MainPitchRangeMode.PERCENT_50 -> 50f
    }

    /** Slew + tiny wow toward [mainPitchPercent]; used for both monitor and FX-bus shifters. */
    private fun effectiveMasterPitchRatio(frameCount: Int, sampleRateHz: Int): Float {
        val target = PitchKnobMath.toPitchShiftRatio(mainPitchPercent)
        val sr = sampleRateHz.toFloat().coerceAtLeast(1f)
        val dt = frameCount.toFloat() / sr
        val aSlew = if (dt > 0f) (1f - exp(-dt / MAIN_PITCH_SLEW_TAU_SEC)).coerceIn(0f, 1f) else 0f
        mainPitchRatioSlewed += aSlew * (target - mainPitchRatioSlewed)

        val dev = abs(target - 1f)
        val wowAmt = (dev / 0.035f).coerceIn(0f, 1f)
        mainPitchWowPhase += 2.0 * PI * MAIN_PITCH_WOW_HZ * (frameCount.toDouble() / sampleRateHz.toDouble().coerceAtLeast(1.0))
        while (mainPitchWowPhase >= 2.0 * PI) mainPitchWowPhase -= 2.0 * PI
        while (mainPitchWowPhase < 0.0) mainPitchWowPhase += 2.0 * PI
        val wow = 1f + (MAIN_PITCH_WOW_DEPTH * wowAmt * sin(mainPitchWowPhase)).toFloat()
        return (mainPitchRatioSlewed * wow).coerceIn(0.5f, 1.5f)
    }

    enum class ScratchFeelPreset {
        SMOOTH,
        CLASSIC,
        CUT
    }

    enum class MainPitchRangeMode {
        PERCENT_12,
        PERCENT_24,
        PERCENT_50
    }

    /**
     * FX-bus ordering after sampler + main pitch: scratch vs delay/flanger, then multiband filter
     * (same placement as today), then **Tape/Wow-Flutter** and **Hall reverb** in either order.
     */
    enum class FxOrderMode(
        val scratchBeforeDelayFlanger: Boolean,
        val tapeWowFlutterBeforeReverb: Boolean,
    ) {
        SCRATCH_FX_FILTER_TAPE_REVERB(true, true),
        SCRATCH_FX_FILTER_REVERB_TAPE(true, false),
        FX_SCRATCH_FILTER_TAPE_REVERB(false, true),
        FX_SCRATCH_FILTER_REVERB_TAPE(false, false),
    }

    private fun scratchRateRangeForPreset(): Float = when (scratchFeelPreset) {
        ScratchFeelPreset.SMOOTH -> 70f
        ScratchFeelPreset.CLASSIC -> 110f
        ScratchFeelPreset.CUT -> 150f
    }

    private fun scratchRateSlewPerFrameForPreset(): Float = when (scratchFeelPreset) {
        ScratchFeelPreset.SMOOTH -> 0.08f
        ScratchFeelPreset.CLASSIC -> 0.14f
        ScratchFeelPreset.CUT -> 0.24f
    }

    private fun scratchVolumeSlewPerFrameForPreset(): Float = when (scratchFeelPreset) {
        ScratchFeelPreset.SMOOTH -> 0.10f
        ScratchFeelPreset.CLASSIC -> 0.18f
        ScratchFeelPreset.CUT -> 0.28f
    }

    private fun scratchLagDecayPerBurstForPreset(): Float = when (scratchFeelPreset) {
        ScratchFeelPreset.SMOOTH -> 0.92f
        ScratchFeelPreset.CLASSIC -> 0.88f
        ScratchFeelPreset.CUT -> 0.82f
    }

    fun startMonitoring() {
        if (engine.state != AudioEngineState.STOPPED) {
            return
        }

        val config = AudioEngineConfig(
            sampleRateHz = AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ,
            framesPerBurst = AndroidAudioDefaults.LOW_LATENCY_FRAMES_PER_BURST,
            inputChannels = 1,
            outputChannels = 2
        )

        currentConfig = config
        fxBusScratch = FloatArray(config.framesPerBurst * config.outputChannels)
        monitorBaseScratch = FloatArray(config.framesPerBurst * config.outputChannels)
        monoPitchScratch = FloatArray(config.framesPerBurst)
        monoPitchOutScratch = FloatArray(config.framesPerBurst)
        fxDelay = InterleavedFeedbackDelay(
            channels = config.outputChannels,
            maxDelayFrames = config.sampleRateHz * DELAY_MAX_SECONDS
        ).also { it.reset() }
        fxFlanger = StereoFlanger(
            channels = config.outputChannels,
            capFrames = FLANGER_CAP_FRAMES
        ).also { it.reset() }
        scratchRing = ScratchRingBuffer(ScratchRingBuffer.DEFAULT_CAP_FRAMES).also { it.reset() }
        fxReverb = HallReverbStereo(config.sampleRateHz).also { it.reset() }
        fxTapeWow = TapeWowFlutterStereo(config.sampleRateHz).also { it.reset() }
        scratchLagFrames = 0f
        scratchRateCurrent = 0f
        scratchRateTarget = 0f
        scratchVolumeCurrent = 1f
        scratchVolumeTarget = 1f
        scratchTouchActive = false
        monitorEq.reset()
        eqParamsDirty = true
        mainPitchShifterMonitor.reset()
        mainPitchShifterFxBus.reset()
        mainPitchRatioSlewed = PitchKnobMath.toPitchShiftRatio(mainPitchPercent)
        mainPitchWowPhase = 0.0
        filterL.reset()
        filterR.reset()
        filterEnv = 0f
        filterLfoPhaseRad = 0.0
        val (cut, qInit) = computeFilterCutoffAndQ(config.sampleRateHz)
        filterL.setCoefficients(filterType, config.sampleRateHz, cut, qInit)
        filterR.setCoefficients(filterType, config.sampleRateHz, cut, qInit)
        filterParamsDirty = false
        loopPlayer.setSamplePitchPercent(samplePitchPercent)
        autoBpmEstimator.configure(config.sampleRateHz)
        autoBpmEstimator.reset()
        autoBpmSampleEstimator.configure(config.sampleRateHz)
        autoBpmSampleEstimator.reset()
        lastBpmIngestSourceWasSample = false
        autoBpmEma = internalBpm.currentBpm()

        engine.start(config) { input, output, frameCount ->
            val qRec = recorder
            val fRec = freeRecorder
            if (qRec != null) {
                qRec.appendInterleaved(input, frameCount)
                recordingProgressFrames += frameCount
                if (qRec.isComplete()) {
                    val done = qRec.completeOrNull()
                    recorder = null
                    recordingBars = null
                    recordingCaptureKind = null
                    recordingTargetFrames = 0
                    recordingProgressFrames = 0
                    if (done != null) {
                        lastQuantizedBars = done.bars
                        val buf = done.toSamplerBuffer()
                        loadedSample = buf
                        bpmAtLastCapture = internalBpm.currentBpm()
                        loopPlayer.load(buf)
                    }
                }
            } else if (fRec != null) {
                fRec.appendInterleaved(input, frameCount)
                recordingProgressFrames += frameCount
                if (fRec.isComplete()) {
                    val buf = fRec.completeOrNull()
                    freeRecorder = null
                    recordingCaptureKind = null
                    recordingTargetFrames = 0
                    recordingProgressFrames = 0
                    if (buf != null) {
                        lastQuantizedBars = null
                        loadedSample = buf
                        bpmAtLastCapture = internalBpm.currentBpm()
                        loopPlayer.load(buf)
                    }
                }
            }

            val inChannels = config.inputChannels
            val samplePlayingBpm = loopPlayer.isLooping() || loopPlayer.isShotActive()
            if (samplePlayingBpm != lastBpmIngestSourceWasSample) {
                if (samplePlayingBpm) autoBpmEstimator.reset() else autoBpmSampleEstimator.reset()
                lastBpmIngestSourceWasSample = samplePlayingBpm
            }
            if (inChannels >= 1 && !samplePlayingBpm) {
                autoBpmEstimator.ingestInterleavedInput(input, inChannels, frameCount)
            }

            val outChannels = config.outputChannels
            val outSamples = frameCount * outChannels
            val mainPitchRatio = effectiveMasterPitchRatio(frameCount, config.sampleRateHz)

            if (inChannels <= 0) {
                for (i in 0 until outSamples) output[i] = 0f
            } else {
                // Dry monitor: input × gain into scratch, then master pitch (same as FX bus) into output.
                var env = filterEnv
                val attack = 0.02f
                val release = 0.005f
                for (f in 0 until frameCount) {
                    val inBase = f * inChannels
                    val outBase = f * outChannels
                    val srcLeft = inBase
                    val x = input[srcLeft]
                    val mag = abs(x)
                    val coeff = if (mag > env) attack else release
                    env += coeff * (mag - env)
                    val sampleAudible = loopPlayer.isLooping() || loopPlayer.isShotActive()
                    val monitorGain = if (sampleAudible) 0f else inputMonitorGain
                    for (oc in 0 until outChannels) {
                        val srcChannel = if (inChannels == 1) 0 else minOf(oc, inChannels - 1)
                        monitorBaseScratch[outBase + oc] = input[inBase + srcChannel] * monitorGain
                    }
                }
                filterEnv = env
                copyOrMasterPitchInterleaved(
                    src = monitorBaseScratch,
                    dst = output,
                    frameCount = frameCount,
                    outChannels = outChannels,
                    ratio = mainPitchRatio,
                    shifter = mainPitchShifterMonitor
                )
            }

            if (outSamples > 0) {
                fxBusScratch.fill(0f, fromIndex = 0, toIndex = outSamples)
                loopPlayer.mixInto(fxBusScratch, outChannels, frameCount)
                loopPlayer.mixShotInto(fxBusScratch, outChannels, frameCount)
                if (samplePlayingBpm) {
                    autoBpmSampleEstimator.ingestInterleavedInput(fxBusScratch, outChannels, frameCount)
                }
                when (samplerFxRoute) {
                    SamplerFxRouteIntent.THROUGH_EFFECTS_PATH -> {
                        // Sampler → master pitch (before delay/flanger wet/dry) → scratch/delay/flanger → filter.
                        pitchInterleavedInPlace(
                            bus = fxBusScratch,
                            frameCount = frameCount,
                            outChannels = outChannels,
                            ratio = mainPitchRatio,
                            shifter = mainPitchShifterFxBus
                        )

                        if (fxOrderMode.scratchBeforeDelayFlanger) {
                            applyScratchToFxBus(frameCount, outChannels, config.sampleRateHz)
                            applyDelayToFxBus(frameCount, config.sampleRateHz)
                            applyFlangerToFxBus(frameCount, config.sampleRateHz)
                        } else {
                            applyDelayToFxBus(frameCount, config.sampleRateHz)
                            applyFlangerToFxBus(frameCount, config.sampleRateHz)
                            applyScratchToFxBus(frameCount, outChannels, config.sampleRateHz)
                        }

                        applyFilterToInterleaved(fxBusScratch, frameCount, outChannels, config.sampleRateHz)
                        if (fxOrderMode.tapeWowFlutterBeforeReverb) {
                            applyTapeWowToFxBus(frameCount, outChannels)
                            applyReverbToFxBus(frameCount, outChannels)
                        } else {
                            applyReverbToFxBus(frameCount, outChannels)
                            applyTapeWowToFxBus(frameCount, outChannels)
                        }
                        val g = mainMixNorm
                        for (i in 0 until outSamples) {
                            output[i] += fxBusScratch[i] * g
                        }
                    }
                    SamplerFxRouteIntent.DIRECT_TO_MONITOR_MIX -> {
                        val g = mainMixNorm
                        for (i in 0 until outSamples) {
                            output[i] += fxBusScratch[i] * g
                        }
                    }
                }
            }

            if (bpmAutoFollowEnabled) {
                val r = if (samplePlayingBpm) {
                    val barBase = loopBpmAtUnityOrNull()
                    if (barBase != null) {
                        val m = PitchKnobMath.toPlaybackSpeedMultiplier(samplePitchPercent).toDouble()
                        AutoBpmReading(
                            bpm = (barBase * m).coerceIn(40.0, 280.0),
                            confidence = 0.95f
                        )
                    } else {
                        autoBpmSampleEstimator.reading()
                    }
                } else if (inChannels >= 1) {
                    autoBpmEstimator.reading()
                } else {
                    null
                }
                if (r != null && r.confidence >= AUTO_BPM_APPLY_CONFIDENCE) {
                    val next = if (samplePlayingBpm && loopBpmAtUnityOrNull() != null) {
                        r.bpm
                    } else {
                        val a = (0.08 + r.confidence * 0.50).coerceIn(0.08, 0.65)
                        autoBpmEma + a * (r.bpm - autoBpmEma)
                    }
                    autoBpmEma = next
                    internalBpm.updateBpm(next)
                }
            }
            if (outChannels >= 2) {
                if (eqParamsDirty) {
                    monitorEq.syncCoefficients(
                        config.sampleRateHz,
                        eqLowDb,
                        eqMidDb,
                        eqHighDb,
                    )
                    eqParamsDirty = false
                }
                monitorEq.processInterleavedStereo(output, frameCount)
            }
            for (i in 0 until outSamples) {
                var s = output[i]
                if (!s.isFinite()) s = 0f
                output[i] = s.coerceIn(-1f, 1f)
            }
        }
    }

    fun stopMonitoring() {
        engine.stop()
        autoBpmEstimator.reset()
        autoBpmSampleEstimator.reset()
        lastBpmIngestSourceWasSample = false
        fxDelay?.reset()
        fxDelay = null
        fxFlanger?.reset()
        fxFlanger = null
        fxReverb = null
        fxTapeWow = null
        scratchRing?.reset()
        scratchRing = null
        scratchLagFrames = 0f
        scratchRateCurrent = 0f
        scratchRateTarget = 0f
        scratchVolumeCurrent = 1f
        scratchVolumeTarget = 1f
        scratchTouchActive = false
        monitorEq.reset()
        eqParamsDirty = true
        filterL.reset()
        filterR.reset()
        filterParamsDirty = true
        recorder = null
        freeRecorder = null
        recordingBars = null
        recordingCaptureKind = null
        recordingProgressFrames = 0
        recordingTargetFrames = 0
        loopPlayer.setLooping(false)
        loopPlayer.setShotPressed(false)
    }

    fun setPreferredDevices(inputId: Int?, outputId: Int?) {
        preferredInputId = inputId
        preferredOutputId = outputId
        backend.setPreferredDevices(inputId, outputId)
    }

    fun isRunning(): Boolean = engine.state == AudioEngineState.RUNNING

    fun meterSnapshot(): AudioMeterSnapshot = backend.meterSnapshot()
    fun latencySnapshot(): AudioLatencySnapshot = backend.latencySnapshot()

    fun startQuantizedRecording(bars: QuantizedBars): Boolean {
        val config = currentConfig ?: return false
        if (!isRunning()) return false
        if (recorder != null || freeRecorder != null) return false

        val next = QuantizedSamplerRecorder(
            sampleRateHz = config.sampleRateHz,
            channels = config.inputChannels
        )
        next.start(bpm = internalBpm.currentBpm(), bars = bars)
        recorder = next
        recordingBars = bars
        recordingCaptureKind = SamplerCaptureKind.QUANTIZED
        recordingTargetFrames = QuantizedLoopMath.targetFrames(
            sampleRateHz = config.sampleRateHz,
            bpm = internalBpm.currentBpm(),
            bars = bars
        )
        recordingProgressFrames = 0
        return true
    }

    /**
     * Records until [maxFrames] interleaved frames are captured, or [stopFreeRecordingEarly] is used.
     */
    fun startFreeRecording(maxFrames: Int): Boolean {
        val config = currentConfig ?: return false
        if (!isRunning()) return false
        if (recorder != null || freeRecorder != null) return false
        if (maxFrames <= 0) return false

        val next = FrameCappedSamplerRecorder(
            sampleRateHz = config.sampleRateHz,
            channels = config.inputChannels
        )
        next.start(maxFrames = maxFrames)
        freeRecorder = next
        recordingBars = null
        recordingCaptureKind = SamplerCaptureKind.FREE
        recordingTargetFrames = maxFrames
        recordingProgressFrames = 0
        return true
    }

    fun startFreeRecordingSeconds(seconds: Double): Boolean {
        val config = currentConfig ?: return false
        val frames = FreeRecordMath.maxFramesForSeconds(config.sampleRateHz, seconds)
        return startFreeRecording(frames)
    }

    fun stopFreeRecordingEarly(): Boolean {
        val active = freeRecorder ?: return false
        active.finishEarly()
        return true
    }

    fun setLoopPlayback(enabled: Boolean): Boolean {
        if (enabled && loadedSample == null && !loopPlayer.hasBuffer()) return false
        loopPlayer.setLooping(enabled)
        return enabled == loopPlayer.isLooping()
    }

    fun isLoopPlaybackRunning(): Boolean = loopPlayer.isLooping()

    fun setShotPressed(down: Boolean) {
        loopPlayer.setShotPressed(down)
    }

    fun isShotActive(): Boolean = loopPlayer.isShotActive()

    fun setReversePlayback(enabled: Boolean) {
        loopPlayer.setReversePlayback(enabled)
    }

    fun isReversePlayback(): Boolean = loopPlayer.isReversePlayback()

    fun samplerFxRouteIntent(): SamplerFxRouteIntent = samplerFxRoute

    fun setSamplerFxRouteIntent(intent: SamplerFxRouteIntent) {
        samplerFxRoute = intent
    }

    /**
     * E4-S1 filter type (LP/HP/BP) applied to FX bus.
     *
     * Does not allocate and updates coefficients safely on the audio callback when running.
     */
    fun setFilterType(type: FilterType) {
        filterType = type
        filterParamsDirty = true
    }

    fun filterType(): FilterType = filterType

    fun setFilterMode(mode: FilterMode) {
        filterMode = mode
        filterParamsDirty = true
    }

    fun filterMode(): FilterMode = filterMode

    /** 0..1, logarithmic mapping to Hz. */
    fun setFilterCutoffNorm(norm: Float) {
        filterCutoffNorm = norm.coerceIn(0f, 1f)
        filterParamsDirty = true
    }

    fun filterCutoffNorm(): Float = filterCutoffNorm

    /** 0..1 mapped to Q (resonance). */
    fun setFilterResonanceNorm(norm: Float) {
        filterResonanceNorm = norm.coerceIn(0f, 1f)
        filterParamsDirty = true
    }

    fun filterResonanceNorm(): Float = filterResonanceNorm

    /** 0..1 depth. */
    fun setFilterLfoDepth(norm: Float) {
        filterLfoDepth = norm.coerceIn(0f, 1f)
        filterParamsDirty = true
    }

    fun filterLfoDepth(): Float = filterLfoDepth

    /** Period in beats (BPM-relative). */
    fun setFilterLfoPeriodBeats(beats: Float) {
        filterLfoPeriodBeats = beats.coerceIn(0.125f, 16f)
        filterParamsDirty = true
    }

    fun filterLfoPeriodBeats(): Float = filterLfoPeriodBeats

    /** 0..1 depth for AUTO envelope mapping. */
    fun setFilterAutoDepth(norm: Float) {
        filterAutoDepth = norm.coerceIn(0f, 1f)
        filterParamsDirty = true
    }

    fun filterAutoDepth(): Float = filterAutoDepth

    /** When false, no new audio is fed into the delay line; feedback continues (echo tail, E4-S3). */
    fun setDelayFeedInput(on: Boolean) {
        delayFeedInput = on
    }

    fun delayFeedInput(): Boolean = delayFeedInput

    /** Echo time as fractional beats vs current internal BPM (see UI for discrete choices). */
    fun setDelayBeats(beats: Float) {
        delayBeats = beats.coerceIn(0.125f, 16f)
    }

    fun delayBeats(): Float = delayBeats

    fun setDelayFeedbackNorm(norm: Float) {
        delayFeedbackNorm = norm.coerceIn(0f, 0.98f)
    }

    fun delayFeedbackNorm(): Float = delayFeedbackNorm

    fun setDelayWetNorm(norm: Float) {
        delayWetNorm = norm.coerceIn(0f, 1f)
    }

    fun delayWetNorm(): Float = delayWetNorm

    /** When true, flanger does not comb-filter (buffer/LFO still advance). */
    fun setFlangerBypass(bypass: Boolean) {
        flangerBypass = bypass
    }

    fun flangerBypass(): Boolean = flangerBypass

    /** LFO cycle length in beats (same discrete list as filter/delay in UI). */
    fun setFlangerLfoBeats(beats: Float) {
        flangerLfoBeats = beats.coerceIn(0.125f, 16f)
    }

    fun flangerLfoBeats(): Float = flangerLfoBeats

    fun setFlangerBaseNorm(norm: Float) {
        flangerBaseNorm = norm.coerceIn(0f, 1f)
    }

    fun flangerBaseNorm(): Float = flangerBaseNorm

    fun setFlangerSweepNorm(norm: Float) {
        flangerSweepNorm = norm.coerceIn(0f, 1f)
    }

    fun flangerSweepNorm(): Float = flangerSweepNorm

    /** 0..1 → manual offset mapped around 0.5 neutral in DSP. */
    fun setFlangerManualNorm(norm: Float) {
        flangerManualNorm = norm.coerceIn(0f, 1f)
    }

    fun flangerManualNorm(): Float = flangerManualNorm

    fun setFlangerWetNorm(norm: Float) {
        flangerWetNorm = norm.coerceIn(0f, 1f)
    }

    fun flangerWetNorm(): Float = flangerWetNorm

    fun setReverbEnabled(enabled: Boolean) {
        reverbEnabled = enabled
    }

    fun reverbEnabled(): Boolean = reverbEnabled

    fun setReverbCathedral(cathedral: Boolean) {
        reverbCathedral = cathedral
    }

    fun reverbCathedral(): Boolean = reverbCathedral

    fun setReverbDecayNorm(norm: Float) {
        reverbDecayNorm = norm.coerceIn(0f, 1f)
    }

    fun reverbDecayNorm(): Float = reverbDecayNorm

    fun setReverbMixNorm(norm: Float) {
        reverbMixNorm = norm.coerceIn(0f, 1f)
    }

    fun reverbMixNorm(): Float = reverbMixNorm

    fun setTapeWowEnabled(enabled: Boolean) {
        tapeWowEnabled = enabled
    }

    fun tapeWowEnabled(): Boolean = tapeWowEnabled

    fun setTapeWowDepthNorm(norm: Float) {
        tapeWowDepthNorm = norm.coerceIn(0f, 1f)
    }

    fun tapeWowDepthNorm(): Float = tapeWowDepthNorm

    fun setTapeFlutterDepthNorm(norm: Float) {
        tapeFlutterDepthNorm = norm.coerceIn(0f, 1f)
    }

    fun tapeFlutterDepthNorm(): Float = tapeFlutterDepthNorm

    fun setTapeMixNorm(norm: Float) {
        tapeMixNorm = norm.coerceIn(0f, 1f)
    }

    fun tapeMixNorm(): Float = tapeMixNorm

    /** E4-S6: EQ band gain in dB (UI fader: [[ThreeBandStereoEq.MIN_DB], [ThreeBandStereoEq.BOOST_MAX_DB]]). */
    fun setEqLowDb(db: Float) {
        eqLowDb = db.coerceIn(ThreeBandStereoEq.MIN_DB, ThreeBandStereoEq.BOOST_MAX_DB)
        eqParamsDirty = true
    }

    fun eqLowDb(): Float = eqLowDb

    fun setEqMidDb(db: Float) {
        eqMidDb = db.coerceIn(ThreeBandStereoEq.MIN_DB, ThreeBandStereoEq.BOOST_MAX_DB)
        eqParamsDirty = true
    }

    fun eqMidDb(): Float = eqMidDb

    fun setEqHighDb(db: Float) {
        eqHighDb = db.coerceIn(ThreeBandStereoEq.MIN_DB, ThreeBandStereoEq.BOOST_MAX_DB)
        eqParamsDirty = true
    }

    fun eqHighDb(): Float = eqHighDb

    /**
     * E4-S5: finger started/ended on scratch surface. When active, FX-bus samples after delay are read
     * from the rolling buffer at lag/volume controlled by [setScratchTouchAxes].
     */
    fun setScratchTouchActive(active: Boolean) {
        scratchTouchActive = active
        if (active) {
            scratchRateCurrent = 0f
            scratchRateTarget = 0f
        } else {
            scratchRateTarget = 0f
            scratchVolumeTarget = 1f
        }
    }

    /**
     * Scratch pad axes:
     * - xNorm: horizontal volume control (0 left/quiet .. 1 right/loud)
     * - dyNorm: vertical motion drives signed platter speed (vinyl-like scrub)
     */
    fun setScratchTouchAxes(xNorm: Float, dyNorm: Float) {
        if (scratchRing == null) return
        val rateRange = scratchRateRangeForPreset()
        val motion = dyNorm.coerceIn(-1f, 1f)
        scratchRateTarget = (-motion * rateRange).coerceIn(-220f, 220f)
        scratchVolumeTarget = xNorm.coerceIn(0f, 1f)
    }

    fun setScratchFeelPreset(preset: ScratchFeelPreset) {
        scratchFeelPreset = preset
    }

    fun scratchFeelPreset(): ScratchFeelPreset = scratchFeelPreset

    fun setFxOrderMode(mode: FxOrderMode) {
        fxOrderMode = mode
    }

    fun fxOrderMode(): FxOrderMode = fxOrderMode

    private fun applyTapeWowToFxBus(frameCount: Int, outChannels: Int) {
        fxTapeWow?.processReplaceInterleaved(
            fxBusScratch,
            frameCount,
            outChannels,
            tapeWowEnabled,
            tapeWowDepthNorm,
            tapeFlutterDepthNorm,
            tapeMixNorm
        )
    }

    private fun applyReverbToFxBus(frameCount: Int, outChannels: Int) {
        fxReverb?.processReplaceInterleaved(
            fxBusScratch,
            frameCount,
            outChannels,
            reverbEnabled,
            reverbCathedral,
            reverbDecayNorm,
            reverbMixNorm
        )
    }

    private fun applyDelayToFxBus(frameCount: Int, sampleRateHz: Int) {
        fxDelay?.let { delay ->
            val dFrames = DelayBeatMath.beatsToDelayFrames(
                internalBpm.currentBpm(),
                delayBeats.toDouble(),
                sampleRateHz
            )
            delay.setDelayFrames(dFrames)
            delay.process(
                fxBusScratch,
                frameCount,
                feedInput = delayFeedInput,
                feedback = delayFeedbackNorm,
                wetMix = delayWetNorm
            )
        }
    }

    private fun applyFlangerToFxBus(frameCount: Int, sampleRateHz: Int) {
        fxFlanger?.let { fl ->
            val hz = FlangerBeatMath.beatsPerCycleToLfoHz(
                internalBpm.currentBpm(),
                flangerLfoBeats.toDouble()
            )
            val baseMs = 1f + flangerBaseNorm * 5f
            val sweepMs = flangerSweepNorm * 4f
            val manualMs = (flangerManualNorm - 0.5f) * 4f
            fl.process(
                interleaved = fxBusScratch,
                frameCount = frameCount,
                sampleRateHz = sampleRateHz,
                lfoHz = hz,
                baseDelayMs = baseMs,
                sweepMs = sweepMs,
                manualMs = manualMs,
                wet = flangerWetNorm,
                bypass = flangerBypass
            )
        }
    }

    private fun mainPitchBypass(ratio: Float): Boolean = abs(ratio - 1f) < 1e-3f

    /**
     * [src] interleaved → optional PV by mono sum → [dst]. Used for input/monitor path after gain.
     */
    private fun copyOrMasterPitchInterleaved(
        src: FloatArray,
        dst: FloatArray,
        frameCount: Int,
        outChannels: Int,
        ratio: Float,
        shifter: StreamingCheapPitchShifterMono
    ) {
        val outSamples = frameCount * outChannels
        if (mainPitchBypass(ratio)) {
            for (i in 0 until outSamples) dst[i] = src[i]
            return
        }
        if (outChannels >= 2) {
            for (f in 0 until frameCount) {
                val l = src[f * outChannels]
                val r = src[f * outChannels + 1]
                monoPitchScratch[f] = 0.5f * (l + r)
            }
        } else {
            for (f in 0 until frameCount) {
                monoPitchScratch[f] = src[f * outChannels]
            }
        }
        shifter.processReplace(monoPitchScratch, 0, monoPitchOutScratch, 0, frameCount, ratio)
        if (outChannels >= 2) {
            for (f in 0 until frameCount) {
                val v = monoPitchOutScratch[f]
                dst[f * outChannels] = v
                dst[f * outChannels + 1] = v
            }
        } else {
            for (f in 0 until frameCount) {
                dst[f] = monoPitchOutScratch[f]
            }
        }
    }

    /** In-place interleaved PV (mono sum → expand) for the FX bus. */
    private fun pitchInterleavedInPlace(
        bus: FloatArray,
        frameCount: Int,
        outChannels: Int,
        ratio: Float,
        shifter: StreamingCheapPitchShifterMono
    ) {
        if (mainPitchBypass(ratio)) return
        if (outChannels >= 2) {
            for (f in 0 until frameCount) {
                val l = bus[f * outChannels]
                val r = bus[f * outChannels + 1]
                monoPitchScratch[f] = 0.5f * (l + r)
            }
        } else {
            for (f in 0 until frameCount) {
                monoPitchScratch[f] = bus[f * outChannels]
            }
        }
        shifter.processReplace(monoPitchScratch, 0, monoPitchOutScratch, 0, frameCount, ratio)
        if (outChannels >= 2) {
            for (f in 0 until frameCount) {
                val v = monoPitchOutScratch[f]
                bus[f * outChannels] = v
                bus[f * outChannels + 1] = v
            }
        } else {
            for (f in 0 until frameCount) {
                bus[f] = monoPitchOutScratch[f]
            }
        }
    }

    private fun applyScratchToFxBus(frameCount: Int, outChannels: Int, sampleRateHz: Int) {
        val ring = scratchRing
        if (ring != null && outChannels >= 2) {
            val maxLag = minOf(ring.maxLagFrames(), sampleRateHz * SCRATCH_MAX_LAG_SECONDS)
            val rateSlew = scratchRateSlewPerFrameForPreset()
            val volSlew = scratchVolumeSlewPerFrameForPreset()
            for (f in 0 until frameCount) {
                val base = f * outChannels
                val lIn = fxBusScratch[base]
                val rIn = fxBusScratch[base + 1]
                ring.writeStereoFrame(lIn, rIn)
                if (scratchTouchActive) {
                    scratchRateCurrent += (scratchRateTarget - scratchRateCurrent) * rateSlew
                    val lagDelta = -scratchRateCurrent
                    scratchLagFrames = (scratchLagFrames + lagDelta).coerceIn(0f, maxLag)
                    scratchVolumeCurrent += (scratchVolumeTarget - scratchVolumeCurrent) * volSlew
                    val (sl, sr) = ring.readStereoAtLagFrames(scratchLagFrames)
                    fxBusScratch[base] = sl * scratchVolumeCurrent
                    fxBusScratch[base + 1] = sr * scratchVolumeCurrent
                }
            }
            if (!scratchTouchActive && scratchLagFrames > 1e-4f) {
                val decay = scratchLagDecayPerBurstForPreset()
                scratchLagFrames *= decay
            }
        }
    }

    private fun applyFilterToInterleaved(
        interleaved: FloatArray,
        frameCount: Int,
        outChannels: Int,
        sampleRateHz: Int
    ) {
        val dynamicMode = filterMode != FilterMode.MANUAL
        if (filterParamsDirty || dynamicMode) {
            val t = filterType
            val (cutoff, qNow) = computeFilterCutoffAndQ(sampleRateHz)
            filterL.setCoefficients(t, sampleRateHz, cutoff, qNow)
            if (outChannels >= 2) {
                filterR.setCoefficients(t, sampleRateHz, cutoff, qNow)
            }
            // LFO/AUTO depend on evolving phase/envelope, so keep updating every callback.
            filterParamsDirty = false
        }
        if (outChannels >= 2) {
            for (f in 0 until frameCount) {
                val base = f * outChannels
                interleaved[base] = filterL.processSample(interleaved[base])
                interleaved[base + 1] = filterR.processSample(interleaved[base + 1])
            }
        } else {
            for (f in 0 until frameCount) {
                interleaved[f] = filterL.processSample(interleaved[f])
            }
        }
    }

    private fun computeFilterCutoffAndQ(sampleRateHz: Int): Pair<Float, Float> {
        val nyq = sampleRateHz / 2f
        val minHz = 40f
        val maxHz = minOf((nyq * FILTER_MAX_NYQUIST_RATIO), 18_000f)
            .coerceAtLeast(minHz + 1f)

        // Log mapping for musical knob feel.
        val logMin = kotlin.math.ln(minHz.toDouble())
        val logMax = kotlin.math.ln(maxHz.toDouble())
        val baseHz = kotlin.math.exp(logMin + (logMax - logMin) * filterCutoffNorm.toDouble()).toFloat()

        val qBase = (0.5f + filterResonanceNorm * 9.5f).coerceIn(0.5f, 10f)
        // Near top-end cutoff, reduce effective resonance to avoid unstable/harsh peak spikes.
        val topBlend = ((baseHz / maxHz) - 0.72f) / 0.28f
        val qDamp = 1f - topBlend.coerceIn(0f, 1f) * 0.62f
        val q = (qBase * qDamp).coerceIn(0.5f, 10f)

        val cutoff = when (filterMode) {
            FilterMode.MANUAL -> baseHz
            FilterMode.LFO -> {
                val bpm = internalBpm.currentBpm().coerceIn(40.0, 280.0)
                val beatsPerSec = bpm / 60.0
                val periodBeats = filterLfoPeriodBeats.coerceAtLeast(0.125f)
                val hz = beatsPerSec / periodBeats.toDouble()
                val phase = filterLfoPhaseRad
                val s = sin(phase)
                // Advance phase by this callback’s duration (keeps continuity without per-frame trig).
                val inc = 2.0 * PI * hz * ((currentConfig?.framesPerBurst ?: AndroidAudioDefaults.LOW_LATENCY_FRAMES_PER_BURST).toDouble() / sampleRateHz.toDouble())
                filterLfoPhaseRad = wrapRad(phase + inc)

                val depth = filterLfoDepth.toDouble().coerceIn(0.0, 1.0)
                val semitoneSpan = 24.0 * depth // up to +/- 24 semitones
                val ratio = 2.0.pow((s * semitoneSpan) / 12.0)
                (baseHz.toDouble() * ratio).toFloat()
            }
            FilterMode.AUTO -> {
                // Envelope to log-space sweep: env ~ 0..1, depth scales travel.
                val env = filterEnv.coerceIn(0f, 1f).toDouble()
                val depth = filterAutoDepth.toDouble().coerceIn(0.0, 1.0)
                val t = (env * depth).coerceIn(0.0, 1.0)
                kotlin.math.exp(logMin + (logMax - logMin) * t).toFloat()
            }
        }.coerceIn(minHz, maxHz)

        return cutoff to q
    }

    private fun wrapRad(x: Double): Double {
        var v = x
        val twoPi = 2.0 * PI
        while (v >= twoPi) v -= twoPi
        while (v < 0.0) v += twoPi
        return v
    }

    fun setMainPitchPercent(percent: Float) {
        mainPitchPercent = PitchKnobMath.clampMainPitchPercent(percent, mainPitchMaxAbsPercent())
    }

    fun mainPitchPercent(): Float = mainPitchPercent

    fun setMainPitchRangeMode(mode: MainPitchRangeMode) {
        mainPitchRangeMode = mode
        mainPitchPercent = PitchKnobMath.clampMainPitchPercent(mainPitchPercent, mainPitchMaxAbsPercent())
    }

    fun mainPitchRangeMode(): MainPitchRangeMode = mainPitchRangeMode

    fun setMainMixNorm(norm: Float) {
        mainMixNorm = norm.coerceIn(0f, 1f)
    }

    fun mainMixNorm(): Float = mainMixNorm

    fun setSamplePitchPercent(percent: Float) {
        samplePitchPercent = PitchKnobMath.clampPercent(percent)
        loopPlayer.setSamplePitchPercent(samplePitchPercent)
    }

    fun samplePitchPercent(): Float = samplePitchPercent

    fun setInputMonitorGain(gain: Float) {
        inputMonitorGain = gain.coerceIn(0f, 2f)
    }

    fun inputMonitorGain(): Float = inputMonitorGain

    fun currentBpm(): Double = internalBpm.currentBpm()

    fun setBpmAutoFollowEnabled(enabled: Boolean) {
        bpmAutoFollowEnabled = enabled
        if (enabled) {
            autoBpmEma = internalBpm.currentBpm()
        }
    }

    fun isBpmAutoFollowEnabled(): Boolean = bpmAutoFollowEnabled

    fun autoBpmReading(): AutoBpmReading {
        val samplePlaying = loopPlayer.isLooping() || loopPlayer.isShotActive()
        if (samplePlaying) {
            val barBase = loopBpmAtUnityOrNull()
            if (barBase != null) {
                val m = PitchKnobMath.toPlaybackSpeedMultiplier(samplePitchPercent).toDouble()
                return AutoBpmReading(
                    bpm = (barBase * m).coerceIn(40.0, 280.0),
                    confidence = 0.95f
                )
            }
            return autoBpmSampleEstimator.reading()
        }
        return autoBpmEstimator.reading()
    }

    /**
     * BPM of the loaded clip at **1.0× sample pitch** when [lastQuantizedBars] is set (4/4 bars).
     */
    private fun loopBpmAtUnityOrNull(): Double? {
        val buf = loadedSample ?: return null
        val bars = lastQuantizedBars ?: return null
        if (buf.frameCount <= 0) return null
        val raw = LoopBpmMath.bpmFromLoopFrames(buf.sampleRateHz, buf.frameCount, bars)
        if (!raw.isFinite()) return null
        return raw.coerceIn(40.0, 280.0)
    }

    /**
     * Tap tempo using [elapsedRealtimeSeconds] (e.g. [android.os.SystemClock.elapsedRealtime] / 1000.0).
     * Updates internal BPM when enough taps are collected; returns the new BPM or `null` if still learning.
     */
    fun tapTempoNow(elapsedRealtimeSeconds: Double): Double? {
        val est = tapBpmEstimator.tap(elapsedRealtimeSeconds) ?: return null
        internalBpm.updateBpm(est)
        autoBpmEma = est
        return est
    }

    fun resetBpmToDefault() {
        tapBpmEstimator.reset()
        autoBpmEstimator.reset()
        autoBpmSampleEstimator.reset()
        internalBpm.updateBpm(120.0)
        autoBpmEma = 120.0
    }

    /**
     * Loads a [SamplerBuffer] into the live sampler (e.g. bundled preset). Replaces any prior capture.
     * Runs offline BPM estimation on the buffer and updates [currentBpm] when the estimate is confident;
     * otherwise uses [bpmAtCapture] when non-null (e.g. library metadata or preset hint).
     * Thread-safe for UI thread; does not start transport — use [setLoopPlayback] to hear it.
     */
    @Synchronized
    fun loadPresetSample(
        buffer: SamplerBuffer,
        lastQuantizedBars: QuantizedBars? = null,
        bpmAtCapture: Double? = null
    ) {
        loadedSample = buffer
        this.lastQuantizedBars = lastQuantizedBars
        bpmAtLastCapture = bpmAtCapture
        loopPlayer.load(buffer)
        applyInternalBpmAfterSampleLoad(buffer, lastQuantizedBars, fallbackBpm = bpmAtCapture)
    }

    /**
     * When [quantizedBars] is set, tempo comes from **loop length × 4/4 bar count** (matches quantized REC).
     * Otherwise onset offline estimate + [fallbackBpm].
     */
    private fun applyInternalBpmAfterSampleLoad(
        buffer: SamplerBuffer,
        quantizedBars: QuantizedBars?,
        fallbackBpm: Double?
    ) {
        val barBpm = quantizedBars?.let { bars ->
            LoopBpmMath.bpmFromLoopFrames(buffer.sampleRateHz, buffer.frameCount, bars)
        }
        val use = when {
            barBpm != null && barBpm.isFinite() && barBpm in 40.0..280.0 -> barBpm
            else -> {
                val r = AutoBpmEstimator.estimateFromInterleavedBuffer(
                    buffer.pcm,
                    buffer.channels,
                    buffer.sampleRateHz
                )
                when {
                    r.confidence >= SAMPLE_LOAD_BPM_MIN_CONFIDENCE -> r.bpm
                    fallbackBpm != null && fallbackBpm.isFinite() && fallbackBpm > 0.0 -> fallbackBpm
                    else -> null
                }
            }
        }
        if (use != null) {
            internalBpm.updateBpm(use)
            autoBpmEma = use
        }
    }

    /**
     * Deep-copies the loaded sample for disk export (UI thread). Returns null if nothing loaded.
     */
    @Synchronized
    fun snapshotLoadedSampleForExport(): SamplerBuffer? {
        val s = loadedSample ?: return null
        return SamplerBuffer(
            pcm = s.pcm.copyOf(),
            sampleRateHz = s.sampleRateHz,
            channels = s.channels
        )
    }

    fun recordingState(): SamplerRecordingState {
        val loaded = loadedSample
        val cfg = currentConfig
        return SamplerRecordingState(
            isRecording = recorder != null || freeRecorder != null,
            captureKind = recordingCaptureKind,
            bars = recordingBars,
            progressFrames = recordingProgressFrames,
            targetFrames = recordingTargetFrames,
            loadedFrameCount = loaded?.frameCount ?: 0,
            loadedSampleRateHz = loaded?.sampleRateHz ?: cfg?.sampleRateHz ?: 0,
            lastQuantizedBars = lastQuantizedBars,
            bpmAtLastCapture = bpmAtLastCapture,
            recordingBpm = internalBpm.currentBpm(),
            isPlaybackLooping = loopPlayer.isLooping(),
            isShotActive = loopPlayer.isShotActive(),
            loopPlayheadFrame = loopPlayer.publishedLoopPlayheadFrame,
            shotPlayheadFrame = loopPlayer.publishedShotPlayheadFrame,
            samplerFxRoute = samplerFxRoute,
            isReversePlayback = loopPlayer.isReversePlayback(),
            mainPitchPercent = mainPitchPercent,
            samplePitchPercent = samplePitchPercent
        )
    }

    /**
     * Stops + restarts the core engine to apply new audio device routing safely.
     *
     * This avoids attempting to mutate AudioRecord/AudioTrack parameters mid-stream,
     * which can cause instability on some Android devices.
     */
    fun rebindIfRunning() {
        if (!isRunning()) return
        if (rebindInProgress) return
        rebindInProgress = true
        try {
            stopMonitoring()
            startMonitoring()
        } finally {
            rebindInProgress = false
        }
    }
}
