package com.a9ski.barrier.servo;

import java.time.Instant;
import java.util.concurrent.locks.LockSupport;

/**
 * Shared open/close/angle/release logic for {@link ServoGate} and
 * {@link MockGate}.
 */
abstract class AbstractGate implements Gate {

    private static final double STATE_EPS_DEG = 1.5;

    private final double openAngle;
    private final double closeAngle;
    private final double minAngle;
    private final double maxAngle;
    private final double moveTimeSec;

    private Double lastAngle;
    private boolean released;

    protected AbstractGate(
            double openAngle,
            double closeAngle,
            double minAngle,
            double maxAngle,
            double moveTimeSec) {
        if (minAngle >= maxAngle) {
            throw new IllegalArgumentException("minAngle must be < maxAngle");
        }
        if (openAngle < minAngle || openAngle > maxAngle) {
            throw new IllegalArgumentException(
                    "openAngle " + openAngle + " is outside [" + minAngle + ", " + maxAngle + "]");
        }
        if (closeAngle < minAngle || closeAngle > maxAngle) {
            throw new IllegalArgumentException(
                    "closeAngle " + closeAngle + " is outside [" + minAngle + ", " + maxAngle + "]");
        }
        if (moveTimeSec < 0) {
            throw new IllegalArgumentException("moveTimeSec must be >= 0");
        }
        this.openAngle = openAngle;
        this.closeAngle = closeAngle;
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.moveTimeSec = moveTimeSec;
    }

    @Override
    public double openAngle() {
        return openAngle;
    }

    @Override
    public double closeAngle() {
        return closeAngle;
    }

    @Override
    public double minAngle() {
        return minAngle;
    }

    @Override
    public double maxAngle() {
        return maxAngle;
    }

    @Override
    public Double currentAngle() {
        return lastAngle;
    }

    @Override
    public GateState state() {
        return classify(lastAngle);
    }

    @Override
    public GateEvent open() {
        return open(moveTimeSec);
    }

    @Override
    public GateEvent open(double moveTimeSec) {
        return command(openAngle, "open", moveTimeSec);
    }

    @Override
    public GateEvent close() {
        return close(moveTimeSec);
    }

    @Override
    public GateEvent close(double moveTimeSec) {
        return command(closeAngle, "close", moveTimeSec);
    }

    @Override
    public GateEvent setAngle(double deg) {
        return setAngle(deg, moveTimeSec);
    }

    @Override
    public GateEvent setAngle(double deg, double moveTimeSec) {
        if (deg < minAngle || deg > maxAngle) {
            throw new IllegalArgumentException(
                    "angle " + deg + " is outside [" + minAngle + ", " + maxAngle + "]");
        }
        return command(deg, "angle", moveTimeSec);
    }

    @Override
    public GateEvent release() {
        if (!released) {
            applyAngle(null);
            teardown();
            released = true;
        }
        lastAngle = null;
        return new GateEvent(Instant.now(), "release", null, GateState.UNKNOWN);
    }

    /** Send the actual PWM command. {@code null} = detach / no pulse. */
    protected abstract void applyAngle(Double deg);

    /** Optional hardware cleanup called once by {@link #release()}. */
    protected void teardown() {
        // default no-op
    }

    private GateEvent command(double deg, String action, double waitSec) {
        if (released) {
            throw new IllegalStateException("Gate has been released; construct a new instance.");
        }
        if (waitSec < 0) {
            throw new IllegalArgumentException("moveTimeSec must be >= 0");
        }
        applyAngle(deg);
        lastAngle = deg;
        if (waitSec > 0) {
            LockSupport.parkNanos((long) (waitSec * 1_000_000_000L));
        }
        return new GateEvent(Instant.now(), action, deg, classify(deg));
    }

    private GateState classify(Double deg) {
        if (deg == null) {
            return GateState.UNKNOWN;
        }
        if (Math.abs(deg - openAngle) <= STATE_EPS_DEG) {
            return GateState.OPEN;
        }
        if (Math.abs(deg - closeAngle) <= STATE_EPS_DEG) {
            return GateState.CLOSED;
        }
        return GateState.INTERMEDIATE;
    }

    /** Linear map from angle to pulse width in milliseconds. */
    static float angleToPulseMs(
            double angleDeg,
            double minAngle,
            double maxAngle,
            double minPulseMs,
            double maxPulseMs) {
        double span = maxAngle - minAngle;
        if (span <= 0) {
            throw new IllegalArgumentException("maxAngle must be > minAngle");
        }
        double t = (angleDeg - minAngle) / span;
        return (float) (minPulseMs + t * (maxPulseMs - minPulseMs));
    }
}
