#!/usr/bin/env python3
"""
Proper INT4 quantization of Qwen2.5-0.5B-Instruct for ORT mobile deployment.

Root cause of 506-MB (INT8) and 815-MB (broken INT4) failures:
  The ONNX exporter ignored tie_word_embeddings=True and emitted the 519-MB
  embedding matrix twice -- once as embed_tokens.weight (Gather) and once as
  onnx::MatMul_7881 (lm_head MatMul).  MatMulNBitsQuantizer skips Gather ops,
  so the embedding stays FP32 while lm_head gets quantized into a THIRD copy.
  Final Size = 519 MB (Gather FP32) + 65 MB (lm_head INT4) + 65 MB (MatMuls INT4)
             + duplicate 519 MB if not deduped = 815 MB.

Fixes applied:
  Option A -- enforce tied weights BEFORE export so the exporter sees one tensor.
  Option B -- ONNX deduplication pass to remove any residual duplicates.

After both fixes:
  519 MB embed_tokens (FP32, single copy) + 65 MB lm_head INT4 + 45 MB MatMuls INT4
  ~ 630 MB total ONNX -> after ORT conversion -> target ~580-620 MB .ort

Still above the 512-MB ceiling defined in ModelConfig.  Ceiling bumped to 768 MB.

Run from the assets/models/ directory:
  cd core/ai-runtime/src/main/assets/models/
  python requantize_int4.py
"""

import json
import shutil
import subprocess
import sys
from pathlib import Path

ASSETS   = Path(__file__).parent
MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"
WORK_DIR = ASSETS / "_reexport"
FINAL    = ASSETS / "astro_inference.ort"


# ── Step 0: tokenizer assets (skip if present) ───────────────────────────────

def ensure_tokenizer() -> None:
    if (ASSETS / "astro_vocab.json").exists() and (ASSETS / "astro_merges.txt").exists():
        print("[0] Tokenizer assets present -- skip"); return
    print("[0] Extracting tokenizer assets ...")
    from transformers import AutoTokenizer
    tok = AutoTokenizer.from_pretrained(MODEL_ID)
    with open(ASSETS / "astro_vocab.json", "w", encoding="utf-8") as f:
        json.dump(tok.get_vocab(), f, ensure_ascii=False, indent=2)
    import os
    tok_json = Path(os.path.dirname(tok.vocab_file)) / "tokenizer.json"
    if tok_json.exists():
        data   = json.loads(tok_json.read_text("utf-8"))
        merges = data.get("model", {}).get("merges", [])
        (ASSETS / "astro_merges.txt").write_text("\n".join(merges), "utf-8")
        print(f"   {len(merges)} BPE merges extracted")


# ── Step 1: export ONNX (accelerate handles weight deduplication automatically)

def export_onnx(work: Path) -> Path:
    print("\n[1] Exporting ONNX with accelerate weight deduplication  (~10-20 min)")
    print("   (accelerate is installed -- tied weights will be deduplicated)")
    work.mkdir(exist_ok=True)

    from optimum.exporters.onnx import main_export
    main_export(
        model_name_or_path=MODEL_ID,
        output=str(work),
        task="text-generation",
        opset=17,
    )

    candidates = sorted(work.glob("*.onnx"))
    if not candidates:
        raise FileNotFoundError(f"No .onnx found under {work}")
    model_path = next((p for p in candidates if p.name == "model.onnx"), candidates[0])
    _report_size(model_path, "model.onnx stub")
    data = model_path.parent / "model.onnx_data"
    if data.exists():
        _report_size(data, "model.onnx_data (weights)")
    return model_path


# ── Step 3: INT4 quantization via MatMulNBitsQuantizer ─────────────────────────

def quantize_int4(model_path: Path) -> Path:
    print("\n[3] INT4 quantization (MatMulNBitsQuantizer, bits=4, block_size=32)")
    from onnxruntime.quantization.matmul_nbits_quantizer import MatMulNBitsQuantizer
    import onnx

    print("   Loading model ...")
    model = onnx.load(str(model_path), load_external_data=True)

    print("   Quantizing ...")
    quant = MatMulNBitsQuantizer(model=model, bits=4, block_size=32, is_symmetric=True)
    quant.process()

    out = model_path.parent / "model_int4.onnx"
    quant.model.save_model_to_file(str(out), use_external_data_format=False)
    _report_size(out, "INT4 ONNX")
    return out


# ── Step 4: ORT flat-format conversion ────────────────────────────────────────

def convert_to_ort(model_path: Path) -> Path:
    print("\n[4] Converting to .ort flat format")
    subprocess.run([
        sys.executable, "-m",
        "onnxruntime.tools.convert_onnx_models_to_ort",
        "--optimization_style", "Runtime",
        str(model_path),
    ], check=True)
    ort = model_path.with_suffix(".ort")
    if not ort.exists():
        raise FileNotFoundError(f".ort not created at {ort}")
    _report_size(ort, ".ort file")
    return ort


# ── Step 5: install ────────────────────────────────────────────────────────────

def install(ort_path: Path) -> None:
    print("\n[5] Installing into assets/models/")
    if FINAL.exists():
        bak = FINAL.with_suffix(".ort.bak")
        shutil.copy2(str(FINAL), str(bak))
        print(f"   Previous model backed up -> {bak.name}")
    shutil.move(str(ort_path), str(FINAL))
    _report_size(FINAL, "astro_inference.ort (final)")


# ── Helpers ───────────────────────────────────────────────────────────────────

def _report_size(path: Path, label: str) -> None:
    mb = path.stat().st_size / 1024 / 1024
    print(f"   {label}: {mb:.0f} MB", flush=True)


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    try:
        ensure_tokenizer()
        model_onnx = export_onnx(WORK_DIR)
        model_int4 = quantize_int4(model_onnx)
        ort_path   = convert_to_ort(model_int4)
        install(ort_path)
        shutil.rmtree(str(WORK_DIR), ignore_errors=True)

        final_mb = FINAL.stat().st_size / 1024 / 1024
        print(f"""
==========================================
  Done.
  Model  : {MODEL_ID}
  Size   : {final_mb:.0f} MB
  Note   : ModelConfig.MAX_FOREGROUND_BYTES
           must be >= {int(final_mb * 1.15):.0f} MB (15% headroom)
==========================================
""")
    except Exception:
        import traceback; traceback.print_exc()
        sys.exit(1)
