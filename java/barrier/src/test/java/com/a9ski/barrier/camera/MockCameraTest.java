package com.a9ski.barrier.camera;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MockCameraTest {

    @TempDir
    Path tempDir;

    @Test
    void captureWritesJpgWithExpectedResolution() throws Exception {
        MockCamera camera = new MockCamera(640, 480);
        Path out = tempDir.resolve("frame_0001.jpg");
        CaptureResult result = camera.capture(out);
        assertTrue(Files.isRegularFile(out));
        assertEquals(640, result.width());
        assertEquals(480, result.height());
        assertTrue(out.toString().endsWith("frame_0001.jpg"));
    }

    @Test
    void captureManyProducesSequentialFiles() {
        MockCamera camera = new MockCamera(320, 240);
        var results = camera.captureMany(tempDir, 3, 0, "frame", "jpg");
        assertEquals(3, results.size());
        assertTrue(Files.isRegularFile(tempDir.resolve("frame_0001.jpg")));
        assertTrue(Files.isRegularFile(tempDir.resolve("frame_0003.jpg")));
    }

    @Test
    void syntheticFrameHasExpectedDimensions() {
        BufferedImage img = MockCamera.syntheticFrame(800, 600, 7);
        assertEquals(800, img.getWidth());
        assertEquals(600, img.getHeight());
    }

    @Test
    void captureWithRotate90SwapsDimensions() {
        MockCamera camera = new MockCamera(640, 480, 90);
        Path out = tempDir.resolve("rotated.jpg");
        CaptureResult result = camera.capture(out);
        assertEquals(480, result.width());
        assertEquals(640, result.height());
        assertTrue(Files.isRegularFile(out));
    }

    @Test
    void invalidRotationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MockCamera(320, 240, 45));
        assertThrows(IllegalArgumentException.class, () -> Camera.requireValidRotation(45));
    }
}
