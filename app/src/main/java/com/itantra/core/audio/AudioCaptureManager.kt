package com.itantra.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.itantra.domain.contracts.AudioCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * AudioCaptureManager — High-accuracy, hardware-filtered audio acquisition engine.
 *
 * Implements strict requirements from the SIH Voice Transceiver Architecture:
 * 1. 16kHz Mono PCM AudioRecord stream.
 * 2. Hardware Acoustic Processing:
 *    - [NoiseSuppressor]: Eliminates background noise and ambient hum.
 *    - [AcousticEchoCanceler]: Eliminates feedback loops during Phone mode.
 *    - [AutomaticGainControl]: Normalizes voice levels across whisper and loud speech.
 * 3. Continuous buffer loop fed into [VADModule] (Silero VAD).
 * 4. Dual Operation Modes:
 *    - **PUSH_TO_TALK** (Half-Duplex): Records while PTT button is held; flushes to STT on release.
 *    - **PHONE_MODE** (Full-Duplex Hands-Free): Continuously listens via Silero VAD. When a pause
 *      or stoppage (>800ms silence after speech) is detected, it automatically finalizes the
 *      sentence buffer, triggers STT transcription, and resumes listening.
 */
class AudioCaptureManager(
    private val vadModule: VADModule,
    private val sttModule: STTModule,
    private val callbacks: AudioCallbacks,
    private val onSpeechReady: suspend (FloatArray, String) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 1600 // 100ms at 16kHz (matching Silero VAD frame size)
        private const val MAX_SPEECH_BUFFER_SAMPLES = SAMPLE_RATE * 30 // 30s maximum per phrase
        private const val SILENCE_CHUNKS_THRESHOLD = 8 // 8 * 100ms = 800ms silence trigger
    }

    enum class Mode {
        PUSH_TO_TALK,
        PHONE_MODE
    }

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null

    @Volatile
    var isCapturing: Boolean = false
        private set

    var currentMode: Mode = Mode.PUSH_TO_TALK
    var currentLanguage: String = "hi"

    // Speech accumulation state
    private val speechBuffer = mutableListOf<FloatArray>()
    private var isSpeechActive = false
    private var silenceChunkCount = 0

    val isRunning: Boolean get() = isCapturing

    /**
     * Start the audio capture stream with hardware acoustic filters.
     */
    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isCapturing) return
        isCapturing = true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "AudioSource.VOICE_COMMUNICATION unavailable, falling back to MIC: ${e.message}")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        }

        val record = audioRecord
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            isCapturing = false
            return
        }

        // Attach hardware DSP acoustic filters to audioSessionId
        val sessionId = record.audioSessionId
        attachHardwareFilters(sessionId)

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            releaseHardwareFilters()
            record.release()
            audioRecord = null
            isCapturing = false
            return
        }

        synchronized(speechBuffer) {
            speechBuffer.clear()
            isSpeechActive = false
            silenceChunkCount = 0
        }

        captureJob = scope.launch {
            readLoop(record)
        }

        Log.i(TAG, "AudioCaptureManager active: 16kHz mono, mode=$currentMode, sessionId=$sessionId")
    }

    /**
     * Attach and enable hardware NoiseSuppressor, AcousticEchoCanceler, and AGC.
     */
    private fun attachHardwareFilters(sessionId: Int) {
        if (sessionId <= 0) return

        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware NoiseSuppressor active")
                }
            } else {
                Log.d(TAG, "Hardware NoiseSuppressor not supported on this device")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing NoiseSuppressor: ${e.message}")
        }

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware AcousticEchoCanceler active")
                }
            } else {
                Log.d(TAG, "Hardware AcousticEchoCanceler not supported on this device")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing AcousticEchoCanceler: ${e.message}")
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                automaticGainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                    Log.i(TAG, "Hardware AutomaticGainControl active")
                }
            } else {
                Log.d(TAG, "Hardware AutomaticGainControl not supported on this device")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing AutomaticGainControl: ${e.message}")
        }
    }

    private fun releaseHardwareFilters() {
        try {
            noiseSuppressor?.release()
            noiseSuppressor = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing NoiseSuppressor: ${e.message}")
        }
        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AcousticEchoCanceler: ${e.message}")
        }
        try {
            automaticGainControl?.release()
            automaticGainControl = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AutomaticGainControl: ${e.message}")
        }
    }

    /**
     * Main audio capture loop.
     */
    private suspend fun readLoop(record: AudioRecord) {
        val shortBuffer = ShortArray(CHUNK_SIZE)
        val floatChunk = FloatArray(CHUNK_SIZE)

        while (scope.isActive && isCapturing) {
            val read = record.read(shortBuffer, 0, CHUNK_SIZE)
            if (read < CHUNK_SIZE) {
                if (read < 0) Log.w(TAG, "AudioRecord read error: $read")
                continue
            }

            // Convert PCM 16-bit to normalized float [-1.0, 1.0]
            var sumSquare = 0.0
            for (i in 0 until CHUNK_SIZE) {
                val f = shortBuffer[i] / 32768.0f
                floatChunk[i] = f
                sumSquare += (f * f)
            }
            val rms = sqrt(sumSquare / CHUNK_SIZE).toFloat()

            // Feed 100ms chunk into Silero VAD
            val prob = vadModule.process(floatChunk)
            val isSpeech = prob >= 0.5f

            if (currentMode == Mode.PUSH_TO_TALK) {
                // In PTT mode: Accumulate unconditionally during the hold
                synchronized(speechBuffer) {
                    val currentSamples = speechBuffer.sumOf { it.size }
                    if (currentSamples + CHUNK_SIZE <= MAX_SPEECH_BUFFER_SAMPLES) {
                        speechBuffer.add(floatChunk.copyOf())
                    }
                }
            } else {
                // In PHONE MODE: Hands-free automatic pause/stoppage detection
                handlePhoneModeChunk(floatChunk, isSpeech)
            }
        }
    }

    /**
     * Phone Mode: Continuous hands-free conversation with pause & stoppage detection.
     */
    private suspend fun handlePhoneModeChunk(chunk: FloatArray, isSpeech: Boolean) {
        var sentenceReady: FloatArray? = null

        synchronized(speechBuffer) {
            if (isSpeech) {
                isSpeechActive = true
                silenceChunkCount = 0
                val currentSamples = speechBuffer.sumOf { it.size }
                if (currentSamples + CHUNK_SIZE <= MAX_SPEECH_BUFFER_SAMPLES) {
                    speechBuffer.add(chunk.copyOf())
                }
            } else if (isSpeechActive) {
                // Speech was active, but current chunk is silence
                speechBuffer.add(chunk.copyOf())
                silenceChunkCount++

                // When silence exceeds threshold (800ms), finalize and fire STT
                if (silenceChunkCount >= SILENCE_CHUNKS_THRESHOLD) {
                    val totalSamples = speechBuffer.sumOf { it.size }
                    // Ignore tiny transient clicks (< 300ms)
                    if (totalSamples >= SAMPLE_RATE * 0.3) {
                        val full = FloatArray(totalSamples)
                        var offset = 0
                        for (c in speechBuffer) {
                            System.arraycopy(c, 0, full, offset, c.size)
                            offset += c.size
                        }
                        sentenceReady = full
                    }
                    speechBuffer.clear()
                    isSpeechActive = false
                    silenceChunkCount = 0
                }
            }
        }

        sentenceReady?.let { audio ->
            Log.i(TAG, "Phone Mode: Detected sentence pause (${audio.size} samples). Triggering STT.")
            onSpeechReady(audio, currentLanguage)
        }
    }

    /**
     * PTT Mode: Triggered when user releases the PTT button.
     * Returns the accumulated speech buffer and stops capture.
     */
    fun flushAndTranscribe(): FloatArray? {
        val full = synchronized(speechBuffer) {
            if (speechBuffer.isEmpty()) null
            else {
                val total = speechBuffer.sumOf { it.size }
                val array = FloatArray(total)
                var offset = 0
                for (chunk in speechBuffer) {
                    System.arraycopy(chunk, 0, array, offset, chunk.size)
                    offset += chunk.size
                }
                speechBuffer.clear()
                isSpeechActive = false
                silenceChunkCount = 0
                array
            }
        }
        return full
    }

    /**
     * Stop capturing and release hardware resources.
     */
    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        releaseHardwareFilters()

        try {
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }

        synchronized(speechBuffer) {
            speechBuffer.clear()
            isSpeechActive = false
            silenceChunkCount = 0
        }

        Log.d(TAG, "AudioCaptureManager stopped and released")
    }
}
