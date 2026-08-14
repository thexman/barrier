package com.a9ski.barrier.orchestrator;

/** State the orchestrator is entering when an event is emitted. */
public enum State {
    IDLE("idle"),
    CAPTURING("capturing"),
    RECOGNIZING("recognizing"),
    AUTHORIZING("authorizing"),
    OPENING("opening"),
    OPEN_HOLD("open_hold"),
    CLOSING("closing"),
    COOLDOWN("cooldown"),
    STOPPING("stopping");

    private final String value;

    State(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
