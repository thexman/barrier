"""Open the default webcam and stream annotated ALPR predictions.

Press ``q`` or ``ESC`` in the window to exit. Run from the repo root::

    python examples/recognize_webcam.py
"""

from __future__ import annotations

from barrier_alpr import PlateRecognizer
from barrier_alpr.video import run_webcam


def main() -> None:
    recognizer = PlateRecognizer()
    run_webcam(recognizer, camera_index=0, every_n_frames=1)


if __name__ == "__main__":
    main()
