package com.freekalizer.tablet.library

import android.content.Context
import com.freekalizer.audio.AndroidAudioDefaults
import com.freekalizer.audio.PcmResampler
import com.freekalizer.audio.WavPcmIo
import com.freekalizer.sampler.SamplerBuffer
import com.freekalizer.sampler.SavedSampleMetadata
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * App-private sample library under [Context.filesDir] (no extra storage permissions).
 * Each item: `{id}.wav` + `{id}.meta.json`.
 */
class SampleLibraryStore(context: Context) {

    private val root: File = File(context.filesDir, "sample_library").apply { mkdirs() }

    data class Entry(
        val id: String,
        val displayName: String,
        val createdAtEpochMillis: Long,
        val favorite: Boolean
    )

    fun listEntries(): List<Entry> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles { f -> f.name.endsWith(META_SUFFIX) }
            .orEmpty()
            .mapNotNull { metaFile ->
                try {
                    val id = metaFile.name.removeSuffix(META_SUFFIX)
                    val json = JSONObject(metaFile.readText())
                    Entry(
                        id = id,
                        displayName = json.optString(KEY_NAME, id),
                        createdAtEpochMillis = json.optLong(KEY_CREATED_AT, 0L),
                        favorite = json.optBoolean(KEY_FAVORITE, false)
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .sortedWith(
                compareByDescending<Entry> { it.favorite }
                    .thenByDescending { it.createdAtEpochMillis }
            )
    }

    fun renameClip(id: String, newDisplayName: String): Result<Unit> {
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("name is empty"))
        }
        val metaFile = File(root, "$id$META_SUFFIX")
        return try {
            if (!metaFile.isFile) return Result.failure(IllegalStateException("missing meta for $id"))
            val o = JSONObject(metaFile.readText())
            o.put(KEY_NAME, trimmed)
            metaFile.writeText(o.toString(2))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setFavorite(id: String, favorite: Boolean): Result<Unit> {
        val metaFile = File(root, "$id$META_SUFFIX")
        return try {
            if (!metaFile.isFile) return Result.failure(IllegalStateException("missing meta for $id"))
            val o = JSONObject(metaFile.readText())
            o.put(KEY_FAVORITE, favorite)
            metaFile.writeText(o.toString(2))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteClip(id: String): Result<Unit> {
        return try {
            val wavFile = File(root, "$id$WAV_SUFFIX")
            val metaFile = File(root, "$id$META_SUFFIX")
            if (!wavFile.isFile && !metaFile.isFile) {
                return Result.failure(IllegalStateException("clip not found"))
            }
            var ok = true
            if (wavFile.isFile) ok = wavFile.delete() && ok
            if (metaFile.isFile) ok = metaFile.delete() && ok
            if (ok) Result.success(Unit)
            else Result.failure(IllegalStateException("could not delete all files for $id"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun save(
        wavBytes: ByteArray,
        metadata: SavedSampleMetadata
    ): Result<Entry> {
        return try {
            val id = UUID.randomUUID().toString()
            val wavFile = File(root, "$id$WAV_SUFFIX")
            val metaFile = File(root, "$id$META_SUFFIX")
            wavFile.writeBytes(wavBytes)
            metaFile.writeText(metadataToJson(metadata).toString(2))
            Result.success(
                Entry(
                    id = id,
                    displayName = metadata.name,
                    createdAtEpochMillis = metadata.createdAtEpochMillis,
                    favorite = metadata.favorite
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadBufferAndMetadata(id: String): Pair<SamplerBuffer, SavedSampleMetadata> {
        val wavFile = File(root, "$id$WAV_SUFFIX")
        val metaFile = File(root, "$id$META_SUFFIX")
        require(wavFile.isFile) { "missing wav for $id" }
        require(metaFile.isFile) { "missing meta for $id" }
        val decoded = WavPcmIo.decode(wavFile.readBytes())
        val meta = metadataFromJson(JSONObject(metaFile.readText()))
        val targetSr = AndroidAudioDefaults.LOW_LATENCY_SAMPLE_RATE_HZ
        val pcm = if (decoded.sampleRateHz == targetSr) {
            decoded.interleavedFloat
        } else {
            PcmResampler.resampleInterleavedLinear(
                decoded.interleavedFloat,
                decoded.channels,
                decoded.sampleRateHz,
                targetSr
            )
        }
        val buffer = SamplerBuffer(pcm = pcm, sampleRateHz = targetSr, channels = decoded.channels)
        return buffer to meta
    }

    private fun metadataToJson(m: SavedSampleMetadata): JSONObject =
        JSONObject().apply {
            put(KEY_FORMAT, 1)
            put(KEY_NAME, m.name)
            put(KEY_CREATED_AT, m.createdAtEpochMillis)
            put(KEY_BPM, m.bpmAtRecord)
            put(KEY_DURATION, m.durationSeconds)
            put(KEY_REVERSE, m.reverse)
            put(KEY_MAIN_PITCH, m.mainPitchPercent.toDouble())
            put(KEY_SAMPLE_PITCH, m.samplePitchPercent.toDouble())
            if (m.quantizedBars != null) put(KEY_QUANT_BARS, m.quantizedBars)
            put(KEY_FAVORITE, m.favorite)
        }

    private fun metadataFromJson(o: JSONObject): SavedSampleMetadata {
        val qb = when {
            !o.has(KEY_QUANT_BARS) || o.isNull(KEY_QUANT_BARS) -> null
            else -> o.optInt(KEY_QUANT_BARS).let { v ->
                if (v in setOf(1, 2, 4, 8, 16)) v else null
            }
        }
        return SavedSampleMetadata(
            name = o.optString(KEY_NAME, "Sample"),
            createdAtEpochMillis = o.optLong(KEY_CREATED_AT, 0L),
            bpmAtRecord = o.optDouble(KEY_BPM, 120.0),
            durationSeconds = o.optDouble(KEY_DURATION, 0.0),
            reverse = o.optBoolean(KEY_REVERSE, false),
            mainPitchPercent = o.optDouble(KEY_MAIN_PITCH, 0.0).toFloat(),
            samplePitchPercent = o.optDouble(KEY_SAMPLE_PITCH, 0.0).toFloat(),
            quantizedBars = qb,
            favorite = o.optBoolean(KEY_FAVORITE, false)
        )
    }

    companion object {
        private const val WAV_SUFFIX = ".wav"
        private const val META_SUFFIX = ".meta.json"
        private const val KEY_FORMAT = "format"
        private const val KEY_NAME = "name"
        private const val KEY_CREATED_AT = "createdAtEpochMillis"
        private const val KEY_BPM = "bpmAtRecord"
        private const val KEY_DURATION = "durationSeconds"
        private const val KEY_REVERSE = "reverse"
        private const val KEY_MAIN_PITCH = "mainPitchPercent"
        private const val KEY_SAMPLE_PITCH = "samplePitchPercent"
        private const val KEY_QUANT_BARS = "quantizedBars"
        private const val KEY_FAVORITE = "favorite"
    }
}
