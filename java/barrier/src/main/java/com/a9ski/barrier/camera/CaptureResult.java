package com.a9ski.barrier.camera;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One captured image on disk plus its metadata.
 */
public record CaptureResult(
        java.nio.file.Path path,
        Instant timestamp,
        int width,
        int height) {

    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", path.toString());
        m.put("timestamp", timestamp.toString());
        m.put("width", width);
        m.put("height", height);
        return m;
    }
}
