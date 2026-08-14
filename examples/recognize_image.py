"""Recognize the license plates in a single image and save the annotated result.

Run from the repo root::

    python examples/recognize_image.py path/to/car.jpg
"""

from __future__ import annotations

import sys
from pathlib import Path

import cv2

from barrier_alpr import PlateRecognizer


def main(image_path: str) -> int:
    src = Path(image_path)
    if not src.is_file():
        print(f"Image not found: {src}", file=sys.stderr)
        return 1

    recognizer = PlateRecognizer()
    annotated, result = recognizer.annotate(src)

    if not result.plates:
        print("No plates detected.")
    else:
        for plate in result.plates:
            print(
                f"{plate.text!r} "
                f"(det={plate.detection_confidence:.2f}, "
                f"ocr={plate.ocr_confidence:.2f}, "
                f"bbox={plate.bbox})"
            )

    out_path = src.with_name(f"{src.stem}_annotated{src.suffix}")
    cv2.imwrite(str(out_path), annotated)
    print(f"Wrote annotated image to {out_path}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python recognize_image.py <image>", file=sys.stderr)
        raise SystemExit(2)
    raise SystemExit(main(sys.argv[1]))
