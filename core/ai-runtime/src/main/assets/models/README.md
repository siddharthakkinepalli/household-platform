# Astro AI Runtime Models

## ONNX Model Excluded from Repository

The `astro_inference.onnx` file (734 MB) is **not committed to Git** due to GitHub's 100 MB file size limit.

## Generate the Model Locally

**Prerequisites:**
- Python 3.8+
- PyTorch
- transformers
- optimum[onnxruntime]

**Steps:**

1. **Install dependencies:**
```bash
pip install torch transformers optimum[onnxruntime]
```

2. **Run the export script:**
```bash
cd core/ai-runtime/src/main/assets/models
bash generate_model.sh
```

Or manually run the Python export command from your IDE/terminal.

3. **Verify the output:**
The script will generate:
- `astro_inference.onnx` (734 MB)
- `astro_vocab.json`
- `astro_merges.txt`

## Model Details

- **Base Model:** Qwen/Qwen2.5-0.5B-Instruct
- **Task:** Text generation (Vedic astrology predictions)
- **Format:** ONNX (optimized for Android NNAPI/CPU)
- **Quantization:** INT4 (via `requantize_int4.py`)
- **Opset:** 17

## Notes

- The vocab and merges files are committed to the repo
- The ONNX model is generated locally by each developer
- CI/CD: Model generation step required before building the app
