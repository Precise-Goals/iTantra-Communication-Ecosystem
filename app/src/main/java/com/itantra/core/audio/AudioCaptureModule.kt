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
    /** (audio, language, cutNs): cutNs is System.nanoTime() when the phrase was cut (T71). */
    private val onSpeechReady: suspend (FloatArray, String, Long) -> Unit
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

    /** One pending STT job (T65). [done] completes after onSpeechReady returns. */
    private class Segment(
        val audio: FloatArray,
        val language: String,
        val done: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
        /** When this phrase was cut (VAD pause or PTT release), not when it left the queue (T71). */
        val cutNs: Long = System.nanoTime()
    )

    /**
     * All finished speech goes through this queue and ONE consumer (T65). Previously
     * onSpeechReady -- which runs the whole STT inference -- was called inline in the capture
     * loop, so the loop stopped reading the microphone during inference and AudioRecord's small
     * buffer overflowed. The queue keeps capture reading and keeps phrases in spoken order.
     */
    private val segmentQueue =
        kotlinx.coroutines.channels.Channel<Segment>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        scope.launch {
            for (segment in segmentQueue) {
                try {
                    onSpeechReady(segment.audio, segment.language, segment.cutNs)
                } catch (e: Exception) {
                    Log.e(TAG, "segment processing failed: ${e.message}", e)
                } finally {
                    segment.done?.complete(Unit)
                }
            }
        }
    }

    /** Queue [audio] behind any phrases already pending and suspend until it is processed. */
    suspend fun submitAndAwait(audio: FloatArray, language: String) {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        segmentQueue.send(Segment(audio, language, done))
        done.await()
    }
    private var isCapturing = false

    // Speech accumulation buffer. Written from the capture coroutine (Dispatchers.IO) and read/
    // cleared from callers of flushAndTranscribe()/stopCapture() on other dispatchers — every
    // access must go through bufferLock, or concurrent reads/writes throw
    // ConcurrentModificationException (confirmed on a real device: PTT release calls
    // flushAndTranscribe() while the capture loop is still appending the next chunk).
    private val bufferLock = Any()
    private val speechBuffer = mutableListOf<FloatArray>()
    private var silenceChunkCount = 0
    // Endpoint sooner once enough speech has been captured to be confident it was a real
    // utterance. The flat 800ms wait was a hard floor under the "words said -> STT complete"
    // metric for every utterance. Short fragments keep the long window to avoid cutting off
    // a hesitant speaker mid-sentence.
    private val silenceChunksLong = (VADModule.SILENCE_DURATION_MS / 100).toInt()  // 8 = 800ms
    private val silenceChunksShort = 5                                             // 500ms
    private val confidentSpeechChunks = 8                                          // 800ms of speech

    private var speechChunkCount = 0

    /** Set by the service while the PTT button is held (T65). */
    @Volatile var pttHeld: Boolean = false
    /** 400 ms: the phrase cut used while PTT is held and enough speech has been captured. */
    private val silenceChunksPtt = 4

    private val silenceChunksForEndOfSpeech: Int
        get() = when {
            pttHeld && speechChunkCount >= confidentSpeechChunks -> silenceChunksPtt
            speechChunkCount >= confidentSpeechChunks -> silenceChunksShort
            else -> silenceChunksLong
        }

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

                var readySegment: FloatArray? = null
                if (isSpeech) {
                    silenceChunkCount = 0
                    synchronized(bufferLock) {
                        // Guard against infinite accumulation
                        if (speechBuffer.sumOf { it.size } < MAX_SPEECH_BUFFER_SAMPLES) {
                            speechBuffer.add(floatChunk)
                            speechChunkCount++
                        }
                    }
                } else {
                    synchronized(bufferLock) {
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
                                speechChunkCount = 0
                                readySegment = combined
                            }
                        }
                    }
                }
                readySegment?.let { combined ->
                    Log.d(TAG, "Speech segment complete: ${combined.size} samples")
                    // Hand off; never run inference on the capture loop (T65).
                    segmentQueue.trySend(Segment(combined, currentLanguage))
                }
            }
        }
    }

    /** For PTT mode: stop capture and immediately flush whatever was accumulated. */
    suspend fun flushAndTranscribe(): FloatArray? = synchronized(bufferLock) {
        if (speechBuffer.isEmpty()) return@synchronized null
        val combined = FloatArray(speechBuffer.sumOf { it.size })
        var offset = 0
        speechBuffer.forEach { chunk ->
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }
        speechBuffer.clear()
        silenceChunkCount = 0
        speechChunkCount = 0
        combined
    }

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        synchronized(bufferLock) { speechBuffer.clear(); speechChunkCount = 0 }
        Log.d(TAG, "Audio capture stopped")
    }

    val isRunning: Boolean get() = isCapturing
}
