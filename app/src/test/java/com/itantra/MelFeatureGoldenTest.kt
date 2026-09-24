package com.itantra

import android.content.Context
import com.itantra.core.audio.STTModule
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern

/**
 * T29 — asserts extractLogMelSpectrogram matches NeMo's own preprocessor (T23) to ~1e-3.
 *
 * fixture.wav and golden_features.npy (app/src/test/resources/) come from a real FLEURS hi_in
 * clip run through the actual AI4Bharat checkpoint's AudioToMelSpectrogramPreprocessor in Colab —
 * see docs/evaluation/nemo_preprocessor_hi.txt for the exact steps and config.
 */
class MelFeatureGoldenTest {

    private val fakeCallbacks = object : AudioCallbacks {
        override fun onVADTriggered(isSpeech: Boolean, probability: Float) {}
        override fun onSTTResult(result: AppResult<String>, confidence: Float, inferenceMs: Long) {}
        override fun onTTSSynthesisComplete(durationMs: Long) {}
        override fun onAudioError(error: AppResult.Error) {}
        override fun onAudioFocusChanged(gained: Boolean) {}
    }

    @Test
    fun testExtractLogMelSpectrogramMatchesNemoGoldenReference() {
        val audio = readWavFloatSamples(resourceFile("fixture.wav"))
        val (goldenShape, golden) = readNpyFloat32(resourceFile("golden_features.npy"))
        val nMels = goldenShape[0]
        val nFrames = goldenShape[1]

        val sttModule = STTModule(mock(Context::class.java), fakeCallbacks)
        val features = sttModule.extractLogMelSpectrogram(audio)

        assertTrue(
            "Expected ${nMels * nFrames} features (shape $nMels x $nFrames), got ${features.size}",
            features.size == nMels * nFrames
        )

        var worstDiff = 0f
        var worstIndex = -1
        for (i in features.indices) {
            val diff = kotlin.math.abs(features[i] - golden[i])
            if (diff > worstDiff) {
                worstDiff = diff
                worstIndex = i
            }
        }

        val mel = if (worstIndex >= 0) worstIndex / nFrames else -1
        val frame = if (worstIndex >= 0) worstIndex % nFrames else -1
        assertTrue(
            "Worst absolute difference from NeMo's golden reference is $worstDiff at flat index " +
                "$worstIndex (mel band $mel, frame $frame) — kotlin=${features.getOrNull(worstIndex)} " +
                "golden=${golden.getOrNull(worstIndex)}. Tolerance is 1e-3.",
            worstDiff < 1e-3
        )
    }

    private fun resourceFile(name: String): File {
        val url = javaClass.classLoader?.getResource(name)
            ?: error("Test resource '$name' not found on the classpath")
        return File(url.toURI())
    }

    /** Reads a mono PCM/IEEE-float WAV file's samples as [-1.0, 1.0] floats. */
    private fun readWavFloatSamples(file: File): FloatArray {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        require(bytes.size >= 12) { "Not a WAV file: too short" }
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        require(riff == "RIFF" && wave == "WAVE") { "Not a RIFF/WAVE file" }

        var pos = 12
        var audioFormat = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataSize = -1
        while (pos + 8 <= bytes.size) {
            val chunkId = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = buf.getInt(pos + 4)
            val chunkDataStart = pos + 8
            when (chunkId) {
                "fmt " -> {
                    audioFormat = buf.getShort(chunkDataStart).toInt()
                    bitsPerSample = buf.getShort(chunkDataStart + 14).toInt()
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = chunkSize
                }
            }
            pos = chunkDataStart + chunkSize + (chunkSize and 1) // chunks are word-aligned
        }
        require(dataOffset >= 0) { "No 'data' chunk found in $file" }
        require(bitsPerSample == 32) { "Expected 32-bit samples, found $bitsPerSample" }

        val sampleCount = dataSize / 4
        val samples = FloatArray(sampleCount)
        when (audioFormat) {
            3 -> for (i in 0 until sampleCount) samples[i] = buf.getFloat(dataOffset + i * 4) // IEEE float
            1 -> for (i in 0 until sampleCount) samples[i] = buf.getInt(dataOffset + i * 4) / 2147483648f // PCM32
            else -> error("Unsupported WAV audio_format=$audioFormat")
        }
        return samples
    }

    /** Reads a float32, C-contiguous, 2D .npy array (as produced by numpy.save on a torch tensor). */
    private fun readNpyFloat32(file: File): Pair<IntArray, FloatArray> {
        val bytes = file.readBytes()
        require(bytes.size > 10 && String(bytes, 1, 5, Charsets.US_ASCII) == "NUMPY") {
            "Not a .npy file: $file"
        }
        val majorVersion = bytes[6].toInt()
        val headerLenBytes = if (majorVersion >= 2) 4 else 2
        val headerLenOffset = 8
        val headerLen = if (majorVersion >= 2) {
            ByteBuffer.wrap(bytes, headerLenOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        } else {
            ByteBuffer.wrap(bytes, headerLenOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        }
        val headerStart = headerLenOffset + headerLenBytes
        val header = String(bytes, headerStart, headerLen, Charsets.US_ASCII)

        require(header.contains("'descr': '<f4'")) { "Expected little-endian float32 .npy, header: $header" }
        require(header.contains("'fortran_order': False")) { "Expected C-order .npy, header: $header" }

        val shapeMatcher = Pattern.compile("'shape':\\s*\\(([^)]*)\\)").matcher(header)
        require(shapeMatcher.find()) { "Could not parse shape from .npy header: $header" }
        val shapeGroup = requireNotNull(shapeMatcher.group(1)) { "Empty shape group in header: $header" }
        val shape = shapeGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }
        require(shape.size == 2) { "Expected a 2D array, got shape $shape" }

        val dataStart = headerStart + headerLen
        val count = shape[0] * shape[1]
        val data = FloatArray(count)
        val dataBuf = ByteBuffer.wrap(bytes, dataStart, count * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) data[i] = dataBuf.getFloat(dataStart + i * 4)
        return shape.toIntArray() to data
    }
}
