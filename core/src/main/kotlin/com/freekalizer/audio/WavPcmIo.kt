package com.freekalizer.audio

import com.freekalizer.sampler.SamplerBuffer
import java.nio.charset.StandardCharsets

/**
 * Minimal RIFF WAVE PCM 16-bit LE read/write for portable sample I/O (no Android APIs).
 */
object WavPcmIo {

    data class DecodeResult(
        val sampleRateHz: Int,
        val channels: Int,
        /** Interleaved -1..1 float, length = frameCount * channels. */
        val interleavedFloat: FloatArray
    )

    fun decode(bytes: ByteArray): DecodeResult {
        require(bytes.size >= 12) { "WAV too small" }
        checkChunk(bytes, 0, "RIFF")
        checkChunk(bytes, 8, "WAVE")
        var offset = 12
        var sampleRate = 44_100
        var channels = 2
        var bitsPerSample = 16
        var audioFormat = 1
        while (offset + 8 <= bytes.size) {
            val chunkId = readAscii(bytes, offset, 4)
            val chunkSize = readLeU32(bytes, offset + 4).toInt()
            val dataStart = offset + 8
            val nextChunk = dataStart + chunkSize + (chunkSize and 1)
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize >= 16) {
                        audioFormat = readLeU16(bytes, dataStart)
                        channels = readLeU16(bytes, dataStart + 2)
                        sampleRate = readLeU32(bytes, dataStart + 4).toInt()
                        bitsPerSample = readLeU16(bytes, dataStart + 14)
                    }
                }
                "data" -> {
                    require(audioFormat == 1) { "WAV must be PCM (format 1), got $audioFormat" }
                    require(bitsPerSample == 16) { "WAV must be 16-bit, got $bitsPerSample" }
                    require(channels in 1..8) { "unsupported channel count $channels" }
                    val bytesPerFrame = channels * 2
                    val frameCount = chunkSize / bytesPerFrame
                    val out = FloatArray(frameCount * channels)
                    var p = dataStart
                    var o = 0
                    while (o < out.size && p + 1 < dataStart + chunkSize && p + 1 < bytes.size) {
                        val s = readLeI16(bytes, p)
                        out[o++] = s / 32768f
                        p += 2
                    }
                    return DecodeResult(sampleRate, channels, out)
                }
            }
            offset = nextChunk
        }
        error("WAV has no data chunk")
    }

    /**
     * Writes a standard PCM WAV (format 1, 16-bit LE). Float samples are clamped to [-1, 1].
     */
    fun encodePcm16LeWav(buffer: SamplerBuffer): ByteArray =
        encodePcm16LeWav(buffer.pcm, buffer.channels, buffer.sampleRateHz)

    fun encodePcm16LeWav(interleavedFloat: FloatArray, channels: Int, sampleRateHz: Int): ByteArray {
        require(channels in 1..8) { "channels must be 1..8" }
        require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
        require(interleavedFloat.size % channels == 0) { "interleaved length must be multiple of channels" }
        val frameCount = interleavedFloat.size / channels
        val dataBytes = frameCount * channels * 2
        val pad = dataBytes and 1
        val fmtChunkBodySize = 16
        val riffPayloadSize = 4 + (8 + fmtChunkBodySize) + (8 + dataBytes + pad)
        val fileSize = 8 + riffPayloadSize
        val out = ByteArray(fileSize)
        var w = 0
        fun writeAscii(s: String) {
            val bb = s.toByteArray(StandardCharsets.US_ASCII)
            bb.copyInto(out, w)
            w += bb.size
        }
        fun writeLeU16(v: Int) {
            out[w++] = (v and 0xff).toByte()
            out[w++] = ((v shr 8) and 0xff).toByte()
        }
        fun writeLeU32(v: Int) {
            out[w++] = (v and 0xff).toByte()
            out[w++] = ((v shr 8) and 0xff).toByte()
            out[w++] = ((v shr 16) and 0xff).toByte()
            out[w++] = ((v shr 24) and 0xff).toByte()
        }
        writeAscii("RIFF")
        writeLeU32(fileSize - 8)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeLeU32(fmtChunkBodySize)
        writeLeU16(1)
        writeLeU16(channels)
        writeLeU32(sampleRateHz)
        val blockAlign = channels * 2
        writeLeU32(sampleRateHz * blockAlign)
        writeLeU16(blockAlign)
        writeLeU16(16)
        writeAscii("data")
        writeLeU32(dataBytes)
        var p = w
        for (i in interleavedFloat.indices) {
            val f = interleavedFloat[i].coerceIn(-1f, 1f)
            val s = (f * 32767.0f).toInt().coerceIn(-32768, 32767)
            out[p++] = (s and 0xff).toByte()
            out[p++] = ((s shr 8) and 0xff).toByte()
        }
        w = p
        if (pad == 1) {
            out[w] = 0
        }
        return out
    }

    private fun checkChunk(bytes: ByteArray, at: Int, label: String) {
        val s = readAscii(bytes, at, 4)
        require(s == label) { "expected $label at $at, got $s" }
    }

    private fun readAscii(bytes: ByteArray, offset: Int, len: Int): String {
        return String(bytes, offset, len, StandardCharsets.US_ASCII)
    }

    private fun readLeU16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readLeI16(bytes: ByteArray, offset: Int): Int {
        val u = readLeU16(bytes, offset)
        return (u shl 16) shr 16
    }

    private fun readLeU32(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
    }
}
