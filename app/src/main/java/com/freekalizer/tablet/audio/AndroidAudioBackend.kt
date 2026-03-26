package com.freekalizer.tablet.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.freekalizer.audio.AudioBackend
import com.freekalizer.audio.AudioEngineConfig
import com.freekalizer.audio.AudioProcessor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class AndroidAudioBackend(
    private val context: Context
) : AudioBackend {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val lock = Any()
    @Volatile
    private var running: Boolean = false

    private val stopRequested = AtomicBoolean(false)
    private var worker: Thread? = null

    @Volatile
    private var record: AudioRecord? = null

    @Volatile
    private var track: AudioTrack? = null

    @Volatile
    private var preferredInputDeviceId: Int? = null

    @Volatile
    private var preferredOutputDeviceId: Int? = null

    fun setPreferredDevices(inputId: Int?, outputId: Int?) {
        preferredInputDeviceId = inputId
        preferredOutputDeviceId = outputId
    }

    override fun start(config: AudioEngineConfig, processor: AudioProcessor) {
        synchronized(lock) {
            check(!running) { "AndroidAudioBackend already running" }
            running = true
            stopRequested.set(false)
        }

        val inChannels = config.inputChannels
        val outChannels = config.outputChannels

        val preferredInput = preferredInputDeviceId?.let { id ->
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
        }
        val preferredOutput = preferredOutputDeviceId?.let { id ->
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }
        }

        // Allocate & run audio on a dedicated thread. This prototype uses AudioRecord/AudioTrack
        // streaming and will later be replaced by a true AAudio/Oboe callback.
        startWithEncoding(
            config = config,
            inputChannels = inChannels,
            outputChannels = outChannels,
            processor = processor,
            preferredInput = preferredInput,
            preferredOutput = preferredOutput,
            encoding = AudioFormat.ENCODING_PCM_16BIT,
        )

        Log.i(TAG, "Backend started using PCM_16BIT. inCh=$inChannels outCh=$outChannels")
    }

    override fun stop() {
        synchronized(lock) {
            if (!running) return
            stopRequested.set(true)
        }

        // Stop/release to unblock any blocking read/write calls.
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        try {
            track?.pause()
        } catch (_: Throwable) {
        }

        worker?.interrupt()

        // Release streams. AudioRecord/AudioTrack may already be released; swallow.
        try {
            record?.release()
        } catch (_: Throwable) {
        }
        try {
            track?.release()
        } catch (_: Throwable) {
        }

        record = null
        track = null

        // Avoid blocking the UI thread too long.
        try {
            worker?.join(250)
        } catch (_: Throwable) {
        }
        worker = null

        running = false
    }

    private fun channelMaskIn(channels: Int): Int = when (channels) {
        1 -> AudioFormat.CHANNEL_IN_MONO
        2 -> AudioFormat.CHANNEL_IN_STEREO
        0 -> AudioFormat.CHANNEL_IN_MONO // unused if inputChannels == 0
        else -> throw IllegalArgumentException("Unsupported inputChannels=$channels")
    }

    private fun channelMaskOut(channels: Int): Int = when (channels) {
        1 -> AudioFormat.CHANNEL_OUT_MONO
        2 -> AudioFormat.CHANNEL_OUT_STEREO
        else -> throw IllegalArgumentException("Unsupported outputChannels=$channels")
    }

    private fun startWithEncoding(
        config: AudioEngineConfig,
        inputChannels: Int,
        outputChannels: Int,
        processor: AudioProcessor,
        preferredInput: AudioDeviceInfo?,
        preferredOutput: AudioDeviceInfo?,
        encoding: Int
    ) {
        val inMask = channelMaskIn(inputChannels)
        val outMask = channelMaskOut(outputChannels)

        val audioFormatIn = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(config.sampleRateHz)
            .setChannelMask(inMask)
            .build()

        val audioFormatOut = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(config.sampleRateHz)
            .setChannelMask(outMask)
            .build()

        val recordBufferBytes = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            inMask,
            encoding
        )
        val trackBufferBytes = AudioTrack.getMinBufferSize(
            config.sampleRateHz,
            outMask,
            encoding
        )

        // Build with low-latency performance mode where available.
        val recordBuilder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(audioFormatIn)
            .setBufferSizeInBytes(recordBufferBytes)

        applyPreferredDeviceIfSupported(recordBuilder, preferredInput)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(audioFormatOut)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(trackBufferBytes)

        applyPreferredDeviceIfSupported(trackBuilder, preferredOutput)

        // Create streams.
        val recordLocal = if (inputChannels == 0) null else recordBuilder.build()
        val trackLocal = trackBuilder.build()

        synchronized(lock) {
            record = recordLocal
            track = trackLocal
        }

        val framesPerBurst = config.framesPerBurst
        val inSamplesExpected = framesPerBurst * inputChannels
        val outSamplesExpected = framesPerBurst * outputChannels

        val threadName = "AudioBackend-$encoding"
        val localProcessor = processor

        worker = Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

                recordLocal?.startRecording()
                trackLocal.play()

                runPcm16Loop(
                    record = recordLocal,
                    track = trackLocal,
                    framesPerBurst = framesPerBurst,
                    inputChannels = inputChannels,
                    outputChannels = outputChannels,
                    processor = localProcessor,
                    inSamplesExpected = inSamplesExpected,
                    outSamplesExpected = outSamplesExpected
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Audio backend worker crashed: ${t.message}", t)
            } finally {
                // If the worker exits, ensure streams are stopped to avoid runaway resources.
                try {
                    recordLocal?.stop()
                } catch (_: Throwable) {
                }
                try {
                    trackLocal.pause()
                } catch (_: Throwable) {
                }
            }
        }, threadName)

        worker!!.start()
    }

    private fun runPcm16Loop(
        record: AudioRecord?,
        track: AudioTrack,
        framesPerBurst: Int,
        inputChannels: Int,
        outputChannels: Int,
        processor: AudioProcessor,
        inSamplesExpected: Int,
        outSamplesExpected: Int
    ) {
        val inputShort = if (inputChannels == 0) null else ShortArray(inSamplesExpected)
        val outputShort = ShortArray(outSamplesExpected)

        val inputFloat = FloatArray(inSamplesExpected)
        val outputFloat = FloatArray(outSamplesExpected)
        val emptyIn = FloatArray(0)

        while (!stopRequested.get()) {
            val framesActual: Int
            if (inputChannels == 0) {
                // Silence: let processor handle it; inputFloat contents are ignored when inputChannels == 0.
                framesActual = framesPerBurst
            } else {
                val samplesRead = record!!.read(inputShort!!, 0, inSamplesExpected)
                if (samplesRead <= 0) {
                    java.util.Arrays.fill(outputFloat, 0f)
                    continue
                }
                // Convert PCM_16 samples to normalized floats for the core processor.
                for (i in 0 until samplesRead) {
                    inputFloat[i] = inputShort[i] / 32768f
                }
                framesActual = samplesRead / inputChannels
            }

            val inArray = if (inputChannels == 0) emptyIn else inputFloat
            processor.process(inArray, outputFloat, framesActual)

            val outSamples = framesActual * outputChannels
            val clampMin = -1.0f
            val clampMax = 1.0f
            for (i in 0 until outSamples) {
                val v = max(clampMin, min(clampMax, outputFloat[i]))
                outputShort[i] = (v * 32767f).toInt().toShort()
            }

            if (outSamples > 0) {
                track.write(outputShort, 0, outSamples, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    /**
     * Uses reflection so this code still compiles if `setPreferredDevice` isn't available on a specific
     * API level/builder variant.
     */
    private fun applyPreferredDeviceIfSupported(builder: Any, preferred: AudioDeviceInfo?) {
        if (preferred == null) return
        try {
            val method = builder.javaClass.getMethod("setPreferredDevice", AudioDeviceInfo::class.java)
            method.invoke(builder, preferred)
        } catch (t: Throwable) {
            Log.w(TAG, "Preferred device not supported on this device/builder: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "AndroidAudioBackend"
    }
}
