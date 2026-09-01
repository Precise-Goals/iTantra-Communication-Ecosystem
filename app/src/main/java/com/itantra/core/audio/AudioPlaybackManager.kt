package com.itantra.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import com.itantra.domain.contracts.AudioCallbacks
import com.itantra.domain.model.AppResult
import com.itantra.domain.model.ErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Audio playback manager for TTS synthesized speech.
 *
 * Handles two playback modes:
 * - **Normal**: [AudioManager.STREAM_MUSIC] — standard voice note playback.
 * - **ALERT**: [AudioManager.STREAM_ALARM] — forces max volume, bypasses DND.
 *
 * Alert mode sequence:
 * 1. Request audio focus with AUDIOFOCUS_GAIN (interrupts other audio).
 * 2. Save current volume, set STREAM_ALARM to MAX.
 * 3. Play synthesized audio on STREAM_ALARM.
 * 4. Restore original volume after playback.
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
     * Play a synthesized PCM waveform.
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
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
            track.play()
            track.write(waveform, 0, waveform.size, AudioTrack.WRITE_BLOCKING)
            track.stop()
        } finally {
            track.release()
            releaseAudioFocus()
        }
    }

    private fun playAlert(waveform: FloatArray) {
        // Save current alarm volume
        savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        // Force maximum volume
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

        requestAudioFocus(isAlert = true)

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            android.media.AudioFormat.ENCODING_PCM_FLOAT
        )

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
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            track.play()
            track.write(waveform, 0, waveform.size, AudioTrack.WRITE_BLOCKING)
            track.stop()
        } finally {
            track.release()
            releaseAudioFocus()
            // Restore original volume
            if (savedVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
                savedVolume = -1
            }
        }
    }

    private fun requestAudioFocus(isAlert: Boolean) {
        val focusGain = if (isAlert) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
                    callbacks.onAudioFocusChanged(focusChange == AudioManager.AUDIOFOCUS_GAIN)
                },
                AudioManager.STREAM_ALARM,
                focusGain
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
