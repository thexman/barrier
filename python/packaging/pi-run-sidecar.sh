#!/usr/bin/env bash
# Run barrier-recognizer-service (models expected under ~/.cache from install.sh).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="${VENV:-$ROOT/venv}"

if [[ ! -x "$VENV/bin/barrier-recognizer-service" ]]; then
  echo "error: venv not found at $VENV — run $ROOT/install.sh first" >&2
  exit 1
fi

for name in open-image-models fast-plate-ocr; do
  if [[ ! -d "${HOME}/.cache/${name}" ]]; then
    echo "error: missing ~/.cache/${name} — run $ROOT/install.sh first" >&2
    exit 1
  fi
done

exec "$VENV/bin/barrier-recognizer-service" "$@"
