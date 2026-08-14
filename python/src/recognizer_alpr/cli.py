"""Command line interface for barrier-recognizer.

Examples
--------
Recognize plates in a single image::

    barrier-recognizer path/to/car.jpg --json result.json --save annotated.jpg

Recognize plates in every image inside a directory::

    barrier-recognizer ./photos --json results.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import cv2

from recognizer_alpr.recognizer import (
    PlateRecognizer,
    RecognitionResult,
    build_summary,
    format_plates,
)


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="barrier-recognizer",
        description="Automatic license plate recognition powered by fast-alpr.",
    )
    parser.add_argument("path", type=Path, help="Image file or directory.")
    parser.add_argument(
        "--save",
        type=Path,
        default=None,
        help=(
            "Where to write annotated output. For a single image pass a file "
            "path; for a directory pass a directory."
        ),
    )
    parser.add_argument(
        "--json",
        dest="json_out",
        type=Path,
        required=True,
        help="Write structured results as JSON to this file (required).",
    )
    parser.add_argument(
        "--detector-model",
        default="yolo-v9-t-384-license-plate-end2end",
        help="fast-alpr plate detector model name.",
    )
    parser.add_argument(
        "--ocr-model",
        default="cct-xs-v2-global-model",
        help="fast-alpr OCR model name.",
    )
    parser.add_argument(
        "--detector-confidence",
        type=float,
        default=0.6,
        help="Minimum detector confidence (0..1).",
    )
    parser.add_argument(
        "--ocr-device",
        default="auto",
        choices=["auto", "cpu", "cuda"],
        help="Device to run OCR on.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return _run(args)


def _collect_images(path: Path) -> list[Path]:
    if path.is_file():
        return [path]
    if not path.is_dir():
        raise FileNotFoundError(f"Path not found: {path}")
    return sorted(p for p in path.iterdir() if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)


def _run(args: argparse.Namespace) -> int:
    recognizer = PlateRecognizer(
        detector_model=args.detector_model,
        ocr_model=args.ocr_model,
        detector_conf_thresh=args.detector_confidence,
        ocr_device=args.ocr_device,
    )
    images = _collect_images(args.path)
    if not images:
        print(f"No images found under {args.path}", file=sys.stderr)
        return 1

    save_target: Path | None = args.save
    save_is_dir = save_target is not None and (
        save_target.is_dir() or (len(images) > 1 and save_target.suffix == "")
    )
    if save_is_dir and save_target is not None:
        save_target.mkdir(parents=True, exist_ok=True)

    results: list[RecognitionResult] = []
    for image_path in images:
        annotated, result = recognizer.annotate(image_path)
        results.append(result)

        if save_target is not None:
            if save_is_dir:
                out_path = save_target / f"{image_path.stem}_annotated{image_path.suffix or '.jpg'}"
            else:
                out_path = save_target
            out_path.parent.mkdir(parents=True, exist_ok=True)
            cv2.imwrite(str(out_path), annotated)
            result.annotated_path = str(out_path)

    print(format_plates(results))

    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(
        json.dumps(build_summary(results), indent=2),
        encoding="utf-8",
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
