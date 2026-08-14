package com.a9ski.barrier.orchestrator;

import java.nio.file.Path;

/** All orchestrator tunables. */
public final class OrchestratorConfig {

    public final Path photosDir;
    public final int captureCount;
    public final double captureIntervalSec;
    public final double proximityIntervalSec;
    public final int debounceCount;
    public final double openHoldSeconds;
    public final double cooldownSeconds;
    public final boolean extendHoldOnNear;
    public final double minOcrConfidence;
    public final int maxCycles;
    public final boolean dryRun;
    public final boolean reloadAllowlist;
    public final double sleepChunkSec;

    public OrchestratorConfig(
            Path photosDir,
            int captureCount,
            double captureIntervalSec,
            double proximityIntervalSec,
            int debounceCount,
            double openHoldSeconds,
            double cooldownSeconds,
            boolean extendHoldOnNear,
            double minOcrConfidence,
            int maxCycles,
            boolean dryRun,
            boolean reloadAllowlist) {
        this(photosDir, captureCount, captureIntervalSec, proximityIntervalSec, debounceCount,
                openHoldSeconds, cooldownSeconds, extendHoldOnNear, minOcrConfidence, maxCycles,
                dryRun, reloadAllowlist, 0.1);
    }

    OrchestratorConfig(
            Path photosDir,
            int captureCount,
            double captureIntervalSec,
            double proximityIntervalSec,
            int debounceCount,
            double openHoldSeconds,
            double cooldownSeconds,
            boolean extendHoldOnNear,
            double minOcrConfidence,
            int maxCycles,
            boolean dryRun,
            boolean reloadAllowlist,
            double sleepChunkSec) {
        this.photosDir = photosDir;
        this.captureCount = captureCount;
        this.captureIntervalSec = captureIntervalSec;
        this.proximityIntervalSec = proximityIntervalSec;
        this.debounceCount = debounceCount;
        this.openHoldSeconds = openHoldSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.extendHoldOnNear = extendHoldOnNear;
        this.minOcrConfidence = minOcrConfidence;
        this.maxCycles = maxCycles;
        this.dryRun = dryRun;
        this.reloadAllowlist = reloadAllowlist;
        this.sleepChunkSec = sleepChunkSec;
    }
}
