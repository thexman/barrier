package com.a9ski.barrier.servo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Software-only gate for tests and laptop development.
 *
 * <p>All commands are appended to {@link #history()} in issue order. No GPIO
 * code is imported.
 */
public final class MockGate extends AbstractGate {

    private final List<GateEvent> history = new ArrayList<>();

    public MockGate(
            double openAngle,
            double closeAngle,
            double minAngle,
            double maxAngle,
            double moveTimeSec) {
        super(openAngle, closeAngle, minAngle, maxAngle, moveTimeSec);
    }

    /** Fast default for unit tests (zero move delay). */
    public MockGate() {
        this(
                Gate.DEFAULT_OPEN_ANGLE,
                Gate.DEFAULT_CLOSE_ANGLE,
                Gate.DEFAULT_MIN_ANGLE,
                Gate.DEFAULT_MAX_ANGLE,
                0.0);
    }

    public List<GateEvent> history() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public GateEvent open(double moveTimeSec) {
        GateEvent event = super.open(moveTimeSec);
        history.add(event);
        return event;
    }

    @Override
    public GateEvent close(double moveTimeSec) {
        GateEvent event = super.close(moveTimeSec);
        history.add(event);
        return event;
    }

    @Override
    public GateEvent setAngle(double deg, double moveTimeSec) {
        GateEvent event = super.setAngle(deg, moveTimeSec);
        history.add(event);
        return event;
    }

    @Override
    public GateEvent release() {
        GateEvent event = super.release();
        history.add(event);
        return event;
    }

    @Override
    protected void applyAngle(Double deg) {
        // Nothing to do; the base class records the commanded angle.
    }
}
