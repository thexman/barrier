"""Recognize plates in every N-th frame of a video and print unique plates.

Run from the repo root::

    python examples/recognize_video.py path/to/video.mp4
"""

from __future__ import annotations

import sys
from pathlib import Path

from barrier_alpr import PlateRecognizer
from barrier_alpr.video import iter_video


def main(video: str, every_n_frames: int = 5) -> int:
    path = Path(video)
    if not path.is_file():
        print(f"Video not found: {path}", file=sys.stderr)
        return 1

    recognizer = PlateRecognizer()
    seen: set[str] = set()

    for item in iter_video(recognizer, path, every_n_frames=every_n_frames):
        for plate in item.result.plates:
            if plate.text and plate.text not in seen:
                seen.add(plate.text)
                print(
                    f"frame {item.frame_index:>6} @ {item.timestamp_ms:8.1f}ms  "
                    f"{plate.text}  (ocr={plate.ocr_confidence:.2f})"
                )

    print(f"\nUnique plates recognized: {len(seen)}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python recognize_video.py <video> [every_n_frames]", file=sys.stderr)
        raise SystemExit(2)
    step = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    raise SystemExit(main(sys.argv[1], step))
