"""
JUGAAD Astro — Asset Generator
================================
Generates astro_vocab.json and astro_merges.txt from Llama-3.2-1B-Instruct tokenizer.
Downloads tokenizer ONLY (~10 MB) — does NOT download model weights.

Run:
    python generate_assets.py

For astro_inference.ort (requires optimum, ~2GB download, 20-30 min):
    See generate_model.sh in this directory.
"""

import os
import json
import shutil

OUTPUT_DIR   = os.path.dirname(os.path.abspath(__file__))
MODEL_ID     = "Qwen/Qwen2.5-1.5B-Instruct"
VOCAB_FILE   = os.path.join(OUTPUT_DIR, "astro_vocab.json")
MERGES_FILE  = os.path.join(OUTPUT_DIR, "astro_merges.txt")
TEMP_DIR     = os.path.join(OUTPUT_DIR, "_tok_tmp")

print(f"[1/4] Loading tokenizer from {MODEL_ID}…")
from transformers import AutoTokenizer

tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
print(f"      Tokenizer type : {type(tokenizer).__name__}")

# ── 1. Vocabulary ──────────────────────────────────────────────────────────────
print("[2/4] Exporting astro_vocab.json…")
vocab = tokenizer.get_vocab()
print(f"      Vocab size      : {len(vocab):,} tokens")

# Spot-check special tokens
for name, expected_id in [
    ("<|im_start|>",  151644),
    ("<|im_end|>",    151645),
    ("<|endoftext|>", 151643),
]:
    actual = vocab.get(name)
    status = "OK" if actual == expected_id else f"MISMATCH got {actual}"
    print(f"      {name} = {expected_id}  [{status}]")

with open(VOCAB_FILE, "w", encoding="utf-8") as f:
    json.dump(vocab, f, ensure_ascii=False, indent=2)
print(f"      Saved → {VOCAB_FILE}  ({os.path.getsize(VOCAB_FILE) // 1024} KB)")

# ── 2. Merges ──────────────────────────────────────────────────────────────────
print("[3/4] Exporting astro_merges.txt…")

merges = []

# Strategy A: direct save_pretrained → parse tokenizer.json
os.makedirs(TEMP_DIR, exist_ok=True)
tokenizer.save_pretrained(TEMP_DIR)
tok_json_path = os.path.join(TEMP_DIR, "tokenizer.json")

if os.path.exists(tok_json_path):
    with open(tok_json_path, "r", encoding="utf-8") as f:
        tok_data = json.load(f)

    model_section = tok_data.get("model", {})
    raw_merges    = model_section.get("merges", [])

    if raw_merges:
        # Can be list-of-strings ["token_a token_b", …] or list-of-lists [["a","b"], …]
        for m in raw_merges:
            if isinstance(m, list):
                merges.append(f"{m[0]} {m[1]}")
            else:
                merges.append(str(m))
        print(f"      Extracted from tokenizer.json → {len(merges):,} merges")
    else:
        print("      tokenizer.json has no 'merges' key — checking for standalone file…")

# Strategy B: standalone merges.txt (legacy BPE models)
if not merges:
    standalone = os.path.join(TEMP_DIR, "merges.txt")
    if os.path.exists(standalone):
        with open(standalone, "r", encoding="utf-8") as f:
            merges = [l.rstrip() for l in f if l.strip() and not l.startswith("#")]
        print(f"      Copied from standalone merges.txt → {len(merges):,} merges")

if merges:
    with open(MERGES_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(merges))
    print(f"      Saved → {MERGES_FILE}  ({os.path.getsize(MERGES_FILE) // 1024} KB)")
else:
    print("      ⚠ No merges found — tokenizer may use SentencePiece or pure byte-level BPE.")
    print("        Creating empty stub; BpeTokenizer will fall back to byte-level encoding.")
    open(MERGES_FILE, "w").close()

# ── 3. Cleanup ─────────────────────────────────────────────────────────────────
print("[4/4] Cleaning up temp files…")
shutil.rmtree(TEMP_DIR, ignore_errors=True)

print()
print("═" * 60)
print("  astro_vocab.json  ✓")
print(f"  astro_merges.txt  {'✓' if merges else '⚠ empty stub'}")
print()
print("  NEXT: generate astro_inference.ort")
print("  Run: bash generate_model.sh  (needs ~2GB download + 20–30 min)")
print("═" * 60)
