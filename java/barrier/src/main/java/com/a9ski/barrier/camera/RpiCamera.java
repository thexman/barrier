package com.a9ski.barrier.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * CSI camera captures by shelling out to {@code rpicam-still} (Bookworm) or
 * {@code libcamera-still} (older Pi OS).
 *
 * <p>This avoids a Java binding for libcamera while still producing real JPGs
 * from the OV5647 module and anything else rpicam supports.
 */
public final class RpiCamera implements Camera {

    private final int width;
    private final int height;
    private final int rotationDegrees;
    private final String stillCommand;
    private final int captureTimeoutMs;

    public RpiCamera(int width, int height, double warmupSec) {
        this(width, height, warmupSec, Camera.DEFAULT_ROTATION);
    }

    public RpiCamera(int width, int height, double warmupSec, int rotationDegrees) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
        if (warmupSec < 0) {
            throw new IllegalArgumentException("warmupSec must be >= 0");
        }
        Camera.requireValidRotation(rotationDegrees);
        this.width = width;
        this.height = height;
        this.rotationDegrees = rotationDegrees;
        this.stillCommand = StillCommandResolver.resolve();
        this.captureTimeoutMs = 1000;
        if (warmupSec > 0) {
            LockSupport.parkNanos((long) (warmupSec * 1_000_000_000L));
        }
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public CaptureResult capture(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new RuntimeException("failed to create directory for " + path, e);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(stillCommand);
        cmd.add("-o");
        cmd.add(path.toAbsolutePath().toString());
        cmd.add("-n");
        cmd.add("--width");
        cmd.add(Integer.toString(width));
        cmd.add("--height");
        cmd.add(Integer.toString(height));
        cmd.add("--timeout");
        cmd.add(Integer.toString(captureTimeoutMs));
        cmd.add("--rotation");
        cmd.add(Integer.toString(rotationDegrees));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes());
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new RuntimeException(stillCommand + " exited " + exit + ": " + output.trim());
            }
            if (!Files.isRegularFile(path)) {
                throw new RuntimeException(stillCommand + " did not write " + path);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("capture interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException("failed to run " + stillCommand, e);
        }

        // 90/270 swap the sensor axes in the written file.
        boolean swap = rotationDegrees == 90 || rotationDegrees == 270;
        int outW = swap ? height : width;
        int outH = swap ? width : height;
        return new CaptureResult(path.toAbsolutePath(), Instant.now(), outW, outH);
    }

    @Override
    public void close() {
        // Nothing persistent to release for the shell-out backend.
    }

    /** Locate {@code rpicam-still} or {@code libcamera-still} on {@code PATH}. */
    static final class StillCommandResolver {
        private StillCommandResolver() {}

        static String resolve() {
            for (String candidate : List.of("rpicam-still", "libcamera-still")) {
                if (isOnPath(candidate)) {
                    return candidate;
                }
            }
            throw new IllegalStateException(
                    "Neither rpicam-still nor libcamera-still found on PATH. "
                            + "Install rpicam-apps on Raspberry Pi OS: sudo apt install rpicam-apps");
        }

        private static boolean isOnPath(String command) {
            String pathEnv = System.getenv("PATH");
            if (pathEnv == null || pathEnv.isBlank()) {
                return false;
            }
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                Path candidate = Path.of(dir, command);
                if (Files.isExecutable(candidate)) {
                    return true;
                }
                Path withExe = Path.of(dir, command + ".exe");
                if (Files.isExecutable(withExe)) {
                    return true;
                }
            }
            return false;
        }
    }
}
