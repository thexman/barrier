#!/usr/bin/env bash
# Install the bundled barrier Python ALPR stack into a local venv (offline).
# Run on Raspberry Pi OS 64-bit (aarch64) after unpacking the artifact.
# The wheelhouse must match this machine's Python minor version (see BUILD.txt).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON="${PYTHON:-python3}"
VENV="${VENV:-$ROOT/venv}"

if [[ "$(uname -m)" != "aarch64" && "$(uname -m)" != "arm64" ]]; then
  echo "warning: this bundle targets aarch64 (Raspberry Pi 64-bit); uname -m=$(uname -m)" >&2
fi

if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "error: $PYTHON not found. On Raspberry Pi OS: sudo apt install -y python3 python3-venv" >&2
  exit 1
fi

PY_VER="$("$PYTHON" -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")')"

SUPPORTED="3.11 3.13"
if [[ -f "$ROOT/BUILD.txt" ]]; then
  line="$(grep -E '^python_versions=' "$ROOT/BUILD.txt" || true)"
  if [[ -n "$line" ]]; then
    SUPPORTED="${line#python_versions=}"
    SUPPORTED="${SUPPORTED//$'\r'/}"
  fi
fi

ok=0
for v in $SUPPORTED; do
  if [[ "$PY_VER" == "$v" ]]; then
    ok=1
    break
  fi
done
if [[ "$ok" -ne 1 ]]; then
  echo "error: this bundle has wheels for Python: ${SUPPORTED}" >&2
  echo "error: ${PYTHON} is Python ${PY_VER} (ABI tags will not match; e.g. numpy)." >&2
  echo "error: use a matching python3.X, e.g. PYTHON=python3.11 ./install.sh" >&2
  exit 1
fi

if [[ ! -d "$ROOT/wheelhouse" ]]; then
  echo "error: missing wheelhouse at $ROOT/wheelhouse" >&2
  exit 1
fi

echo "Creating venv at $VENV (Python ${PY_VER})"
"$PYTHON" -m venv "$VENV"
# shellcheck disable=SC1091
source "$VENV/bin/activate"

# Stay offline: upgrade pip only from the bundled wheelhouse when present.
if compgen -G "$ROOT/wheelhouse/pip-"*.whl >/dev/null; then
  python -m pip install --no-index --find-links="$ROOT/wheelhouse" --upgrade pip
fi
python -m pip install --no-index --find-links="$ROOT/wheelhouse" "barrier[sidecar]"

# fast-alpr downloads models into fixed ~/.cache paths (no HF_HOME override).
echo "Installing ONNX models into ~/.cache …"
mkdir -p "${HOME}/.cache"
for name in open-image-models fast-plate-ocr; do
  src="$ROOT/models/${name}"
  dst="${HOME}/.cache/${name}"
  if [[ ! -d "$src" ]]; then
    echo "error: missing bundled model dir: $src" >&2
    exit 1
  fi
  mkdir -p "$dst"
  cp -a "${src}/." "$dst/"
  echo "  -> $dst"
done

echo
echo "Installed."
echo "Start the sidecar with:"
echo "  $ROOT/run-sidecar.sh"
echo "Or:"
echo "  $VENV/bin/barrier-recognizer-service --host 127.0.0.1 --port 8765"
