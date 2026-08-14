# barrier (Java)

Java implementation of the barrier parking-gate: proximity sensing, servo gate
control, CSI camera captures, and the end-to-end orchestrator. ALPR runs in a
separate Python HTTP sidecar (`barrier-recognizer-service`); see
[`../python/README.md`](../python/README.md) for sidecar setup.

## Requirements

- JDK **17+** (`sudo apt install default-jdk-headless` on Raspberry Pi OS).
- Apache **Maven 3.8+**.
- Target hardware: **Raspberry Pi 4 Model B**, Raspberry Pi OS **64-bit**.
  On the Pi you also want:
  - `pigpio` / `pigpiod` — not always in apt; build from source (see top-level
    [`README.md`](../README.md#system-packages))
  - `rpicam-apps` — `sudo apt install rpicam-apps`
  - Python sidecar — see [`../python/README.md`](../python/README.md)

## Build

```bash
cd java/barrier
mvn -q clean package
```

If Maven Central fetches fail with `PKIX path building failed` (common on
corporate Windows machines that inject a root CA into the Windows cert
store), point Maven's JVM at the Windows trust store instead of the JDK's
`cacerts`:

```powershell
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
mvn -q clean package
```

On the Pi you shouldn't need this — Raspberry Pi OS OpenJDK has a fresh
`cacerts` and no MITM.

The build produces a fat JAR at `java/barrier/target/barrier.jar` (~4.7 MB). Copy it to
the Pi and run:

```bash
java -jar barrier.jar hello
java -jar barrier.jar proximity --mock --count 5
java -jar barrier.jar servo --mock open
java -jar barrier.jar camera ./photos --count 3 --mock
java -jar barrier.jar --log-json orchestrator --mock --mock-plate TEST --allow-all --max-cycles 1
```

## CI artifacts (download for the Pi)

GitHub Actions workflow [`.github/workflows/build.yml`](../.github/workflows/build.yml)
builds and tests the fat JAR on every push / PR, then uploads a **`barrier-jar`**
artifact (`barrier.jar` + `BUILD.txt`).

**From the Actions UI:** open the workflow run → *Artifacts* → download
`barrier-jar` → copy `barrier.jar` to the Pi.

**From a tagged release** (push a `v*` tag, e.g. `v0.1.0`):

```bash
# on the Pi — download from a tagged release
VERSION=v0.1.0
curl -fsSL -o barrier.jar \
  "https://github.com/thexman/barrier/releases/download/${VERSION}/barrier.jar"
sudo apt install -y default-jdk-headless
java -jar barrier.jar hello
```

The JAR is Java 17 bytecode — no native compile step is required on the Pi.
GPIO / camera still need the packages listed under [Pi deployment](#pi-deployment).

## Pi deployment

```bash
sudo apt install -y default-jdk-headless rpicam-apps
# pigpio: build from source — see ../README.md#system-packages
sudo systemctl enable --now pigpiod
# then install/start the Python sidecar (see ../python/README.md)
java -DPIGPIOD_HOST=127.0.0.1 -jar barrier.jar orchestrator --allowlist plates.txt
```

Prefer the offline CI Python bundle on the Pi when you want wheels + models
pre-packaged — see [`../python/README.md`](../python/README.md#offline-pi-bundle-ci-artifact).

Individual module smoke tests:

```bash
java -DPIGPIOD_HOST=127.0.0.1 -jar barrier.jar proximity --interval 0.2
java -DPIGPIOD_HOST=127.0.0.1 -jar barrier.jar servo open
java -DPIGPIOD_HOST=127.0.0.1 -jar barrier.jar camera ./photos --count 3
```

## Layout

```
java/
├── README.md
└── barrier/                         # Maven module
    ├── pom.xml
    ├── src/main/java/com/a9ski/barrier/
    │   ├── BarrierCli.java              # picocli root command
    │   ├── commands/
    │   │   ├── HelloCommand.java        # `barrier hello`
    │   │   ├── VersionCommand.java      # `barrier version` (git SHA from JAR)
    │   │   ├── ProximityCommand.java    # `barrier proximity`
    │   │   ├── ServoCommand.java        # `barrier servo`
    │   │   ├── CameraCommand.java       # `barrier camera`
    │   │   └── OrchestratorCommand.java # `barrier orchestrator`
    │   ├── proximity/                   # HC-SR04 (diozero) + mock
    │   ├── servo/                       # PwmServo gate + mock
    │   ├── camera/                      # rpicam-still shell-out + Java2D mock
    │   ├── recognizer/                  # HTTP client for Python ALPR sidecar
    │   └── orchestrator/                # state machine, allowlist, events
    ├── src/main/resources/logback.xml
    └── src/test/java/…
```

## Subcommands

| Subcommand     | Purpose |
| -------------- | ------- |
| `hello`        | JVM / OS diagnostics after copying the JAR to a new machine |
| `version`      | Git commit SHA embedded in the JAR (`build-info.properties`) |
| `proximity`    | Continuous HC-SR04 distance readings (`--mock` for laptops) |
| `servo`        | Open/close/angle/cycle gate control (`open`, `close`, `angle`, `cycle`) |
| `camera`       | Capture still frames to a directory (`--mock` for Java2D test images) |
| `orchestrator` | Full loop: proximity → camera → ALPR sidecar → allowlist → servo |

Root flags: `--log-json` (newline-delimited JSON events), `-v` / `--verbose`,
`-h` / `--help`.

### Orchestrator highlights

- `--allowlist FILE` or `--allow-all` (required unless testing)
- `--recognizer-url` — sidecar base URL (default `http://127.0.0.1:8765`)
- `--mock-plate TEXT` — skip sidecar; always return TEXT from recognizer
- `--mock` — enable all hardware mocks
- `--dry-run` — log state machine without moving the servo
- Allowlist hot-reload on file mtime change (disable with `--no-reload-allowlist`)

See `java -jar barrier.jar orchestrator --help` for the full flag list.

## Notes

- Bytecode is compiled to Java 17 (`<maven.compiler.release>17</maven.compiler.release>`
  in the POM). It's fine to develop with a newer JDK locally; the produced
  JAR still runs on 17.
- The picocli annotation processor is enabled at compile time so `--help`
  and reflection-free subcommand discovery work when we eventually build a
  GraalVM native image.
- diozero uses the **pigpio** provider when `pigpiod` is available (bundle
  includes `diozero-provider-pigpio`). Ensure `pigpiod` is running on the Pi
  (`sudo systemctl enable --now pigpiod`). Without it, diozero falls back to
  the builtin gpiochip backend.
