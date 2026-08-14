package com.a9ski.barrier.servo;

/** Gate state derived from the last commanded angle. */
public enum GateState {
    OPEN("open"),
    CLOSED("closed"),
    INTERMEDIATE("intermediate"),
    UNKNOWN("unknown");

    private final String value;

    GateState(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
