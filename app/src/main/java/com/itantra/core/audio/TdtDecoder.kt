package com.itantra.core.audio

/**
 * T78 — greedy TDT (token-and-duration transducer) decode loop for SraVaani. Pure Kotlin,
 * ONNX-free (kept separate from [STTModule] the same way [CtcDecoder] is), so the loop itself is
 * unit-testable with a stub decoder_joint call instead of a real ONNX session.
 *
 * Ported from the model card's own `sravaani_onnx_infer.py::decode_rnnt` (read in full during
 * T77/T78 Step 1) and cross-checked against `modeling_sravaani.py::_greedy_one`. Every constant
 * below (blank id, vocab size, duration set, max_symbols) comes from that script and the model's
 * `config.json`, not assumed.
 */
object TdtDecoder {
    /** `model.decoding.blank_id`; also the last index of the token-logit slice (vocab ids are 0..4999). */
    const val BLANK_ID = 5000
    /** Real BPE token ids run 0..4999 (5000 pieces); token logits are indices 0..VOCAB_SIZE inclusive. */
    const val VOCAB_SIZE = 5000
    /** TDT duration set, from `config.json`'s `tdt_kwargs`. */
    val DURATIONS = intArrayOf(0, 1, 2, 3, 4)
    /** Hardcoded in the reference script via `range(10)`, not read from a config field. */
    const val MAX_SYMBOLS = 10
    /** `pred_hidden` — LSTM predictor hidden/cell state width. */
    const val PRED_HIDDEN = 640
    /** SentencePiece id 0 (`<unk>`). `sp.decode()` renders it as `" ⁇ "`, not its literal piece
     *  text — confirmed by comparing every one of the 5000 vocabulary pieces against `sp.decode()`
     *  in Colab (T78 Step 3); it was the only mismatch. */
    const val UNK_ID = 0
    private const val UNK_TEXT = " ⁇ "

    /** One `decoder_joint` call's result: 5006-wide logits (5001 token logits + 5 duration
     *  logits, split at [VOCAB_SIZE] + 1) and the updated LSTM state. */
    class StepResult(val logits: FloatArray, val hNew: FloatArray, val cNew: FloatArray)

    /** Abstracts the ONNX `decoder_joint` call so [decode] is testable without a model.
     *  @param encoderFrame the encoder output at one time step, length 1024.
     *  @param lastToken the previously emitted token id (or [BLANK_ID] at utterance start).
     *  @param h LSTM hidden state, length [PRED_HIDDEN].
     *  @param c LSTM cell state, length [PRED_HIDDEN]. */
    fun interface DecoderJointCall {
        fun run(encoderFrame: FloatArray, lastToken: Int, h: FloatArray, c: FloatArray): StepResult
    }

    /**
     * Greedy TDT decode over one utterance's encoder output.
     *
     * @param encoderOut flattened `[1024, encoderLen]`, row-major (`encoderOut[d * encoderLen + t]`)
     *   — i.e. the encoder's `outputs[1, 1024, T]` with the batch dim dropped, in numpy C-order.
     * @param encoderLen number of valid encoder time steps (`encoded_lengths`).
     * @param decoderJoint the decoder_joint call (real ONNX session in [STTModule], a recorded
     *   stub in [TdtDecoderParityTest]).
     * @return decoded token ids, in emission order (never includes [BLANK_ID]).
     */
    fun decode(encoderOut: FloatArray, encoderLen: Int, decoderJoint: DecoderJointCall): List<Int> {
        var h = FloatArray(PRED_HIDDEN)
        var c = FloatArray(PRED_HIDDEN)
        var curLabel = BLANK_ID
        val tokens = mutableListOf<Int>()
        var t = 0

        while (t < encoderLen) {
            val frame = FloatArray(1024) { d -> encoderOut[d * encoderLen + t] }
            var advanced = false

            for (symbolStep in 0 until MAX_SYMBOLS) {
                val result = decoderJoint.run(frame, curLabel, h, c)
                val logits = result.logits

                var tokenPred = 0
                var tokenBest = logits[0]
                for (i in 1..VOCAB_SIZE) {
                    if (logits[i] > tokenBest) { tokenBest = logits[i]; tokenPred = i }
                }

                var durationIdx = 0
                var durationBest = logits[VOCAB_SIZE + 1]
                for (i in 1 until DURATIONS.size) {
                    val v = logits[VOCAB_SIZE + 1 + i]
                    if (v > durationBest) { durationBest = v; durationIdx = i }
                }

                if (tokenPred == BLANK_ID) {
                    t += maxOf(1, DURATIONS[durationIdx])
                    advanced = true
                    break
                }

                tokens.add(tokenPred)
                curLabel = tokenPred
                h = result.hNew
                c = result.cNew
            }
            if (!advanced) t += 1 // safety valve: max_symbols hit without a blank
        }
        return tokens
    }

    /**
     * Decodes a token-id sequence to text using [vocab] (the flat `<piece> <id>` file, same
     * format [CtcDecoder.parseTokens] reads), joining pieces and turning `▁` into a space — the
     * same rule [CtcDecoder.decodeToken] uses for NeMo's BPE vocabularies (both are BPE-family).
     * [UNK_ID] is special-cased to `" ⁇ "` to match `sp.decode()` exactly (confirmed against all
     * 5000 vocabulary pieces in T78 Step 3 — the only divergence found).
     *
     * Matching [CtcDecoder.greedyDecode]'s final `.trim()`, this can differ from `sp.decode()` by
     * a single incidental leading/trailing space when [UNK_ID] is literally the first or last
     * token of the whole utterance (confirmed in T78 Step 3) — a whitespace-only edge case, not a
     * text-content divergence, and consistent with how CTC transcripts are already trimmed here.
     */
    fun decodeToText(tokenIds: List<Int>, vocab: Array<String>): String {
        val sb = StringBuilder()
        for (id in tokenIds) {
            if (id == UNK_ID) sb.append(UNK_TEXT) else sb.append(CtcDecoder.decodeToken(id, vocab))
        }
        return sb.toString().trim()
    }
}
