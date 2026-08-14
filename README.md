# barrier-alpr

Automatic license-plate recognition for the **barrier** project, built on top of
[`fast-alpr`](https://github.com/ankandrew/fast-alpr).

`fast-alpr` provides fast ONNX detection + OCR models out of the box. This
project wraps them in a small, ergonomic API and a CLI that can:

- recognize plates in a single image or a whole folder of images,
- process a video file (optionally saving an annotated MP4),
- stream from a webcam in real time,
- export structured results as JSON.

## Requirements

- Python **3.10 – 3.13**
- Windows, Linux or macOS

The default install uses the CPU ONNX runtime, which is enough for images and
low-resolution video. For faster GPU inference see
[Alternative install targets](#alternative-install-targets).

## Installation

Clone the repo, create a virtual environment, then install the package:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install --upgrade pip
pip install -e .
```

Or, if you just want the runtime dependencies without installing the package:

```powershell
pip install -r requirements.txt
```

The first ALPR run will download the detection and OCR ONNX models from the
model hub and cache them on disk.

### Alternative install targets

`fast-alpr` supports several ONNX Runtime backends. Pick the one that matches
your hardware:

| Hardware                | Install command                                  |
| ----------------------- | ------------------------------------------------ |
| CPU (default)           | `pip install fast-alpr[onnx]`                    |
| NVIDIA GPU (CUDA)       | `pip install fast-alpr[onnx-gpu]`                |
| Intel (OpenVINO)        | `pip install fast-alpr[onnx-openvino]`           |
| Windows (DirectML)      | `pip install fast-alpr[onnx-directml]`           |
| Qualcomm (QNN)          | `pip install fast-alpr[onnx-qnn]`                |

## Command line usage

The package installs a `barrier-alpr` console script. All subcommands share
optional model flags: `--detector-model`, `--ocr-model`, `--detector-conf`,
`--ocr-device`.

### Recognize a single image

```powershell
barrier-alpr image path\to\car.jpg --save annotated.jpg
```

### Recognize every image in a folder (recursively) and export JSON

```powershell
barrier-alpr image .\photos --recursive --save .\outputs --json results.json
```

If `--save` is a directory, each annotated image is written next to the source
name (`<name>_annotated.<ext>`). If it is a file path, a single image is
written there.

### Process a video

```powershell
barrier-alpr video traffic.mp4 --save annotated.mp4 --every 2 --json frames.json
```

`--every N` processes every N-th frame (useful to speed up long clips).
Pass `--show` to also preview the annotated frames in a window while the video
is being processed.

### Live webcam

```powershell
barrier-alpr webcam --camera 0
```

Press `q` or `Esc` in the window to stop.

You can also invoke the CLI as a module:

```powershell
python -m barrier_alpr image path\to\car.jpg
```

## Python API

```python
from barrier_alpr import PlateRecognizer

recognizer = PlateRecognizer(
    detector_model="yolo-v9-t-384-license-plate-end2end",
    ocr_model="cct-xs-v2-global-model",
    detector_conf_thresh=0.4,
    ocr_device="auto",
)

result = recognizer.recognize("path/to/car.jpg")
for plate in result.plates:
    print(plate.text, plate.detection_confidence, plate.ocr_confidence, plate.bbox)

best = result.best()
if best:
    print("Best guess:", best.text)
```

To also get an annotated image (BGR numpy array, ready for `cv2.imwrite`):

```python
annotated, result = recognizer.annotate("path/to/car.jpg")
```

### Video / webcam

```python
from barrier_alpr import PlateRecognizer
from barrier_alpr.video import iter_video, run_webcam

recognizer = PlateRecognizer()

for item in iter_video(recognizer, "traffic.mp4", every_n_frames=5):
    for plate in item.result.plates:
        if plate.text:
            print(item.frame_index, plate.text)

# Interactive webcam preview:
run_webcam(recognizer, camera_index=0)
```

## Project layout

```
barrier/
├── examples/
│   ├── recognize_image.py     # Single-image example
│   ├── recognize_video.py     # Video-file example
│   └── recognize_webcam.py    # Live webcam example
├── src/
│   └── barrier_alpr/
│       ├── __init__.py
│       ├── __main__.py        # Enables `python -m barrier_alpr`
│       ├── cli.py             # `barrier-alpr` console entry point
│       ├── recognizer.py      # PlateRecognizer + result dataclasses
│       └── video.py           # Video / webcam helpers
├── pyproject.toml
├── requirements.txt
└── README.md
```

## Troubleshooting

- **Model download fails / is slow.** The first run downloads ~30 MB of ONNX
  models. Rerun the command; downloads are cached under `~/.cache` (or the
  platform equivalent).
- **Webcam window does not open.** On headless systems `cv2.imshow` cannot
  render; use the `video` / `image` subcommands with `--save` instead.
- **CUDA not being used.** Install the GPU extra: `pip install fast-alpr[onnx-gpu]`,
  and start the recognizer with `--ocr-device cuda`.

## License

MIT
