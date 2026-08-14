package com.a9ski.barrier.servo;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One command applied to the gate.
 *
 * @param timestamp wall-clock time the command completed
 * @param action    {@code "open"}, {@code "close"}, {@code "angle"} or {@code "release"}
 * @param angleDeg  commanded angle, or {@code null} when detached
 * @param state     post-command classification
 */
public record GateEvent(Instant timestamp, String action, Double angleDeg, GateState state) {

    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp.toString());
        m.put("action", action);
        m.put("angle_deg", angleDeg);
        m.put("state", state.value());
        return m;
    }
}
