package com.a9ski.barrier.camera;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;

/**
 * Software-only camera that writes synthetic JPGs using Java2D.
 *
 * <p>Every capture writes a colourful gradient with a large frame index and a
 * timestamp overlay — useful for hardware-free tests and laptop demos.
 * {@code rotationDegrees} mirrors {@code rpicam-still --rotation} for mock runs.
 */
public final class MockCamera implements Camera {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int width;
    private final int height;
    private final int rotationDegrees;
    private int counter;

    public MockCamera(int width, int height) {
        this(width, height, Camera.DEFAULT_ROTATION);
    }

    public MockCamera(int width, int height, int rotationDegrees) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
        Camera.requireValidRotation(rotationDegrees);
        this.width = width;
        this.height = height;
        this.rotationDegrees = rotationDegrees;
    }

    public MockCamera() {
        this(1280, 720);
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
        counter++;
        BufferedImage image = applyRotation(syntheticFrame(width, height, counter), rotationDegrees);
        try {
            Files.createDirectories(path.getParent());
            if (!ImageIO.write(image, "jpg", path.toFile())) {
                throw new RuntimeException("ImageIO failed to write " + path);
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to write mock frame to " + path, e);
        }
        return new CaptureResult(
                path.toAbsolutePath(), Instant.now(), image.getWidth(), image.getHeight());
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    static BufferedImage syntheticFrame(int width, int height, int index) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            float hueShift = (index * 7) % 360;
            Color topLeft = Color.getHSBColor(hueShift / 360f, 0.75f, 0.95f);
            Color bottomRight = Color.getHSBColor((hueShift + 120) % 360 / 360f, 0.65f, 0.55f);
            g.setPaint(new GradientPaint(0, 0, topLeft, width, height, bottomRight));
            g.fillRect(0, 0, width, height);

            float scale = Math.max(1f, width / 640f);
            int fontSize = Math.max(18, Math.round(28 * scale));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));

            String label = String.format("MOCK #%04d", index);
            String timestamp = TS.format(LocalDateTime.now());

            drawOutlinedText(g, label, (int) (20 * scale), (int) (60 * scale));
            drawOutlinedText(g, timestamp, (int) (20 * scale), height - (int) (20 * scale));
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Approximate {@code rpicam-still --rotation} for mock frames. */
    static BufferedImage applyRotation(BufferedImage source, int degrees) {
        Camera.requireValidRotation(degrees);
        if (degrees == 0) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        AffineTransform transform = new AffineTransform();
        int outW;
        int outH;
        switch (degrees) {
            case 90 -> {
                transform.translate(h, 0);
                transform.rotate(Math.toRadians(90));
                outW = h;
                outH = w;
            }
            case 180 -> {
                transform.translate(w, h);
                transform.rotate(Math.toRadians(180));
                outW = w;
                outH = h;
            }
            case 270 -> {
                transform.translate(0, w);
                transform.rotate(Math.toRadians(270));
                outW = h;
                outH = w;
            }
            default -> throw new IllegalStateException("unreachable rotate " + degrees);
        }
        BufferedImage dest = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, transform, null);
        } finally {
            g.dispose();
        }
        return dest;
    }

    private static void drawOutlinedText(Graphics2D g, String text, int x, int y) {
        g.setColor(Color.BLACK);
        g.drawString(text, x + 2, y + 2);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }
}
