package com.itantra.domain.contracts

import com.itantra.domain.model.AppResult

/**
 * Contract interface between Domain A audio pipeline and Domain B UI shell.
 *
 * FROZEN POST SPRINT 1 — Do not modify signatures unilaterally.
 * All callbacks are emitted from Dispatchers.IO or Dispatchers.Default (ONNX inference).
 * Domain B collects on Dispatchers.Main via ViewModel's viewModelScope.
 */
interface AudioCallbacks {
    /**
     * Called every 100ms with VAD result.
     * @param isSpeech true if the current audio chunk contains speech.
     * @param probability Raw VAD probability score [0.0–1.0].
     */
    fun onVADTriggered(isSpeech: Boolean, probability: Float)

    /**
     * Called when STT inference completes on a speech segment.
     * @param result AppResult.Success(text) or AppResult.Error if inference failed.
     * @param confidence STT model confidence score [0.0–1.0].
     * @param inferenceMs Time taken for ONNX inference in milliseconds.
     */
    fun onSTTResult(result: AppResult<String>, confidence: Float, inferenceMs: Long)

    /**
     * Called when TTS synthesis and playback are complete.
     * @param durationMs Duration of the synthesized audio in milliseconds.
     */
    fun onTTSSynthesisComplete(durationMs: Long)

    /**
     * Called when an audio pipeline error occurs.
     * @param error Typed error with ErrorCode for UI handling.
     */
    fun onAudioError(error: AppResult.Error)

    /**
     * Called when audio focus state changes (gain/loss from other apps or alerts).
     * @param gained true if audio focus was gained, false if lost.
     */
    fun onAudioFocusChanged(gained: Boolean)
}
