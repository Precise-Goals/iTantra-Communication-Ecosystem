package com.itantra.core.audio

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Keeps each received message's synthesized speech as a 16-bit mono WAV, so it can be replayed
 * like a voice note (T67). Files live in filesDir/voicenotes/, named by sender and sequence so the
 * UI can find a message's note without any new field on TransceiverMessage.
 *
 * Only reads files written by [save]; this is not a general WAV parser.
 */
object VoiceNoteStore {

    private const val DIR = "voicenotes"
    /** Oldest notes are deleted beyond this count. ~200 KB each for a few seconds of speech. */
    private const val MAX_NOTES = 200

    fun fileFor(context: Context, senderId: String, sequence: Int): File {
        val safeSender = senderId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(File(context.filesDir, DIR), "${safeSender}_$sequence.wav")
    }

    fun save(context: Context, senderId: String, sequence: Int, samples: FloatArray, sampleRate: Int): File? =
        runCatching {
            val file = fileFor(context, senderId, sequence)
            file.parentFile?.mkdirs()
            val dataBytes = samples.size * 2
            val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray(Charsets.US_ASCII)); buf.putInt(36 + dataBytes)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII)); buf.putInt(16)
            buf.putShort(1)                 // PCM
            buf.putShort(1)                 // mono
            buf.putInt(sampleRate)
            buf.putInt(sampleRate * 2)      // byte rate
            buf.putShort(2)                 // block align
            buf.putShort(16)                // bits per sample
            buf.put("data".toByteArray(Charsets.US_ASCII)); buf.putInt(dataBytes)
            for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
            file.writeBytes(buf.array())
            prune(context)
            file
        }.getOrNull()

    /** Returns (samples, sampleRate) for a file written by [save], or null. */
    fun load(file: File): Pair<FloatArray, Int>? = runCatching {
        val bb = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val sampleRate = bb.getInt(24)
        val n = bb.getInt(40) / 2
        FloatArray(n) { i -> bb.getShort(44 + i * 2) / Short.MAX_VALUE.toFloat() } to sampleRate
    }.getOrNull()

    private fun prune(context: Context) {
        val files = File(context.filesDir, DIR).listFiles()?.sortedBy { it.lastModified() } ?: return
        files.dropLast(MAX_NOTES).forEach { it.delete() }
    }
}
