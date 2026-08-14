"""Command line interface for barrier-alpr.

Examples
--------
Recognize plates in a single image::

    barrier-alpr image path/to/car.jpg --save annotated.jpg

Recognize plates in every image inside a directory (recursively)::

    barrier-alpr image ./photos --recursive --json results.json

Process a video file and write an annotated MP4::

    barrier-alpr video traffic.mp4 --save annotated.mp4

Open the default webcam and stream results::

    barrier-alpr webcam
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Iterable

import cv2

from barrier_alpr.recognizer import (
    PlateRecognizer,
    RecognitionResult,
    format_plates,
)
from barrier_alpr.video import iter_video, run_webcam


IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="barrier-alpr",
        description="Automatic license plate recognition powered by fast-alpr.",
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
        "--detector-conf",
        type=float,
        default=0.4,
        help="Minimum detector confidence (0..1).",
    )
    parser.add_argument(
        "--ocr-device",
        default="auto",
        choices=["auto", "cpu", "cuda"],
        help="Device to run OCR on.",
    )

    sub = parser.add_subparsers(dest="command", required=True)

    p_img = sub.add_parser("image", help="Recognize plates in an image or folder.")
    p_img.add_argument("path", type=Path, help="Image file or directory.")
    p_img.add_argument(
        "--recursive",
        action="store_true",
        help="Recurse into subdirectories when a folder is given.",
    )
    p_img.add_argument(
        "--save",
        type=Path,
        default=None,
        help=(
            "Where to write annotated output. For a single image pass a file "
            "path; for a directory pass a directory."
        ),
    )
    p_img.add_argument(
        "--json",
        dest="json_out",
        type=Path,
        default=None,
        help="Write structured results as JSON to this file.",
    )

    p_vid = sub.add_parser("video", help="Recognize plates in a video file.")
    p_vid.add_argument("path", type=Path, help="Path to the video file.")
    p_vid.add_argument(
        "--save",
        type=Path,
        default=None,
        help="Write an annotated MP4 to this path.",
    )
    p_vid.add_argument(
        "--every",
        type=int,
        default=1,
        help="Process every N-th frame (1 = every frame).",
    )
    p_vid.add_argument(
        "--json",
        dest="json_out",
        type=Path,
        default=None,
        help="Write per-frame results as JSON to this file.",
    )
    p_vid.add_argument(
        "--show",
        action="store_true",
        help="Display annotated frames in a window while processing.",
    )

    p_cam = sub.add_parser("webcam", help="Stream recognition from a webcam.")
    p_cam.add_argument(
        "--camera",
        type=int,
        default=0,
        help="Camera index (default 0).",
    )
    p_cam.add_argument(
        "--every",
        type=int,
        default=1,
        help="Process every N-th frame (1 = every frame).",
    )

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    recognizer = PlateRecognizer(
        detector_model=args.detector_model,
        ocr_model=args.ocr_model,
        detector_conf_thresh=args.detector_conf,
        ocr_device=args.ocr_device,
    )

    if args.command == "image":
        return _cmd_image(recognizer, args)
    if args.command == "video":
        return _cmd_video(recognizer, args)
    if args.command == "webcam":
        run_webcam(recognizer, camera_index=args.camera, every_n_frames=args.every)
        return 0

    parser.error(f"Unknown command: {args.command}")
    return 2


def _collect_images(path: Path, recursive: bool) -> list[Path]:
    if path.is_file():
        return [path]
    if not path.is_dir():
        raise FileNotFoundError(f"Path not found: {path}")
    iterator: Iterable[Path] = path.rglob("*") if recursive else path.iterdir()
    return sorted(p for p in iterator if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS)


def _cmd_image(recognizer: PlateRecognizer, args: argparse.Namespace) -> int:
    images = _collect_images(args.path, recursive=args.recursive)
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

    print(format_plates(results))

    if args.json_out is not None:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(
            json.dumps([r.as_dict() for r in results], indent=2),
            encoding="utf-8",
        )

    return 0


def _cmd_video(recognizer: PlateRecognizer, args: argparse.Namespace) -> int:
    if not args.path.is_file():
        print(f"Video file not found: {args.path}", file=sys.stderr)
        return 1

    writer: cv2.VideoWriter | None = None
    per_frame: list[dict] = []
    try:
        for item in iter_video(recognizer, args.path, every_n_frames=args.every):
            if args.save is not None:
                if writer is None:
                    args.save.parent.mkdir(parents=True, exist_ok=True)
                    h, w = item.annotated.shape[:2]
                    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
                    writer = cv2.VideoWriter(str(args.save), fourcc, 25.0, (w, h))
                writer.write(item.annotated)

            if args.show:
                cv2.imshow("barrier-alpr", item.annotated)
                if (cv2.waitKey(1) & 0xFF) in (ord("q"), 27):
                    break

            per_frame.append(
                {
                    "frame_index": item.frame_index,
                    "timestamp_ms": item.timestamp_ms,
                    **item.result.as_dict(),
                }
            )
            texts = item.result.texts
            if texts:
                print(f"frame {item.frame_index:>6} @ {item.timestamp_ms:8.1f}ms: {', '.join(texts)}")
    finally:
        if writer is not None:
            writer.release()
        if args.show:
            cv2.destroyAllWindows()

    if args.json_out is not None:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(per_frame, indent=2), encoding="utf-8")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
