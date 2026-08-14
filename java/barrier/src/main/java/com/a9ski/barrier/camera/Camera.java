package com.a9ski.barrier.camera;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Common interface for the real (rpicam-still) and mock cameras.
 */
public interface Camera {

    int DEFAULT_WIDTH = 2592;
    int DEFAULT_HEIGHT = 1944;
    double DEFAULT_WARMUP_S = 1.0;
    double DEFAULT_INTERVAL_S = 1.0;
    int DEFAULT_ROTATION = 180;
    String DEFAULT_PREFIX = "frame";
    String DEFAULT_EXT = "jpg";

    /** Allowed values for {@code --rotate} / rpicam-still {@code --rotation}. */
    static void requireValidRotation(int degrees) {
        if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
            throw new IllegalArgumentException(
                    "rotate must be 0, 90, 180 or 270; got " + degrees);
        }
    }

    int width();

    int height();

    /** Take one picture and write it to {@code path}. Creates parent dirs. */
    CaptureResult capture(Path path);

    /**
     * Capture {@code count} frames under {@code outputDir}.
     *
     * <p>Filenames follow {@code {prefix}_{index:04d}.{ext}} (1-indexed).
     * Sleeps {@code intervalSec} seconds <em>between</em> successive captures;
     * the first frame is taken immediately.
     */
    default List<CaptureResult> captureMany(
            Path outputDir,
            int count,
            double intervalSec,
            String prefix,
            String ext) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        if (intervalSec < 0) {
            throw new IllegalArgumentException("intervalSec must be >= 0");
        }
        String cleanExt = ext.startsWith(".") ? ext.substring(1) : ext;
        try {
            Files.createDirectories(outputDir);
        } catch (java.io.IOException e) {
            throw new RuntimeException("failed to create output dir " + outputDir, e);
        }

        List<CaptureResult> results = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Path path = outputDir.resolve(String.format("%s_%04d.%s", prefix, i, cleanExt));
            results.add(capture(path));
            if (i < count && intervalSec > 0) {
                LockSupport.parkNanos((long) (intervalSec * 1_000_000_000L));
            }
        }
        return results;
    }

    /** Release any resources. Safe to call multiple times. */
    void close();
}
