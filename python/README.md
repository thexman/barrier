# barrier (Python)

ALPR package and HTTP sidecar for the Java parking-gate orchestrator. Built on
[`fast-alpr`](https://github.com/ankandrew/fast-alpr) (ONNX). The Java fat JAR
calls this service over HTTP; see [`../README.md`](../README.md) and
[`../java/README.md`](../java/README.md) for the gate loop and Pi deployment.

## Requirements

- Python **3.10 – 3.13**
- Windows, Linux, macOS, or Raspberry Pi OS

## Installation

From the **repo root**, create a virtual environment and install the package:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install --upgrade pip
pip install -e .\python
```

```bash
# Linux / macOS / Raspberry Pi
python -m venv .venv
source .venv/bin/activate
pip install --upgrade pip
pip install -e ./python
```

Or install only the runtime dependencies without the package itself:

```bash
pip install -r python/requirements.txt
```

For the HTTP sidecar used by the Java orchestrator, install the `[sidecar]`
extra:

```bash
pip install -e ./python[sidecar]
```

You can also install from this directory:

```bash
cd python
pip install -e .[sidecar]
```

The first ALPR run downloads the detection and OCR ONNX models from the model
hub and caches them on disk (~30 MB).

### Offline Pi bundle (CI artifact)

GitHub Actions workflow [`.github/workflows/build.yml`](../.github/workflows/build.yml)
builds an **aarch64** tarball with:

- a `wheelhouse/` of `barrier[sidecar]` and all dependencies (onnxruntime,
  opencv, fastapi, …) for **Python 3.11 and 3.13** (Pi OS Bookworm / Trixie)
- a `models/` directory with the default ONNX detector
  (`~/.cache/open-image-models`) and OCR (`~/.cache/fast-plate-ocr`) files
- `install.sh` / `run-sidecar.sh` for offline install and start on the Pi

**Download:** Actions → *Build* run → artifact `barrier-python-pi-aarch64`,
or from a GitHub Release (`snapshot` or a `v*` tag):

```bash
# on Raspberry Pi OS 64-bit (python3 must be 3.11 or 3.13)
VERSION=snapshot
curl -fsSL -o barrier-python-pi-aarch64.tar.gz \
  "https://github.com/thexman/barrier/releases/download/${VERSION}/barrier-python-pi-aarch64.tar.gz"
sudo apt install -y python3 python3-venv
tar xzf barrier-python-pi-aarch64.tar.gz
cd barrier-python-pi-aarch64
./install.sh
./run-sidecar.sh --host 127.0.0.1 --port 8765
```

`install.sh` installs wheels offline into a local venv and copies the ONNX
models into `~/.cache/…` (where fast-alpr looks for them). No PyPI or model
download is required afterward. If your `python3` minor version is not in
`BUILD.txt` (`python_versions=`), install fails with a clear error — use
matching interpreter, e.g. `PYTHON=python3.11 ./install.sh`.

### Alternative ONNX backends

The default install uses the CPU ONNX runtime. `fast-alpr` supports several
backends — pick the one that matches your hardware:

| Hardware           | Install command                        |
| ------------------ | -------------------------------------- |
| CPU (default)      | `pip install fast-alpr[onnx]`          |
| NVIDIA GPU (CUDA)  | `pip install fast-alpr[onnx-gpu]`      |
| Intel (OpenVINO)   | `pip install fast-alpr[onnx-openvino]` |
| Windows (DirectML) | `pip install fast-alpr[onnx-directml]` |
| Qualcomm (QNN)     | `pip install fast-alpr[onnx-qnn]`      |

## Sidecar (for the Java orchestrator)

```bash
barrier-recognizer-service --host 127.0.0.1 --port 8765
```

Endpoints: `GET /health`, `POST /recognize/path`, `POST /recognize/upload`.
Default URL expected by the Java orchestrator: `http://127.0.0.1:8765`.

## CLI quick start

```bash
barrier-recognizer path/to/car.jpg --json result.json --save annotated.jpg
python -m recognizer_alpr path/to/car.jpg --json result.json
```

Full CLI / API documentation: top-level [`README.md`](../README.md).

## Example

```bash
# after pip install -e ./python
python python/examples/recognize_image.py path/to/car.jpg
```
