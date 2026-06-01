package com.jugaad.core.airuntime.model

/**
 * Compile-time constants for the local inference runtime.
 *
 * ══ BASE MODEL: Qwen/Qwen2.5-0.5B-Instruct, INT4 quantized ══
 *   - NOT gated — downloads freely.
 *   - 151,936 token tiktoken vocabulary.
 *   - Shipped as .onnx (not .ort) — ORT's flat-format converter inflated
 *     this model from 734 MB to 1.25 GB due to pre-baked operator metadata.
 *     ORT Runtime loads .onnx natively at full speed.
 *
 * INT4 quantization: MatMulNBitsQuantizer (bits=4, block_size=32, symmetric)
 * Deduplication:     accelerate-backed lm_head/embed_tokens dedup (saves 519 MB)
 * Final size:        734 MB (down from 506 MB INT8 original)
 *
 * Asset files:
 *   astro_inference.onnx  (734 MB, INT4 — run requantize_int4.py to regenerate)
 *   astro_vocab.json      (3.4 MB, 151,936 tokens)
 *   astro_merges.txt      (1.8 MB, BPE merges)
 */
object ModelConfig {

    // ── Asset paths ───────────────────────────────────────────────────────────
    const val MODEL_ASSET    = "models/astro_inference.onnx"
    const val VOCAB_ASSET    = "models/astro_vocab.json"
    const val MERGES_ASSET   = "models/astro_merges.txt"

    // ── Model identity ────────────────────────────────────────────────────────
    const val MODEL_VERSION  = "qwen2.5-0.5b-int4-v2"

    // ── Inference parameters ──────────────────────────────────────────────────
    const val TEMPERATURE    = 0.2f
    const val TOP_P          = 0.9f

    // ── Sequence limits ───────────────────────────────────────────────────────
    const val MAX_INPUT_TOKENS  = 512
    const val MAX_OUTPUT_TOKENS = 256

    // ── Memory ceilings (bytes) ───────────────────────────────────────────────
    // Model on disk: 734 MB INT4 ONNX.
    // ORT session overhead (operator kernels, execution plan): ~150 MB.
    // App heap: ~16 MB. Total: ~900 MB. Ceiling set to 1 024 MB (1 GB) for safety.
    // Minimum device RAM for reliable inference: 4 GB.
    const val MAX_FOREGROUND_BYTES = 1024L * 1024 * 1024
    const val MAX_BACKGROUND_BYTES = 64L   * 1024 * 1024

    // ── Thread pool sizes ─────────────────────────────────────────────────────
    const val INTER_OP_THREADS = 2
    const val INTRA_OP_THREADS = 4

    // ── Tensor buffer pool ────────────────────────────────────────────────────
    const val TENSOR_POOL_SIZE   = 4

    // Qwen2.5-0.5B vocab = 151,936 tokens (from config.json).
    const val QWEN_VOCAB_SIZE    = 151_936
    const val LOGIT_BUFFER_SIZE  = QWEN_VOCAB_SIZE

    // ── Special token IDs — Qwen2.5-Instruct ────────────
    const val BOS_ID  = 151_644   // <|im_start|>
    const val EOS_ID  = 151_643   // <|endoftext|>
    const val EOT_ID  = 151_645   // <|im_end|>
    const val PAD_ID  = 151_643
    const val UNK_ID  = -1
}
