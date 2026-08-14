package com.a9ski.barrier.orchestrator;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.locks.LockSupport;

import com.a9ski.barrier.camera.Camera;
import com.a9ski.barrier.camera.CaptureResult;
import com.a9ski.barrier.proximity.ProximitySensor;
import com.a9ski.barrier.proximity.Reading;
import com.a9ski.barrier.recognizer.PlateDetection;
import com.a9ski.barrier.recognizer.RecognitionResult;
import com.a9ski.barrier.servo.Gate;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * Ties proximity, camera, ALPR and servo into one cycle-per-vehicle loop.
 */
public final class Orchestrator {

    private final OrchestratorConfig config;
    private final ProximitySensor proximity;
    private final Camera camera;
    private final Gate gate;
    private final PlateRecognizer recognizer;
    private final Allowlist allowlist;

    private volatile boolean stopRequested;
    private int cyclesCompleted;

    public Orchestrator(
            OrchestratorConfig config,
            ProximitySensor proximity,
            Camera camera,
            Gate gate,
            PlateRecognizer recognizer,
            Allowlist allowlist) {
        this.config = config;
        this.proximity = proximity;
        this.camera = camera;
        this.gate = gate;
        this.recognizer = recognizer;
        this.allowlist = allowlist;
    }

    public void requestStop() {
        stopRequested = true;
    }

    public void run(Consumer<Event> sink) {
        sink.accept(emit(State.IDLE, "orchestrator started", Map.of(
                "allowlist_size", allowlist.size(),
                "allow_all", allowlist.isAllowAll(),
                "dry_run", config.dryRun,
                "photos_dir", config.photosDir.toString())));

        while (!stopRequested) {
            if (config.maxCycles > 0 && cyclesCompleted >= config.maxCycles) {
                break;
            }
            oneCycle(sink);
        }
        shutdown(sink);
    }

    private void oneCycle(Consumer<Event> sink) {
        Double triggerDistance = awaitTrigger();
        if (triggerDistance == null) {
            return;
        }

        if (config.reloadAllowlist) {
            Event reloadEvent = maybeReloadAllowlist();
            if (reloadEvent != null) {
                sink.accept(reloadEvent);
            }
        }

        int cycleId = cyclesCompleted + 1;
        sink.accept(emit(State.CAPTURING, "proximity triggered", Map.of(
                "cycle_id", cycleId,
                "distance_cm", round1(triggerDistance))));

        List<Path> frames;
        try {
            frames = captureFrames(cycleId);
        } catch (RuntimeException e) {
            sink.accept(emit(State.COOLDOWN, "capture failed: " + e.getMessage(), Map.of("exception_message", e.getMessage(), "stacktrace", ExceptionUtils.getStackTrace(e))));
            cyclesCompleted++;
            sleep(config.cooldownSeconds);
            return;
        }

        if (frames.isEmpty()) {
            sink.accept(emit(State.COOLDOWN, "capture produced no frames"));
            cyclesCompleted++;
            sleep(config.cooldownSeconds);
            return;
        }

        sink.accept(emit(State.RECOGNIZING, "captured frames", Map.of(
                "cycle_id", cycleId,
                "count", frames.size(),
                "first", frames.get(0).toString(),
                "last", frames.get(frames.size() - 1).toString())));

        BestPlate best = recognizeBest(frames);
        if (best == null) {
            sink.accept(emit(State.COOLDOWN, "no plate recognized", Map.of(
                    "cycle_id", cycleId,
                    "frames", frames.size())));
            cyclesCompleted++;
            sleep(config.cooldownSeconds);
            return;
        }

        Map<String, Object> plateDetail = best.toDetail(cycleId);
        sink.accept(emit(State.AUTHORIZING, "recognized '" + best.text + "'", plateDetail));

        if (!allowlist.matches(best.text)) {
            sink.accept(emit(State.COOLDOWN, "denied: '" + best.text + "' not in allowlist", plateDetail));
            cyclesCompleted++;
            sleep(config.cooldownSeconds);
            return;
        }

        openCloseGate(sink, best, cycleId);
        cyclesCompleted++;
        sleep(config.cooldownSeconds);
    }

    private Event maybeReloadAllowlist() {
        ReloadStatus status = allowlist.reloadIfChanged();
        if (status.message().isEmpty()) {
            return null;
        }
        return emit(State.IDLE, "allowlist " + status.message(), Map.of(
                "changed", status.changed(),
                "ok", status.ok(),
                "size_before", status.sizeBefore(),
                "size_after", status.sizeAfter()));
    }

    private Double awaitTrigger() {
        int consecutive = 0;
        while (!stopRequested) {
            try {
                Reading reading = proximity.read();
                if (reading.isNear()) {
                    consecutive++;
                    if (consecutive >= config.debounceCount) {
                        return reading.distanceCm();
                    }
                } else {
                    consecutive = 0;
                }
            } catch (RuntimeException ignored) {
                consecutive = 0;
            }
            sleep(config.proximityIntervalSec);
        }
        return null;
    }

