package com.a9ski.barrier.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import com.a9ski.barrier.BarrierCli;
import com.a9ski.barrier.camera.Camera;
import com.a9ski.barrier.camera.CaptureResult;
import com.a9ski.barrier.camera.MockCamera;
import com.a9ski.barrier.camera.RpiCamera;

/**
 * {@code barrier camera OUTPUT_DIR} — take one or many still pictures.
 *
 * <p>On the Pi the real backend shells out to {@code rpicam-still}; use
 * {@code --mock} for synthetic JPGs on hosts without a CSI camera.
 */
@Command(
        name = "camera",
        description = "Take pictures with the Raspberry Pi CSI camera (rpicam-still on Pi, mock elsewhere).")
public final class CameraCommand implements Runnable {

    @ParentCommand
    private BarrierCli root;

    @Parameters(
            index = "0",
            description = "Directory that will hold the captured images (created if missing).")
    private Path outputDir;

    @Option(names = {"-n", "--count"}, description = "How many pictures to take (required).")
    private int count = -1;

    @Option(names = "--interval", description = "Delay between captures in seconds (default: ${DEFAULT-VALUE}).")
    private double intervalSec = Camera.DEFAULT_INTERVAL_S;

    @Option(
            names = "--resolution",
            description = "Sensor resolution as WxH (default: ${DEFAULT-VALUE}).")
    private String resolution = Camera.DEFAULT_WIDTH + "x" + Camera.DEFAULT_HEIGHT;

    @Option(names = "--prefix", description = "Filename prefix (default: ${DEFAULT-VALUE}).")
    private String prefix = Camera.DEFAULT_PREFIX;

    @Option(names = "--ext", description = "Image file extension (default: ${DEFAULT-VALUE}).")
    private String ext = Camera.DEFAULT_EXT;

    @Option(names = "--warmup", description = "Sensor warm-up before the first capture in seconds (default: ${DEFAULT-VALUE}).")
    private double warmupSec = Camera.DEFAULT_WARMUP_S;

    @Option(
            names = "--rotate",
            description = "Pass --rotation DEG to rpicam-still (0, 90, 180 or 270; default: ${DEFAULT-VALUE}).")
    private int rotate = Camera.DEFAULT_ROTATION;

    @Option(names = "--mock", description = "Use a software mock camera; no CSI hardware required.")
    private boolean mock;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter PRETTY_TS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void run() {
        if (count < 0) {
            throw new IllegalArgumentException("--count is required and must be >= 0");
        }
        if (intervalSec < 0) {
            throw new IllegalArgumentException("--interval must be >= 0");
        }
        if (warmupSec < 0) {
            throw new IllegalArgumentException("--warmup must be >= 0");
        }
        Camera.requireValidRotation(rotate);

        int[] res = parseResolution(resolution);
        Camera camera = buildCamera(res[0], res[1]);
        try {
            List<CaptureResult> results = camera.captureMany(
                    outputDir, count, intervalSec, prefix, ext);
            for (CaptureResult result : results) {
                emit(result);
            }
        } finally {
            camera.close();
        }
    }

    private Camera buildCamera(int width, int height) {
        if (mock) {
            return new MockCamera(width, height, rotate);
        }
        return new RpiCamera(width, height, warmupSec, rotate);
    }

    private void emit(CaptureResult result) {
        boolean asJson = root != null && root.isLogJson();
        if (asJson) {
            try {
                System.out.println(JSON.writeValueAsString(result.toJsonMap()));
            } catch (IOException e) {
                throw new RuntimeException("failed to serialise capture result", e);
            }
        } else {
            String ts = PRETTY_TS.format(result.timestamp().atZone(java.time.ZoneId.systemDefault()));
            System.out.printf(Locale.ROOT, "[%s] %dx%d  %s%n",
                    ts, result.width(), result.height(), result.path());
        }
        System.out.flush();
    }

    static int[] parseResolution(String text) {
        try {
            String[] parts = text.toLowerCase(Locale.ROOT).split("x", 2);
            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());
            if (w <= 0 || h <= 0) {
                throw new IllegalArgumentException("resolution values must be positive; got " + w + "x" + h);
            }
            return new int[] {w, h};
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "invalid resolution " + text + "; expected WIDTHxHEIGHT (e.g. 2592x1944)", e);
        }
    }
}
