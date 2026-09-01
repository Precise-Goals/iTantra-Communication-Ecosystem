package com.itantra.core.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuous audio capture module using [AudioRecord].
 *
 * Captures 16kHz mono PCM audio in 100ms chunks and routes them to [VADModule].
 * When VAD detects a complete speech segment (ended by silence), the accumulated
 * buffer is forwarded to [STTModule] for transcription.
 *
 * Audio pipeline:
 * Microphone → AudioRecord (16kHz, PCM_16BIT)
 *           → 100ms chunks → VADModule
 *           → Speech detected → accumulate
 *           → Silence (>800ms) → STTModule.transcribe()
 */
class AudioCaptureModule(
    private val vadModule: VADModule,
    private val sttModule: STTModule,
    private val callbacks: AudioCallbacks,
    private val onSpeechReady: suspend (FloatArray, String) -> Unit
) {
    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = VADModule.CHUNK_SIZE  // 1600 samples = 100ms
        private const val MAX_SPEECH_BUFFER_SAMPLES = SAMPLE_RATE * 30 // 30s max
    }

    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var isCapturing = false

    // Speech accumulation buffer
    private val speechBuffer = mutableListOf<FloatArray>()
    private var silenceChunkCount = 0
    private val silenceChunksForEndOfSpeech = (VADModule.SILENCE_DURATION_MS / 100).toInt() // 8 chunks

    var currentLanguage: String = "hi"

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isCapturing) return
        isCapturing = true

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(CHUNK_SIZE * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            callbacks.onAudioError(
                AppResult.Error(ErrorCode.PERMISSION_DENIED, "Microphone not accessible")
            )
            return
        }

        audioRecord?.startRecording()
        Log.d(TAG, "Audio capture started at ${SAMPLE_RATE}Hz")

        captureJob = scope.launch {
            val rawBuffer = ShortArray(CHUNK_SIZE)

            while (isActive && isCapturing) {
                val samplesRead = audioRecord?.read(rawBuffer, 0, CHUNK_SIZE) ?: -1

                if (samplesRead <= 0) {
                    delay(10)
                    continue
                }

                // Normalize PCM Short → Float [-1.0, 1.0]
                val floatChunk = FloatArray(samplesRead) { i ->
                    rawBuffer[i].toFloat() / Short.MAX_VALUE
                }

                // Run VAD
                val speechProb = vadModule.process(floatChunk)
                val isSpeech = speechProb >= 0.5f

                if (isSpeech) {
                    silenceChunkCount = 0
                    // Guard against infinite accumulation
                    if (speechBuffer.sumOf { it.size } < MAX_SPEECH_BUFFER_SAMPLES) {
                        speechBuffer.add(floatChunk)
                    }
                } else {
                    if (speechBuffer.isNotEmpty()) {
                        silenceChunkCount++
                        // Include a brief trailing silence for natural sentence boundary
                        speechBuffer.add(floatChunk)

                        if (silenceChunkCount >= silenceChunksForEndOfSpeech) {
                            // End of speech detected — submit for STT
                            val combined = FloatArray(speechBuffer.sumOf { it.size })
                            var offset = 0
                            speechBuffer.forEach { chunk ->
                                chunk.copyInto(combined, offset)
                                offset += chunk.size
                            }
                            speechBuffer.clear()
                            silenceChunkCount = 0

                            Log.d(TAG, "Speech segment complete: ${combined.size} samples")
                            onSpeechReady(combined, currentLanguage)
                        }
                    }
                }
            }
        }
    }

    /** For PTT mode: stop capture and immediately flush whatever was accumulated. */
    suspend fun flushAndTranscribe(): FloatArray? {
        if (speechBuffer.isEmpty()) return null
        val combined = FloatArray(speechBuffer.sumOf { it.size })
        var offset = 0
        speechBuffer.forEach { chunk ->
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }
        speechBuffer.clear()
        silenceChunkCount = 0
        return combined
    }

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        speechBuffer.clear()
        Log.d(TAG, "Audio capture stopped")
    }

    val isRunning: Boolean get() = isCapturing
}
