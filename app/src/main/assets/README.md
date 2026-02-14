# MiniJarvis Model Assets

This directory should contain the Gemma 2B quantized model file.

## Required File

**gemma-2b-q4_0.gguf** - Google Gemma 2B model, 4-bit quantized GGUF format

## How to Obtain

1. Download from HuggingFace:
   - Visit: https://huggingface.co/leliuga/ggml-gemma-2b-v1-q4_0
   - Download: `gemma-2b-v1-q4_0.gguf`

2. Place this file in this directory:
   ```
   app/src/main/assets/gemma-2b-q4_0.gguf
   ```

## Model Specifications

- **Model**: Google Gemma 2B
- **Quantization**: Q4_0 (4-bit)
- **Format**: GGUF
- **Size**: ~2GB
- **Context**: 1024 tokens
- **Target devices**: 4GB+ RAM

## Notes

- The app includes a MockLLMEngine for testing without the model
- For production, add the actual model file here
- Ensure sufficient device storage (3GB+ recommended)