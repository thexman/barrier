# GitHub Actions

CI for the barrier parking-gate project lives in [`workflows/build.yml`](workflows/build.yml).

## Workflow: `Build`

**Triggers**

- Push to `main` or `release`
- Pull requests targeting `main` or `release`
- Tag pushes matching `v*` (e.g. `v0.1.0`)
- Manual `workflow_dispatch`

**Jobs**

| Job | Runner | Output |
| --- | ------ | ------ |
| `java` | `ubuntu-latest` | Fat JAR built with JDK 17 (`mvn clean verify`) |
| `python` | `ubuntu-24.04-arm` | Offline ALPR bundle for Raspberry Pi (aarch64) |
| `release` | `ubuntu-latest` | Always publishes a GitHub Release (skipped on PRs only) |

### Releases

Every successful non-PR build publishes a GitHub Release:

| Build type | Release name / tag | Behavior |
| ---------- | ------------------ | -------- |
| Untagged (`main`, `release`, `workflow_dispatch`) | `snapshot` | Assets replaced in place; tag moved only when the commit does **not** change `.github/workflows/` (GITHUB_TOKEN cannot retarget tags onto workflow-editing commits — that is why push can 403 while a later manual re-run succeeds) |
| Tagged (`v*`) | the tag name (e.g. `v0.1.0`) | Release created/updated as the latest release |

**No PAT required.** `GITHUB_TOKEN` can update tags and upload assets, but GitHub blocks *creating* a release when the target commit changes `.github/workflows/`. Bootstrap once in the UI if CI says the release is missing:

1. Open [Create release](https://github.com/thexman/barrier/releases/new?tag=snapshot&prerelease=1) (tag `snapshot`, mark as pre-release)
2. Publish (notes/assets can be empty — CI will replace assets)
3. Re-run the failed **Build** workflow (or push again)

After that, every build only moves the `snapshot` tag and replaces files.

**Tag name must start with `v`** — the push filter is `v*` (`v0.1.0` works; `0.1.0` does not).

**From the CLI:**

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Artifacts (every successful run)

Download from **Actions → Build → \<run\> → Artifacts**:

| Artifact name | Contents |
| ------------- | -------- |
| `barrier-jar` | `barrier.jar`, versioned copy, `BUILD.txt` |
| `barrier-python-pi-aarch64` | `barrier-python-pi-aarch64.tar.gz`, `*-BUILD.txt`, `*-README.txt` |

Retention: 30 days.

## GitHub Release assets

Each release (version tag or `snapshot`) includes:

- `barrier.jar`
- `barrier-jar-BUILD.txt`
- `barrier-python-pi-aarch64.tar.gz`
- `barrier-python-pi-aarch64-BUILD.txt`
- `barrier-python-pi-aarch64-README.txt`

## Download on a Raspberry Pi

Set the release tag once (`v0.1.0` or `snapshot`), then `curl` the assets:

```bash
VERSION=v0.1.0
# or: VERSION=snapshot

# Java fat JAR (JDK 17 bytecode — works on Raspberry Pi OS 64-bit)
curl -fsSL -o barrier.jar \
  "https://github.com/thexman/barrier/releases/download/${VERSION}/barrier.jar"

# Python ALPR offline bundle (wheels + ONNX models for aarch64)
curl -fsSL -o barrier-python-pi-aarch64.tar.gz \
  "https://github.com/thexman/barrier/releases/download/${VERSION}/barrier-python-pi-aarch64.tar.gz"
```

### Quick start after download

```bash
# Java
sudo apt install -y default-jdk-headless
java -jar barrier.jar hello

# Python sidecar
sudo apt install -y python3 python3-venv
tar xzf barrier-python-pi-aarch64.tar.gz
cd barrier-python-pi-aarch64
./install.sh
./run-sidecar.sh --host 127.0.0.1 --port 8765 &

# End-to-end (JAR + sidecar)
java -jar barrier.jar orchestrator --allowlist ./plates.txt
```

## What the Python bundle contains

- `wheelhouse/` — `barrier[sidecar]` and all dependencies (onnxruntime, opencv, fastapi, …) for **linux aarch64**, Python **3.11** and **3.13**
- `models/` — default ONNX detector (`open-image-models`) and OCR (`fast-plate-ocr`)
- `install.sh` — creates a local venv, offline `pip install`, copies models into `~/.cache/`
- `run-sidecar.sh` — starts `barrier-recognizer-service`

More detail: [`../java/README.md`](../java/README.md), [`../python/README.md`](../python/README.md), top-level [`../README.md`](../README.md).
