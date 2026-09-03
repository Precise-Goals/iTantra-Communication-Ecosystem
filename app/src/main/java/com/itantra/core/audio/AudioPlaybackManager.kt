package com.itantra.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.TransceiverMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * AudioPlaybackManager — Receiver-side playback & alert override engine.
 *
 * Implements deliverable 4 from the SIH Voice Transceiver Architecture:
 * 1. Parses incoming payloads ({lang, type, text}).
 * 2. **Blindly trusts the sender's language ID**: Bypasses receiver-side text language detection
 *    completely, eliminating misclassification and synthesis lag.
 * 3. Preprocesses text through [IndicTextNormalizer] (currency, numbers, abbreviations).
 * 4. Synthesizes PCM waveform using [TTSModule].
 * 5. Handles priority playback:
 *    - **ALERT mode**: Forces maximum volume on [AudioManager.STREAM_ALARM], requests
 *      [AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE], and enforces non-interruptible output.
 *    - **Normal speech mode**: Plays via [AudioManager.STREAM_MUSIC] at 100% loudspeaker volume.
 */
class AudioPlaybackManager(
    private val context: Context,
    private val callbacks: AudioCallbacks
) {
    companion object {
        private const val TAG = "AudioPlayback"
        private const val SAMPLE_RATE = TTSModule.PLAYBACK_SAMPLE_RATE
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_FLOAT
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioFocusRequest: AudioFocusRequest? = null
    private var savedVolume: Int = -1

    /**
     * Process an incoming domain TransceiverMessage.
     * Bypasses text language detection and routes directly to the designated TTS voice.
     */
    fun playIncomingMessage(message: TransceiverMessage, ttsModule: TTSModule) {
        scope.launch {
            val isAlert = message.type == MessageType.ALERT
            val targetLang = message.srcLang.ifBlank { message.dstLang.ifBlank { "hi" } }

            // Clean numbers, currency, and symbols prior to phonemization
            val normalizedText = IndicTextNormalizer.normalize(message.text, targetLang)
            Log.i(TAG, "Synthesizing incoming message directly in [$targetLang] (isAlert=$isAlert): '${normalizedText.take(30)}...'")

            val rawWaveform = ttsModule.synthesize(normalizedText, targetLang)
            if (rawWaveform != null) {
                if (isAlert) {
                    playAlert(rawWaveform)
                } else {
                    playNormal(rawWaveform)
                }
            } else {
                Log.w(TAG, "TTS synthesis returned null for language [$targetLang]")
            }
        }
    }

    /**
     * Process an incoming JSON string payload: {"lang":"hi", "type":"alert", "text":"..."}.
     */
    fun playIncomingPayload(jsonPayload: String, ttsModule: TTSModule) {
        try {
            val json = JSONObject(jsonPayload)
            val lang = json.optString("lang", "hi")
            val type = json.optString("type", "speech").lowercase()
            val text = json.getString("text")
            val isAlert = type == "alert"

            val msg = TransceiverMessage(
                type = if (isAlert) MessageType.ALERT else MessageType.SPEECH,
                text = text,
                srcLang = lang,
                dstLang = lang,
                senderId = json.optString("senderId", "unknown")
            )
            playIncomingMessage(msg, ttsModule)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming payload: ${e.message}")
        }
    }

    /**
     * Play a synthesized PCM waveform directly.
     *
     * @param waveform Float PCM samples at [SAMPLE_RATE] Hz.
     * @param isAlert If true, uses alarm stream with max volume override.
     */
    fun play(waveform: FloatArray, isAlert: Boolean = false) {
        scope.launch {
            if (isAlert) playAlert(waveform) else playNormal(waveform)
        }
    }

    private fun playNormal(waveform: FloatArray) {
        requestAudioFocus(isAlert = false)

        // Force maximum 100% device media volume for clear, loud voice playback
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set max media volume: ${e.message}")
        }

        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.setVolume(1.0f)
            track.play()
            track.write(waveform, 0, waveform.size, AudioTrack.WRITE_BLOCKING)
            track.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing normal audio: ${e.message}")
        } finally {
            track.release()
            releaseAudioFocus()
        }
    }

    private fun playAlert(waveform: FloatArray) {
        // Save current alarm volume
        savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        // Force maximum alarm volume
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            maxVolume,
            AudioManager.FLAG_SHOW_UI
        )

        // Request DND bypass (requires MANAGE_NOTIFICATION_POLICY)
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        } catch (e: SecurityException) {
            Log.w(TAG, "DND override permission not granted: ${e.message}")
        }

        // Request EXCLUSIVE transient audio focus to completely silence other audio
        requestAudioFocus(isAlert = true)

        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.setVolume(1.0f)
            track.play()
            track.write(waveform, 0, waveform.size, AudioTrack.WRITE_BLOCKING)
            track.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alert audio: ${e.message}")
        } finally {
            track.release()
            releaseAudioFocus()
            // Restore original volume
            if (savedVolume >= 0) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
                } catch (_: Exception) {}
                savedVolume = -1
            }
        }
    }

    private fun requestAudioFocus(isAlert: Boolean) {
        // AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE blocks and silences all other device audio
        val focusGain = if (isAlert) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        } else {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    val gained = focusChange == AudioManager.AUDIOFOCUS_GAIN
                    callbacks.onAudioFocusChanged(gained)
                }
                .build()

            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    val gained = focusChange == AudioManager.AUDIOFOCUS_GAIN
                    callbacks.onAudioFocusChanged(gained)
                },
                if (isAlert) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC,
                focusGain
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
