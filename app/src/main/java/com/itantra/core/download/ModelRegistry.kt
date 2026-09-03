package com.itantra.core.download

import com.itantra.domain.model.ModelPack

/**
 * Central registry of essential downloadable model packs for iTantra.
 *
 * Minimalized strictly to the 4 essential on-device neural engines:
 * 1. Silero VAD v4: Voice Activity Detection (pause/stoppage)
 * 2. IndicConformer ONNX: Multilingual STT from meetsync/indic-conformer-onnx-sherpa
 * 3. Indic-Parler-TTS / IndicTTS: Multilingual speech synthesis
 * 4. Neural Transformer NLP: On-device universal AI reasoning assistant
 */
object ModelRegistry {

    // ── Official HuggingFace & Upstream Endpoints ─────────────────────────
    // mijuanlo/silero-vad-onnx (Silero VAD ONNX)
    private const val SILERO_VAD_URL =
        "https://huggingface.co/mijuanlo/silero-vad-onnx/resolve/main/onnx/model.onnx"

    // meetsync/indic-conformer-onnx-sherpa (AI4Bharat IndicConformer INT8 Quantized ASR)
    private const val INDIC_CONFORMER_SHERPA_URL =
        "https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/model.int8.onnx"

    private const val INDIC_CONFORMER_TOKENS_URL =
        "https://huggingface.co/meetsync/indic-conformer-onnx-sherpa/resolve/main/tokens.txt"

    // ai4bharat/indic-parler-tts & IndicTTS Multilingual acoustic voice engine
    private const val INDIC_PARLER_TTS_URL =
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/pratham/medium/hi_IN-pratham-medium.onnx"

    // On-device Qwen2.5-0.5B-Instruct 4-bit Quantized Multilingual LLM (HuggingFace onnx-community)
    private const val QWEN25_05B_URL =
        "https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct/resolve/main/onnx/model_q4.onnx"
    private const val QWEN25_TOKENIZER_URL =
        "https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct/resolve/main/tokenizer.json"

    // ── Model Info ────────────────────────────────────────────────────────
    data class ModelInfo(
        val pack: ModelPack,
        val fileName: String,
        val downloadUrl: String,
        val sha256: String,       // Expected SHA-256 ("unknown" if verified by size check)
        val sizeBytes: Long,
        val secondaryUrl: String? = null,
        val secondaryFileName: String? = null,
        val version: String = "2.0.0"
    )

    val registry: Map<ModelPack, ModelInfo> = mapOf(

        // 1. Voice Activity Detector (Silero VAD from mijuanlo/silero-vad-onnx)
        ModelPack.VAD_MODEL to ModelInfo(
            pack = ModelPack.VAD_MODEL,
            fileName = "silero_vad_v4.onnx",
            downloadUrl = SILERO_VAD_URL,
            sha256 = "unknown",
            sizeBytes = 2_346_000L // 2.24 MB
        ),

        // 2. Speech-to-Text: AI4Bharat IndicConformer ONNX (meetsync/indic-conformer-onnx-sherpa)
        ModelPack.STT_INDIC_CONFORMER to ModelInfo(
            pack = ModelPack.STT_INDIC_CONFORMER,
            fileName = "indicconformer_sherpa_int8.onnx",
            downloadUrl = INDIC_CONFORMER_SHERPA_URL,
            sha256 = "unknown",
            sizeBytes = 197_132_288L, // ~188 MB INT8 ASR
            secondaryUrl = INDIC_CONFORMER_TOKENS_URL,
            secondaryFileName = "tokens.txt"
        ),

        // 3. Text-to-Speech: AI4Bharat Indic-Parler-TTS / IndicTTS Multilingual
        ModelPack.TTS_INDIC_MODEL to ModelInfo(
            pack = ModelPack.TTS_INDIC_MODEL,
            fileName = "hi_vits_int8.onnx",
            downloadUrl = INDIC_PARLER_TTS_URL,
            sha256 = "169964b0871667f6793416d4b35e97357a68ba1ad01df8580c28048989ee7693",
            sizeBytes = 63_516_050L // 60.57 MB
        ),

        // 4. AI Assistant: Qwen2.5-0.5B-Instruct Quantized Multilingual LLM (~350 MB)
        ModelPack.AI_ASSISTANT to ModelInfo(
            pack = ModelPack.AI_ASSISTANT,
            fileName = "qwen25_05b_q4.onnx",
            downloadUrl = QWEN25_05B_URL,
            sha256 = "unknown",
            sizeBytes = 368_000_000L, // ~350 MB
            secondaryUrl = QWEN25_TOKENIZER_URL,
            secondaryFileName = "tokenizer.json"
        )
    )

    fun getInfo(pack: ModelPack): ModelInfo? = registry[pack]

    fun totalSizeBytes(packs: List<ModelPack>): Long =
        packs.sumOf { registry[it]?.sizeBytes ?: 0L }
}
