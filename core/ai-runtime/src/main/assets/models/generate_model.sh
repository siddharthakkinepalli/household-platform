#!/usr/bin/env bash
# ============================================================
# JUGAAD Astro — ORT Model Generator
# Target: Qwen/Qwen2.5-1.5B-Instruct
#
# Requirements:
#   pip install optimum[onnxruntime] onnxruntime-tools transformers
# ============================================================

set -e

MODEL_ID="Qwen/Qwen2.5-0.5B-Instruct"
ONNX_OUT="./onnx_output"
QUANT_OUT="./quantized_output"
FINAL_ORT="./astro_inference.ort"
VOCAB_OUT="./astro_vocab.json"
MERGES_OUT="./astro_merges.txt"

echo "=== [1/5] Extracting Qwen Tokenizer Assets ==="
python -c "
from transformers import AutoTokenizer
import json, os
t = AutoTokenizer.from_pretrained('$MODEL_ID')
with open('$VOCAB_OUT', 'w', encoding='utf-8') as f:
    json.dump(t.get_vocab(), f, ensure_ascii=False, indent=2)
# Qwen2 tokenizer merges extraction
try:
    with open(t.vocab_file.replace('vocab.json', 'merges.txt'), 'r', encoding='utf-8') as f:
        with open('$MERGES_OUT', 'w', encoding='utf-8') as out:
            out.write(f.read())
except:
    print('Merges extraction skipped (using unified vocab)')
"

echo "=== [2/5] Exporting Qwen2.5 to ONNX format ==="
optimum-cli export onnx \
  --model "$MODEL_ID" \
  --task text-generation-with-past \
  --opset 17 \
  "$ONNX_OUT/"

echo "=== [3/5] INT4 quantization for NPU/ARM64 ==="
optimum-cli onnxruntime quantize \
  --onnx_model "$ONNX_OUT/model.onnx" \
  --output "$QUANT_OUT/" \
  --weight_type int4 \
  --arm64

echo "=== [4/5] Converting to mobile .ort format ==="
python -m onnxruntime.tools.convert_onnx_models_to_ort \
  "$QUANT_OUT/model_quantized.onnx"

echo "=== [5/5] Finalizing Assets ==="
mv "$QUANT_OUT/model_quantized.ort" "$FINAL_ORT"

echo ""
echo "=============================================="
echo "  Assets generated for Jugaad Astro Runtime"
echo "  Model: $MODEL_ID"
echo "  BOS: 151644 | EOS: 151643 | EOT: 151645"
echo "=============================================="

# Cleanup
rm -rf "$ONNX_OUT" "$QUANT_OUT"
echo "  Working directories cleaned up."

# Line 63
# Line 64
# Line 65