    private List<Path> captureFrames(int cycleId) {
        Path cycleDir = config.photosDir.resolve(String.format("cycle_%04d", cycleId));
        List<CaptureResult> results = camera.captureMany(
                cycleDir,
                config.captureCount,
                config.captureIntervalSec,
                Camera.DEFAULT_PREFIX,
                Camera.DEFAULT_EXT);
        List<Path> paths = new ArrayList<>(results.size());
        for (CaptureResult r : results) {
            paths.add(r.path());
        }
        return paths;
    }

    private BestPlate recognizeBest(List<Path> frames) {
        BestPlate best = null;
        for (Path path : frames) {
            RecognitionResult result;
            try {
                result = recognizer.recognize(path);
            } catch (RuntimeException ignored) {
                continue;
            }
            for (PlateDetection plate : result.plates()) {
                if (plate.text() == null || plate.text().isBlank()) {
                    continue;
                }
                if (plate.ocrConfidence() < config.minOcrConfidence) {
                    continue;
                }
                if (best == null || plate.ocrConfidence() > best.ocrConfidence) {
                    best = new BestPlate(
                            plate.text(),
                            round4(plate.ocrConfidence()),
                            round4(plate.detectionConfidence()),
                            path);
                }
            }
        }
        return best;
    }

    private void openCloseGate(Consumer<Event> sink, BestPlate plate, int cycleId) {
        String prefix = config.dryRun ? "dry-run: " : "";
        Map<String, Object> detail = plate.toDetail(cycleId);

        sink.accept(emit(State.OPENING, prefix + "opening for '" + plate.text + "'", detail));
        if (!config.dryRun) {
            try {
                gate.open();
            } catch (RuntimeException e) {
                sink.accept(emit(State.COOLDOWN, "gate open failed: " + e.getMessage(),
                        Map.of("cycle_id", cycleId, "exception_message", e.getMessage(), "stacktrace", ExceptionUtils.getStackTrace(e))));
                return;
            }
        }

        sink.accept(emit(State.OPEN_HOLD, prefix + "holding open", Map.of(
                "cycle_id", cycleId,
                "seconds", config.openHoldSeconds)));
        holdOpenPeriod();

        sink.accept(emit(State.CLOSING, prefix + "closing", Map.of("cycle_id", cycleId)));
        if (!config.dryRun) {
            try {
                gate.close();
            } catch (RuntimeException e) {
                sink.accept(emit(State.COOLDOWN, "gate close failed: " + e.getMessage(),
                        Map.of("cycle_id", cycleId, "exception_message", e.getMessage(), "stacktrace", ExceptionUtils.getStackTrace(e))));
                return;
            }
        }

        sink.accept(emit(State.COOLDOWN, "cycle complete", Map.of("cycle_id", cycleId)));
    }

    private void holdOpenPeriod() {
        sleep(config.openHoldSeconds);
        if (!config.extendHoldOnNear) {
            return;
        }
        while (!stopRequested) {
            try {
                Reading reading = proximity.read();
                if (!reading.isNear()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                return;
            }
            sleep(config.openHoldSeconds);
        }
    }

    private void shutdown(Consumer<Event> sink) {
        sink.accept(emit(State.STOPPING, "shutting down", Map.of(
                "cycles_completed", cyclesCompleted)));
        if (!config.dryRun) {
            try {
                gate.close();
            } catch (RuntimeException ignored) {
                // best-effort
            }
        }
        try {
            gate.release();
        } catch (RuntimeException ignored) {
            // best-effort
        }
        proximity.close();
        camera.close();
    }

    private Event emit(State state, String message) {
        return emit(state, message, Map.of());
    }

    private Event emit(State state, String message, Map<String, Object> detail) {
        return new Event(Instant.now(), state, message, detail);
    }

    private void sleep(double totalSec) {
        if (totalSec <= 0) {
            return;
        }
        long deadline = System.nanoTime() + (long) (totalSec * 1_000_000_000L);
        long chunk = (long) (config.sleepChunkSec * 1_000_000_000L);
        while (!stopRequested) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            LockSupport.parkNanos(Math.min(chunk, remaining));
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    private static final class BestPlate {
        final String text;
        final double ocrConfidence;
        final double detectionConfidence;
        final Path frame;

        BestPlate(String text, double ocrConfidence, double detectionConfidence, Path frame) {
            this.text = text;
            this.ocrConfidence = ocrConfidence;
            this.detectionConfidence = detectionConfidence;
            this.frame = frame;
        }

        Map<String, Object> toDetail(int cycleId) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", text);
            m.put("ocr_confidence", ocrConfidence);
            m.put("detection_confidence", detectionConfidence);
            m.put("frame", frame.toString());
            m.put("cycle_id", cycleId);
            return m;
        }
    }
}
