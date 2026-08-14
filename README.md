# barrier

> **Architecture: Java fat JAR + Python ALPR sidecar.** Proximity, servo, camera
> and orchestration run in a Java fat JAR (Maven, JDK 17, Raspberry Pi 4 Model B,
> Raspberry Pi OS 64-bit).
> License-plate recognition runs in Python as a small HTTP sidecar
> (`barrier-recognizer-service`) built on ONNX / fast-alpr.
> See [`java/README.md`](java/README.md) for the Java build and Pi deployment.
> See [`python/README.md`](python/README.md) for the Python ALPR package.
> See [`.github/README.md`](.github/README.md) for CI artifacts and release downloads.

Utilities that power the **barrier** parking-gate project:

- **`recognizer_alpr`** *(Python)* — automatic license-plate recognition built on
  [`fast-alpr`](https://github.com/ankandrew/fast-alpr). Exposed as the
  `barrier-recognizer` CLI and the `barrier-recognizer-service` HTTP sidecar.
- **proximity, servo, camera, orchestrator** *(Java)* — HC-SR04 sensing,
  hobby-servo gate control, CSI camera captures, and the end-to-end state
  machine. Exposed as subcommands of `java -jar barrier.jar …`; each ships
  with mock implementations for hardware-free testing. See
  [`java/README.md`](java/README.md).

## What each module does

### `recognizer_alpr` *(Python)*

- recognize plates in a single image or a whole folder of images,
- save annotated images with bounding boxes and OCR overlays,
- export structured results as JSON.

### `orchestrator` *(Java)*

- watch the proximity sensor and fire a cycle when a vehicle is detected,
- capture N still frames, call the Python ALPR sidecar and keep the
  highest-confidence plate above a configurable threshold,
- match the plate against a plain-text allowlist (case- and punctuation-
  insensitive) and, if allowed, open the servo-driven gate for a configurable
  hold time before closing it again,
- stream every state transition as a pretty log line or newline-delimited
  JSON, and shut the gate cleanly on Ctrl-C / SIGTERM.

## Requirements

### Software
- **Java 17+** — for the fat JAR (proximity, servo, camera, orchestrator); the
  JAR is compiled for bytecode level 17
- **Python 3.10 – 3.13** — for the ALPR sidecar (see [`python/README.md`](python/README.md))
- Windows, macOS or Linux (Raspberry Pi OS **64-bit** on the Pi)

### Hardware
- Raspbery PI that supports pigpio (tested on model 4B).
- Compatible video camera (tested with [MakerHawk Raspberry Pi Camera IR Fisheye Wide-angle](https://www.amazon.co.uk/dp/B07DRH5Y5S?ref_=ppx_hzsearch_conn_dt_b_fed_asin_title_3))
- Proximity sensor (tested with HC-SR04)
- Servo motor (tested with MG995)

## Installation

Install the Raspbery PI OS **64 bits** using instructions from
https://www.raspberrypi.com/software/operating-systems/

### System packages

Install the runtime libraries first. You typically need a JDK **17+**, Python 3,
GPIO (`pigpio` / `pigpiod`), and the CSI camera tools.

```bash
sudo apt update
sudo apt upgrade
sudo reboot
```

**Packages available from apt** on current Raspberry Pi OS:

```bash
sudo apt install -y \
  default-jdk-headless \
  python3 python3-venv python3-pip \
  python3-setuptools python3-full \
  rpicam-apps \
  curl \
  build-essential

java -version   # must report 17 or newer
```

`default-jdk-headless` installs whatever OpenJDK your image ships (often 21+).
That is fine — the fat JAR only requires a **17+** runtime. If your image still
has OpenJDK 17 packaged, `openjdk-17-jdk-headless` works too.

**`pigpio` is no longer in apt** on current Raspberry Pi OS images. Build and
install it from source (needed for reliable HC-SR04 + servo PWM via diozero on
Pi 4):

```bash
cd /tmp
wget https://github.com/joan2937/pigpio/archive/refs/tags/v79.tar.gz
tar zxf v79.tar.gz
cd pigpio-79
make
sudo make install
sudo ldconfig
sudo systemctl daemon-reload
sudo systemctl enable --now pigpiod
```

If `systemctl enable pigpiod` fails because the unit file is missing, create
`/etc/systemd/system/pigpiod.service`:

```ini
[Unit]
Description=Daemon required to control GPIO pins via pigpio

[Service]
Type=forking
ExecStart=/usr/local/bin/pigpiod -t 0 -l
Restart=always
ExecStop=/bin/systemctl kill pigpiod

[Install]
WantedBy=multi-user.target
```

Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pigpiod
```

`pigpiod` is required for real HC-SR04 / servo GPIO: the fat JAR includes
diozero’s pigpio provider. If the daemon is not running you may see builtin
gpiochip errors such as `Duplicate key 0` on newer Raspberry Pi OS images.

Optional: allow your user to access GPIO without sudo:

```bash
sudo usermod -aG gpio "$USER"
# log out and back in for the group change to apply
```

Plug the camera ribbon into the Pi’s CSI-2 **CAM** port (not the DSI display
port), contacts facing the correct way, then power on. Current Raspberry Pi OS
detects supported cameras automatically — there is **no** “Camera” toggle under
`raspi-config` → *Interface Options*.

Verify detection:

```bash
rpicam-hello --list-cameras
# optional smoke capture (no preview window needed if headless):
rpicam-still -o /tmp/test.jpg --nopreview
```

If no cameras are listed, reseat the ribbon, confirm `camera_auto_detect=1` in
`/boot/firmware/config.txt`, then reboot.

| Component | Used for |
| --------- | -------- |
| `default-jdk-headless` (Java 17+) | Run `barrier.jar` |
| `python3` / `python3-venv` / `python3-pip` | ALPR sidecar |
| `pigpio` / `pigpiod` *(built from source)* | HC-SR04 and hobby-servo GPIO via diozero |
| `rpicam-apps` | Still captures (`rpicam-still`) |
| `curl` | Download release artifacts |

**Development laptop** (Windows / macOS / Linux) — no GPIO or CSI camera
required if you use `--mock` flags:

| Need | How to install |
| ---- | -------------- |
| JDK **17+** | [Adoptium Temurin](https://adoptium.net/) or your OS package manager |
| Python **3.10 – 3.13** | [python.org](https://www.python.org/downloads/) or your OS package manager |
| Apache **Maven 3.8+** *(only to build the JAR from source)* | [maven.apache.org](https://maven.apache.org/download.cgi) or your OS package manager |

### Java fat JAR

```bash
# From a tagged release (easier on the Pi):
cd ~/
VERSION=v0.1.0
curl -fsSL -o barrier.jar "https://github.com/thexman/barrier/releases/download/${VERSION}/barrier.jar"

# Or from a workflow run: Actions → Build → Artifacts → barrier-jar
java -jar barrier.jar hello
```

### Python ALPR sidecar

For an **offline Raspberry Pi bundle** (wheels + ONNX models, no PyPI/hub at
runtime), download the CI/release artifact — see
[`python/README.md`](python/README.md#offline-pi-bundle-ci-artifact):

```bash
VERSION=v0.1.0
curl -fsSL -o barrier-python-pi-aarch64.tar.gz \
  "https://github.com/thexman/barrier/releases/download/${VERSION}/barrier-python-pi-aarch64.tar.gz"
tar xzf barrier-python-pi-aarch64.tar.gz
cd barrier-python-pi-aarch64
./install.sh
./run-sidecar.sh --host 127.0.0.1 --port 8765 &
cd ..
java -DPIGPIOD_HOST=127.0.0.1 -jar barrier.jar orchestrator --allowlist ./plates.txt
```

For a normal editable install (downloads models on first run), see
[`python/README.md`](python/README.md).

## License plate recognition (`barrier-recognizer` CLI)

Install the Python package first — see [`python/README.md`](python/README.md).
The package installs a `barrier-recognizer` console script. It takes a single
image file or a directory of images as its positional argument. `--json` is
**required** and specifies where to write the structured results. Optional
flags cover annotated output and model selection (`--save`,
`--detector-model`, `--ocr-model`, `--detector-confidence`, `--ocr-device`).

When a directory is given, only image files directly inside it are processed
(sub-directories are ignored).

### Recognize a single image

```powershell
barrier-recognizer path\to\car.jpg --json result.json --save annotated.jpg
```

### Recognize every image in a folder

```powershell
barrier-recognizer .\photos --json results.json --save .\outputs
```

If `--save` is a directory, each annotated image is written next to the source
name (`<name>_annotated.<ext>`). If it is a file path, a single image is
written there.

You can also invoke the CLI as a module:

```powershell
python -m recognizer_alpr path\to\car.jpg --json result.json
```

## Python API

```python
from recognizer_alpr import PlateRecognizer

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

## Proximity sensing (Java `barrier proximity` subcommand)

HC-SR04 ultrasonic distance sensing via the Java fat JAR — see
[`java/README.md`](java/README.md) for the build. Example usage:

```bash
java -jar barrier.jar proximity                      # defaults: BCM 23/24
java -jar barrier.jar --log-json proximity --only-changes --threshold 80
java -jar barrier.jar proximity --mock --interval 0.1 --count 30
```

### Wiring (HC-SR04 → Raspberry Pi, defaults)

| HC-SR04 pin | Pi connection                                                                 |
| ----------- | ----------------------------------------------------------------------------- |
| `VCC`       | 5 V                                                                           |
| `GND`       | GND                                                                           |
| `TRIG`      | BCM **23** (physical pin 16)                                                  |
| `ECHO`      | BCM **24** (physical pin 18) via a resistor divider (5 V → 3.3 V; e.g. 1kΩ + 2kΩ) |

Do **not** feed the raw 5 V `ECHO` line straight into the Pi — its GPIOs are
only 3.3 V tolerant. Override the pins with `--trigger` / `--echo` if you
wire differently.

### Flags

Root flags (apply to every subcommand): `--log-json`, `-v` / `--verbose`,
`-h` / `--help`.

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--trigger PIN` | `23` | BCM pin wired to the sensor TRIG line |
| `--echo PIN` | `24` | BCM pin wired to the sensor ECHO line |
| `--threshold CM` | `100` | Distance at or below which a reading is NEAR |
| `--max-distance CM` | `400` | Sensor range ceiling in cm; readings clamp here |
| `--interval SEC` | `0.2` | Poll interval in seconds |
| `--count N` | `0` | Stop after N emitted readings; `0` = run forever |
| `--only-changes` | off | After the first reading, only emit when near/far flips |
| `--mock` | off | Use the software mock sensor; no GPIO required |

Pretty output is one line per reading (`NEAR` / `far` + distance). With
`--log-json`, each reading is a JSON object on stdout.

## Gate servo control (Java `barrier servo` subcommand)

Hobby-servo gate control via the Java fat JAR — see
[`java/README.md`](java/README.md) for the build. Example usage:

```bash
java -jar barrier.jar servo open
java -jar barrier.jar servo open --auto-close-after 10
java -jar barrier.jar servo angle 45
java -jar barrier.jar servo cycle --count 3 --dwell 1
java -jar barrier.jar --log-json servo --mock cycle --count 2 --dwell 0
```

### Wiring (hobby servo → Raspberry Pi, defaults)

| Servo wire        | Pi connection                                                   |
| ----------------- | --------------------------------------------------------------- |
| Signal (yellow)   | BCM **18** (physical pin 12) — hardware-PWM capable             |
| VCC (red)         | External 5 V supply able to source 500 mA+ *(do not use the Pi's 5 V rail for anything bigger than an SG90)* |
| GND (brown/black) | Servo GND **and** Pi GND (common ground!)                       |

Change the pin with `--pin`. If your servo needs the extended 0.5–2.5 ms
pulse-width range, pass `--min-pulse-ms 0.5 --max-pulse-ms 2.5`.

### Flags

Shared options (before the subcommand name, or after `servo` and before the
action):

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--pin PIN` | `18` | BCM pin wired to the servo signal |
| `--open-angle DEG` | `90` | Angle for the OPEN position |
| `--close-angle DEG` | `0` | Angle for the CLOSED position |
| `--min-angle DEG` | `-90` | Physical minimum angle of the servo |
| `--max-angle DEG` | `90` | Physical maximum angle of the servo |
| `--min-pulse-ms MS` | `1` | Pulse width (ms) that maps to `--min-angle` |
| `--max-pulse-ms MS` | `2` | Pulse width (ms) that maps to `--max-angle` |
| `--move-time SEC` | `0.5` | Seconds to wait for the servo to reach the target |
| `--mock` | off | Use a software mock gate; no GPIO required |

Subcommands:

| Subcommand | Flags / args | Description |
| ---------- | ------------ | ----------- |
| `open` | `--auto-close-after SEC` (optional) | Move to OPEN; optionally wait and close again |
| `close` | — | Move to CLOSED |
| `angle DEG` | positional angle in degrees | Move to an arbitrary angle within min/max |
| `cycle` | `--count N` (default `3`), `--dwell SEC` (default `1`) | Open/close repeatedly for self-test |

Root `--log-json` emits each gate event as newline-delimited JSON instead of
pretty text.

## Camera captures (Java `barrier camera` subcommand)

Raspberry Pi CSI still captures via the Java fat JAR — see
[`java/README.md`](java/README.md) for the build. On the Pi it shells out to
`rpicam-still` (current Raspberry Pi OS) or `libcamera-still` (older images).
Install on the Pi:

```bash
sudo apt install rpicam-apps
```

Plug the ribbon into the Pi's CSI-2 `CAM` port. Cameras are auto-detected on
current Raspberry Pi OS (no `raspi-config` Camera option). Check with
`rpicam-hello --list-cameras`.

```bash
java -jar barrier.jar camera ./photos --count 10
java -jar barrier.jar camera ./photos --count 5 --interval 1 --resolution 1920x1080
java -jar barrier.jar camera ./photos --count 3 --rotate 90
java -jar barrier.jar --log-json camera ./photos --count 3 --mock
```

### Flags

| Flag / arg | Default | Description |
| ---------- | ------- | ----------- |
| `OUTPUT_DIR` | *(required)* | Directory for captured images (created if missing) |
| `-n` / `--count N` | *(required)* | How many pictures to take (`>= 0`) |
| `--interval SEC` | `1` | Delay between captures in seconds |
| `--resolution WxH` | `2592x1944` | Capture resolution |
| `--prefix NAME` | `frame` | Filename prefix (`frame_0001.jpg`, …) |
| `--ext EXT` | `jpg` | Image file extension |
| `--warmup SEC` | `1` | Sensor warm-up before the first capture (real camera only) |
| `--rotate DEG` | `180` | Passed to `rpicam-still --rotation` (`0`, `90`, `180`, or `270`) |
| `--mock` | off | Use a software mock camera; no CSI hardware required |

Root `--log-json` emits one JSON object per capture instead of a pretty line.

## Barrier orchestrator (`barrier orchestrator` subcommand)

The orchestrator ties everything together in Java. It runs a single state
machine that:

1. polls the proximity sensor until a vehicle is detected (`--debounce-count`
   consecutive NEAR readings);
2. captures `--capture-count` frames into `--photos-dir/cycle_NNNN/`;
3. POSTs each frame to the ALPR sidecar (`--recognizer-url`, default
   `http://127.0.0.1:8765`) and picks the plate with the highest OCR
   confidence above `--min-ocr-confidence`;
4. matches the plate against the `--allowlist` (case- and punctuation-
   insensitive);
5. commands the servo to open, holds for `--open-seconds`, then closes;
6. waits `--cooldown-seconds` and re-arms.

Every state transition is logged as an `Event` (pretty text or JSON with
`--log-json`).

### Allowlist format

Plain UTF-8 text, one plate per line. Blank lines and `#`-comments are
ignored. Matching is case-insensitive and ignores non-alphanumeric
characters, so `AB 12-34` in the file will match OCR results like
`ab1234`, `AB1234` or `AB-12 34`.

```text
# Household plates
CO4701BC
CA1234AB

# Guest for this week
XY9988ZZ
```

### Hot-reload

The allowlist file's mtime is polled once per cycle — right after a proximity
trigger fires, before authorization — and re-read whenever it changes. That
means edits (add, remove, rename a plate) take effect on **the very next
vehicle**, no restart needed. Reload activity is logged as an `IDLE` event:

```
[10:14:07.812] IDLE         allowlist reloaded (3 -> 4 plates)  (changed=True, ok=True, size_before=3, size_after=4)
```

Failures are non-fatal: if the file is missing, empty, or unreadable at
reload time, the orchestrator keeps the previous plates in memory and logs
the reason (`ok=False`). This prevents a bad save from locking everyone out.
Disable the behaviour with `--no-reload-allowlist` if you want the startup
snapshot to remain frozen for the whole run.

### CLI usage

Run on the Pi with the sidecar and an allowlist:

```bash
barrier-recognizer-service --host 127.0.0.1 --port 8765 &
java -jar barrier.jar orchestrator --allowlist ./plates.txt
```

Quick end-to-end mock cycle on a Windows laptop (no hardware, no sidecar,
opens the gate every trigger):

```powershell
java -jar java/barrier/target/barrier.jar orchestrator --mock --mock-plate CO4701BC --allow-all `
        --open-seconds 1 --cooldown-seconds 1 --max-cycles 2 `
        --debounce-count 2 --capture-count 1
```

Dry run against real hardware — log the full state machine but never move
the servo, useful for tuning proximity / OCR thresholds:

```bash
java -jar barrier.jar orchestrator --allowlist ./plates.txt --dry-run
```

JSON stream for downstream processing:

```bash
java -jar barrier.jar --log-json orchestrator --allowlist ./plates.txt | tee events.jsonl
```

### Flags

#### Behaviour

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--allowlist FILE` | — | Path to a plates allowlist (one plate per line, `#` for comments). **Required** unless `--allow-all` |
| `--allow-all` | off | Bypass the allowlist (testing only). **Required** unless `--allowlist` |
| `--photos-dir DIR` | `./photos` | Root directory for captured frames (`cycle_NNNN/`) |
| `--open-seconds N` | `8` | Hold the gate open after an allowed match |
| `--cooldown-seconds N` | `3` | Idle time after each cycle before re-arming |
| `--debounce-count N` | `3` | Consecutive NEAR readings required to fire a cycle |
| `--capture-count N` | `3` | Frames captured per trigger |
| `--capture-interval SEC` | `0.15` | Delay between captures within a cycle |
| `--min-ocr-confidence F` | `0.6` | Reject OCR results below this confidence |
| `--extend-hold-on-near` | off | Keep the gate open while proximity is still NEAR |
| `--max-cycles N` | `0` | Stop after N cycles; `0` = run forever |
| `--dry-run` | off | Log every step but never command the servo |
| `--reload-allowlist` / `--no-reload-allowlist` | reload on | Reload the allowlist when the file's mtime changes |
| `--recognizer-url URL` | `http://127.0.0.1:8765` | ALPR sidecar base URL |

#### Mocks

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--mock` | off | Enable all hardware mocks (proximity, camera, servo) |
| `--mock-proximity` | off | Use the mock proximity sensor only |
| `--mock-camera` | off | Use the mock camera only |
| `--mock-servo` | off | Use the mock gate only |
| `--mock-plate TEXT` | — | Stub the recognizer to always return TEXT (skips the sidecar) |

#### Proximity passthrough

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--trigger-pin PIN` | `23` | BCM TRIG pin |
| `--echo-pin PIN` | `24` | BCM ECHO pin |
| `--proximity-threshold CM` | `100` | NEAR threshold in cm |
| `--proximity-max CM` | `400` | Max sensor range in cm |
| `--proximity-interval SEC` | `0.2` | Proximity poll interval while idle |

#### Servo passthrough

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--servo-pin PIN` | `18` | BCM servo signal pin |
| `--open-angle DEG` | `90` | Open angle |
| `--close-angle DEG` | `0` | Close angle |
| `--servo-min-angle DEG` | `-90` | Physical min angle |
| `--servo-max-angle DEG` | `90` | Physical max angle |
| `--min-pulse-ms MS` | `1` | Min pulse width |
| `--max-pulse-ms MS` | `2` | Max pulse width |
| `--servo-move-time SEC` | `0.5` | Wait after commanding a move |

#### Camera passthrough

| Flag | Default | Description |
| ---- | ------- | ----------- |
| `--resolution WxH` | `2592x1944` | Capture resolution |
| `--camera-warmup SEC` | `1` | Warm-up before the first capture of a cycle |
| `--rotate DEG` | `180` | Passed to `rpicam-still --rotation` (`0`, `90`, `180`, or `270`) |

Root `--log-json` emits each state-machine `Event` as newline-delimited JSON.

## Project layout

```
barrier/
├── java/                      # Java fat JAR (see java/barrier/)
├── python/                    # ALPR CLI + HTTP sidecar
│   ├── README.md
│   ├── pyproject.toml
│   ├── requirements.txt
│   ├── examples/
│   │   └── recognize_image.py
│   └── src/
│       └── recognizer_alpr/   # `barrier-recognizer` + service
│           ├── __init__.py
│           ├── __main__.py    # Enables `python -m recognizer_alpr`
│           ├── cli.py
│           ├── service.py     # FastAPI sidecar
│           └── recognizer.py
└── README.md
```

## Troubleshooting

- **Model download fails / is slow.** The first run downloads ~30 MB of ONNX
  models. Rerun the command; downloads are cached under `~/.cache` (or the
  platform equivalent).
- **CUDA not being used.** Install the GPU extra: `pip install fast-alpr[onnx-gpu]`,
  and start the recognizer with `--ocr-device cuda`.
- **`Failed to add edge detection` / permission errors on the Pi.** Your user
  needs GPIO access — add it to the `gpio` group, or run under `sudo`, or
  ensure `pigpiod` is running for diozero.
- **Servo jitters or chatters.** Software PWM on the Pi is not perfectly
  timed. Use a hardware-PWM pin (BCM 12/13/18/19), give the servo a stable
  external 5 V supply with a common ground, and ensure `pigpiod` is running
  (build/install pigpio from source — see [System packages](#system-packages)) so
  diozero can use the pigpio backend.
- **`orchestrator` never fires a cycle.** Confirm proximity is producing NEAR
  readings first: `java -jar barrier.jar proximity --interval 0.2`. If the
  sensor intermittently reads far, raise `--debounce-count` or
  `--proximity-threshold`.
- **Allowed plate keeps getting denied.** OCR confusion is common (e.g.
  `0` vs `O`, `1` vs `I`). Add both variants to `--allowlist`, or lower
  `--min-ocr-confidence` after inspecting the JSON log to see what the OCR
  actually returned.
- **Gate closes too soon for slow drivers.** Raise `--open-seconds`, or add
  `--extend-hold-on-near` so the timer resets while the sensor still reports
  the vehicle.

## License

This project is licensed under the **GNU General Public License v3.0**
(GPL-3.0). See [`LICENSE`](LICENSE) for the full text.

### Third-party dependency licenses

Direct runtime dependencies and their declared licenses (as of the versions
pinned / currently resolved in this repo). Test-only and purely build-tool
dependencies are omitted unless noted.

#### Java (`java/barrier/pom.xml`)

| Dependency | License |
| ---------- | ------- |
| [picocli](https://picocli.info) | Apache License 2.0 |
| [diozero-core](https://www.diozero.com) | MIT |
| [Jackson](https://github.com/FasterXML/jackson) (`jackson-databind`, `jackson-datatype-jsr310`) | Apache License 2.0 |
| [SLF4J API](https://www.slf4j.org) | MIT |
| [Logback Classic](https://logback.qos.ch) | Eclipse Public License 1.0 **or** GNU LGPL 2.1 (dual-licensed) |
| [JUnit Jupiter](https://junit.org/junit5/) *(test)* | Eclipse Public License 2.0 |

Notable transitive runtime libraries pulled in by the above:

| Dependency | Via | License |
| ---------- | --- | ------- |
| tinylog (`tinylog-api` / `tinylog-impl`) | diozero-core | Apache License 2.0 |
| jackson-core / jackson-annotations | jackson-databind | Apache License 2.0 |
| logback-core | logback-classic | EPL-1.0 **or** LGPL-2.1 |

#### Python (`python/pyproject.toml`)

| Dependency | License |
| ---------- | ------- |
| [fast-alpr](https://github.com/ankandrew/fast-alpr) | MIT |
| [opencv-python](https://github.com/opencv/opencv-python) | Apache License 2.0 |
| [NumPy](https://numpy.org) | BSD-3-Clause |
| [FastAPI](https://fastapi.tiangolo.com) *(optional `[sidecar]`)* | MIT |
| [Uvicorn](https://www.uvicorn.org) *(optional `[sidecar]`)* | BSD-3-Clause |

Notable transitive runtime libraries:

| Dependency | Via | License |
| ---------- | --- | ------- |
| [ONNX Runtime](https://onnxruntime.ai) | `fast-alpr[onnx]` | MIT |
| Starlette | FastAPI | BSD-3-Clause |
| Pydantic | FastAPI | MIT |

Upstream packages may themselves bundle further native libraries (for example
OpenCV / FFmpeg codecs inside `opencv-python` wheels, or NumPy’s vendored
math routines). Consult each project’s NOTICE / LICENSE files for the full
transitive picture before redistributing binaries.
