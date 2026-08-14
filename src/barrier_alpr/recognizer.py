"""High-level license plate recognizer built on top of `fast-alpr`.

This module wraps the `fast_alpr.ALPR` class with:
- Simple dataclasses for results (no dependency on fast-alpr internals for callers).
- Convenience helpers to accept either a file path or a numpy image.
- A method to render annotated images.
"""

from __future__ import annotations

import statistics
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Sequence

import cv2
import numpy as np
from fast_alpr import ALPR


ImageInput = str | Path | np.ndarray


@dataclass(frozen=True)
class PlateDetection:
    """One detected license plate on a single frame."""

    text: str
    """OCR result. May be an empty string if OCR failed."""

    detection_confidence: float
    """Confidence of the plate detector (0..1)."""

    ocr_confidence: float
    """Average confidence of the OCR model (0..1)."""

    bbox: tuple[int, int, int, int]
    """Bounding box as ``(x1, y1, x2, y2)`` in pixel coordinates."""

    region: str | None = None
    """Optional region / country prediction (only some OCR models emit this)."""

    region_confidence: float | None = None
    """Confidence for :attr:`region`, if available."""

    def as_dict(self) -> dict:
        return {
            "text": self.text,
            "detection_confidence": self.detection_confidence,
            "ocr_confidence": self.ocr_confidence,
            "bbox": list(self.bbox),
            "region": self.region,
            "region_confidence": self.region_confidence,
        }


@dataclass
class RecognitionResult:
    """All plates detected on a single image / frame."""

    source: str
    """Human readable source identifier (path or ``"<frame>"``)."""

    plates: list[PlateDetection] = field(default_factory=list)

    @property
    def texts(self) -> list[str]:
        return [p.text for p in self.plates if p.text]

    def best(self) -> PlateDetection | None:
        """Return the detection with the highest OCR confidence, if any."""
        if not self.plates:
            return None
        return max(self.plates, key=lambda p: p.ocr_confidence)

    def as_dict(self) -> dict:
        return {
            "source": self.source,
            "plates": [p.as_dict() for p in self.plates],
        }


class PlateRecognizer:
    """Recognize license plates in images and video frames.

    Parameters
    ----------
    detector_model:
        Name of the plate detection model. See fast-alpr docs for options.
    ocr_model:
        Name of the OCR model. See fast-alpr docs for options.
    detector_conf_thresh:
        Minimum detector confidence to keep a plate.
    ocr_device:
        Where to run OCR: ``"auto"``, ``"cpu"`` or ``"cuda"``.
    """

    def __init__(
        self,
        detector_model: str = "yolo-v9-t-384-license-plate-end2end",
        ocr_model: str = "cct-xs-v2-global-model",
        detector_conf_thresh: float = 0.4,
        ocr_device: str = "auto",
    ) -> None:
        self._alpr = ALPR(
            detector_model=detector_model,
            ocr_model=ocr_model,
            detector_conf_thresh=detector_conf_thresh,
            ocr_device=ocr_device,
        )

    def recognize(self, image: ImageInput) -> RecognitionResult:
        """Run detection + OCR on a single image."""
        frame, source = _load_image(image)
        raw_results = self._alpr.predict(frame)
        plates = [_to_plate_detection(r) for r in raw_results]
        return RecognitionResult(source=source, plates=plates)

    def recognize_many(self, images: Iterable[ImageInput]) -> list[RecognitionResult]:
        return [self.recognize(img) for img in images]

    def annotate(self, image: ImageInput) -> tuple[np.ndarray, RecognitionResult]:
        """Return an annotated image plus the parsed results."""
        frame, source = _load_image(image)
        drawn = self._alpr.draw_predictions(frame)
        annotated = drawn.image
        plates = [_to_plate_detection(r) for r in drawn.results]
        return annotated, RecognitionResult(source=source, plates=plates)


def _load_image(image: ImageInput) -> tuple[np.ndarray, str]:
    if isinstance(image, np.ndarray):
        return image, "<frame>"

    path = Path(image)
    if not path.is_file():
        raise FileNotFoundError(f"Image not found: {path}")

    frame = cv2.imread(str(path))
    if frame is None:
        raise ValueError(f"Could not decode image: {path}")
    return frame, str(path)


def _to_plate_detection(raw) -> PlateDetection:
    """Convert a fast-alpr ``ALPRResult`` into our dataclass.

    fast-alpr returns objects with ``detection`` (bbox + confidence) and
    ``ocr`` (text + confidence). ``ocr`` can be ``None`` when OCR failed, and
    ``ocr.confidence`` can be either a single float **or** a per-character list
    (see ``fast_alpr.base.OcrResult``); we average the list to a scalar just
    like fast-alpr does internally when it draws overlays.
    """
    detection = getattr(raw, "detection", None)
    ocr = getattr(raw, "ocr", None)

    bbox_obj = getattr(detection, "bounding_box", None) if detection is not None else None
    if bbox_obj is not None:
        bbox = (
            int(getattr(bbox_obj, "x1", 0)),
            int(getattr(bbox_obj, "y1", 0)),
            int(getattr(bbox_obj, "x2", 0)),
            int(getattr(bbox_obj, "y2", 0)),
        )
    else:
        bbox = (0, 0, 0, 0)

    det_conf = _to_scalar_confidence(getattr(detection, "confidence", 0.0))

    if ocr is None:
        text = ""
        ocr_conf = 0.0
        region: str | None = None
        region_conf: float | None = None
    else:
        text = str(getattr(ocr, "text", "") or "")
        ocr_conf = _to_scalar_confidence(getattr(ocr, "confidence", 0.0))
        region = getattr(ocr, "region", None)
        raw_region_conf = getattr(ocr, "region_confidence", None)
        region_conf = float(raw_region_conf) if raw_region_conf is not None else None

    return PlateDetection(
        text=text,
        detection_confidence=det_conf,
        ocr_confidence=ocr_conf,
        bbox=bbox,
        region=region,
        region_confidence=region_conf,
    )


def _to_scalar_confidence(value) -> float:
    """Collapse ``float | list[float] | None`` into a single float in [0, 1]."""
    if value is None:
        return 0.0
    if isinstance(value, (list, tuple)):
        if not value:
            return 0.0
        return float(statistics.mean(float(v) for v in value))
    if isinstance(value, np.ndarray):
        if value.size == 0:
            return 0.0
        return float(value.mean())
    return float(value)


def format_plates(results: Sequence[RecognitionResult]) -> str:
    """Compact multi-line summary of a batch of results."""
    lines: list[str] = []
    for r in results:
        if not r.plates:
            lines.append(f"{r.source}: <no plate detected>")
            continue
        for p in r.plates:
            lines.append(
                f"{r.source}: {p.text or '<no text>'} "
                f"(det={p.detection_confidence:.2f}, ocr={p.ocr_confidence:.2f}, "
                f"bbox={p.bbox})"
            )
    return "\n".join(lines)
