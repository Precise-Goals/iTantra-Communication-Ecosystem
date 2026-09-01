#!/usr/bin/env python3
"""
iTantra Model Export Pipeline — Step 1: IndicConformer STT → ONNX

Exports AI4Bharat IndicConformer multilingual checkpoint to ONNX format
for use with ONNX Runtime Mobile on Android.

Usage:
    python export_indicconformer.py --output ../app/src/main/assets/models/

Prerequisites:
    pip install -r requirements.txt
    # Download checkpoint from AI4Bharat HuggingFace:
    # https://huggingface.co/ai4bharat/indicconformer_stt_hi_hybrid_rnnt_large

Author: Gaurav (Domain A — Engine)
"""

import argparse
import os
import sys
from pathlib import Path

import torch
import onnx
import onnxruntime as ort
import numpy as np


def export_indicconformer(checkpoint_path: str, output_dir: str, opset: int = 17):
    """
    Export IndicConformer PyTorch checkpoint to ONNX.
    
    Args:
        checkpoint_path: Path to .nemo or .pt checkpoint file
        output_dir: Directory to save the exported ONNX model
        opset: ONNX opset version (17 recommended for ORT Mobile)
    """
    print(f"Loading IndicConformer checkpoint: {checkpoint_path}")
    
    # For NeMo checkpoints:
    try:
        import nemo.collections.asr as nemo_asr
        model = nemo_asr.models.EncDecMultiTaskModel.restore_from(checkpoint_path)
        model.eval()
        print(f"Loaded NeMo ASR model: {model.__class__.__name__}")
    except ImportError:
        print("NeMo not available. Loading raw PyTorch checkpoint...")
        checkpoint = torch.load(checkpoint_path, map_location="cpu")
        print("Checkpoint keys:", list(checkpoint.keys())[:5])
        print("Please use a NeMo checkpoint for full export support.")
        return

    # Dummy input for tracing (1 second of 16kHz audio → 100 mel frames)
    dummy_audio = torch.randn(1, 16000)  # 1s at 16kHz
    dummy_lengths = torch.tensor([16000])

    output_path = os.path.join(output_dir, "indicconformer_fp32.onnx")
    os.makedirs(output_dir, exist_ok=True)

    print(f"Exporting to ONNX (opset {opset})...")
    try:
        # Export via torch.onnx.export
        torch.onnx.export(
            model,
            (dummy_audio, dummy_lengths),
            output_path,
            opset_version=opset,
            input_names=["audio_signal", "length"],
            output_names=["logprobs"],
            dynamic_axes={
                "audio_signal": {0: "batch", 1: "time"},
                "length": {0: "batch"},
                "logprobs": {0: "batch", 1: "time", 2: "vocab"}
            },
            do_constant_folding=True
        )
        print(f"✅ Exported to: {output_path}")
        print(f"   Model size: {os.path.getsize(output_path) / 1e6:.1f} MB")
    except Exception as e:
        print(f"❌ Export failed: {e}")
        print("Hint: Some NeMo models require custom export. Check AI4Bharat GitHub for export scripts.")
        return None

    # Validate ONNX model
    print("Validating ONNX model...")
    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)
    print("✅ ONNX model is valid")

    return output_path


def export_silero_vad(output_dir: str):
    """
    Export Silero VAD v4 to ONNX from PyTorch Hub.
    """
    print("Loading Silero VAD from torch.hub...")
    model, utils = torch.hub.load(
        repo_or_dir="snakers4/silero-vad",
        model="silero_vad",
        force_reload=False,
        onnx=True
    )

    output_path = os.path.join(output_dir, "silero_vad.onnx")
    os.makedirs(output_dir, exist_ok=True)

    # Silero provides ONNX directly via onnx=True parameter
    # Copy the ONNX file to our assets directory
    import shutil
    vad_onnx_path = os.path.join(torch.hub.get_dir(), "snakers4_silero-vad_master", "files", "silero_vad.onnx")
    if os.path.exists(vad_onnx_path):
        shutil.copy(vad_onnx_path, output_path)
        print(f"✅ Silero VAD exported to: {output_path}")
        print(f"   Model size: {os.path.getsize(output_path) / 1e6:.2f} MB")
    else:
        # Alternative: direct torch.onnx.export
        dummy_audio = torch.randn(1, 1600)  # 100ms at 16kHz
        dummy_sr = torch.tensor(16000)
        dummy_h = torch.zeros(2, 1, 64)
        dummy_c = torch.zeros(2, 1, 64)
        torch.onnx.export(
            model,
            (dummy_audio, dummy_sr, dummy_h, dummy_c),
            output_path,
            opset_version=16,
            input_names=["input", "sr", "h", "c"],
            output_names=["output", "hn", "cn"],
            dynamic_axes={"input": {1: "time"}}
        )
        print(f"✅ Silero VAD exported via torch.onnx.export to: {output_path}")


def main():
    parser = argparse.ArgumentParser(description="Export IndicConformer and Silero VAD to ONNX")
    parser.add_argument("--checkpoint", type=str, help="Path to IndicConformer .nemo checkpoint")
    parser.add_argument("--output", type=str, default="../app/src/main/assets/models/", help="Output directory for ONNX models")
    parser.add_argument("--vad-only", action="store_true", help="Export only Silero VAD")
    args = parser.parse_args()

    output_dir = Path(args.output).resolve()
    print(f"Output directory: {output_dir}")

    if not args.vad_only:
        if not args.checkpoint:
            print("❌ --checkpoint required for STT export. Download from:")
            print("   https://huggingface.co/ai4bharat/indicconformer_stt_multilingual_hybrid_rnnt_large")
            sys.exit(1)
        export_indicconformer(args.checkpoint, str(output_dir))

    print("\n--- Exporting Silero VAD ---")
    export_silero_vad(str(output_dir / ".."))  # Goes to assets/models/


if __name__ == "__main__":
    main()
