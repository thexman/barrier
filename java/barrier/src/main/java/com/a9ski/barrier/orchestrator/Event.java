package com.a9ski.barrier.orchestrator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** One state-transition record emitted by {@link Orchestrator#run}. */
public record Event(Instant timestamp, State state, String message, Map<String, Object> detail) {

    public Event {
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }

    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", timestamp.toString());
        m.put("state", state.value());
        m.put("message", message);
        m.put("detail", detail);
        return m;
    }
}
