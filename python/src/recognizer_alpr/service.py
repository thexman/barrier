"""HTTP sidecar exposing :class:`PlateRecognizer` to the Java orchestrator.

Run on the Pi alongside the Java fat JAR::

    pip install -e ./python[sidecar]
    barrier-recognizer-service --host 127.0.0.1 --port 8765

Endpoints
---------
POST /recognize/path
    JSON body ``{"path": "/abs/path/to/frame.jpg"}`` — for frames already on
    disk (what the Java camera module writes).

POST /recognize/upload
    ``multipart/form-data`` with a ``file`` field — for remote clients.

Both return :meth:`RecognitionResult.as_dict` JSON.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from recognizer_alpr.recognizer import PlateRecognizer, RecognitionResult

try:
    from fastapi import FastAPI, File, HTTPException, UploadFile
    from pydantic import BaseModel, Field
except ImportError as e:  # pragma: no cover - optional extra
    raise ImportError(
        "FastAPI is required for the recognizer sidecar. Install with: "
        "pip install -e ./python[sidecar]"
    ) from e

app = FastAPI(title="barrier-recognizer-sidecar", version="0.1.0")

_recognizer: PlateRecognizer | None = None


def _get_recognizer() -> PlateRecognizer:
    global _recognizer
    if _recognizer is None:
        _recognizer = PlateRecognizer()
    return _recognizer


class PathRequest(BaseModel):
    path: str = Field(..., description="Absolute or relative path to an image on disk.")


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/recognize/path")
def recognize_path(body: PathRequest) -> dict:
    path = Path(body.path)
    if not path.is_file():
        raise HTTPException(status_code=404, detail=f"image not found: {path}")
    try:
        result = _get_recognizer().recognize(path)
    except (OSError, ValueError) as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    return result.as_dict()


@app.post("/recognize/upload")
async def recognize_upload(file: UploadFile = File(...)) -> dict:
    suffix = Path(file.filename or "upload.jpg").suffix or ".jpg"
    import tempfile

    tmp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = Path(tmp.name)
        result = _get_recognizer().recognize(tmp_path)
    except (OSError, ValueError) as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    finally:
        if tmp_path is not None:
            try:
                tmp_path.unlink(missing_ok=True)
            except OSError:
                pass
    result = _with_upload_source(result, file.filename)
    return result.as_dict()


def _with_upload_source(result: RecognitionResult, filename: str | None) -> RecognitionResult:
    source = filename or result.source
    return RecognitionResult(source=source, plates=result.plates, annotated_path=result.annotated_path)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="barrier-recognizer-service",
        description="Run the fast-alpr HTTP sidecar for the Java orchestrator.",
    )
    parser.add_argument("--host", default="127.0.0.1", help="Bind address (default: 127.0.0.1).")
    parser.add_argument("--port", type=int, default=8765, help="Bind port (default: 8765).")
    parser.add_argument(
        "--reload",
        action="store_true",
        help="Enable auto-reload (development only).",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    import uvicorn

    uvicorn.run(
        "recognizer_alpr.service:app",
        host=args.host,
        port=args.port,
        reload=args.reload,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
