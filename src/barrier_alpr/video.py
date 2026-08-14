"""Video / webcam helpers built on top of :class:`PlateRecognizer`."""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

from barrier_alpr.recognizer import PlateRecognizer, RecognitionResult


@dataclass
class VideoFrameResult:
    frame_index: int
    timestamp_ms: float
    result: RecognitionResult
    annotated: np.ndarray


def iter_video(
    recognizer: PlateRecognizer,
    source: str | int | Path,
    every_n_frames: int = 1,
) -> Iterator[VideoFrameResult]:
    """Yield ALPR results for each processed frame of a video or camera stream.

    Parameters
    ----------
    recognizer:
        Configured :class:`PlateRecognizer`.
    source:
        Path to a video file, or an integer camera index (``0`` for the default
        webcam).
    every_n_frames:
        Skip factor. ``1`` processes every frame, ``2`` every second frame, etc.
    """
    if every_n_frames < 1:
        raise ValueError("every_n_frames must be >= 1")

    cap = cv2.VideoCapture(source if isinstance(source, int) else str(source))
    if not cap.isOpened():
        raise RuntimeError(f"Could not open video source: {source!r}")

    try:
        frame_index = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break

            if frame_index % every_n_frames == 0:
                annotated, result = recognizer.annotate(frame)
                timestamp = float(cap.get(cv2.CAP_PROP_POS_MSEC))
                yield VideoFrameResult(
                    frame_index=frame_index,
                    timestamp_ms=timestamp,
                    result=result,
                    annotated=annotated,
                )
            frame_index += 1
    finally:
        cap.release()


def run_webcam(
    recognizer: PlateRecognizer,
    camera_index: int = 0,
    window_name: str = "barrier-alpr",
    every_n_frames: int = 1,
) -> None:
    """Show the annotated webcam feed until the user presses ``q`` or ``ESC``."""
    for item in iter_video(recognizer, camera_index, every_n_frames=every_n_frames):
        cv2.imshow(window_name, item.annotated)
        key = cv2.waitKey(1) & 0xFF
        if key in (ord("q"), 27):
            break
    cv2.destroyAllWindows()
