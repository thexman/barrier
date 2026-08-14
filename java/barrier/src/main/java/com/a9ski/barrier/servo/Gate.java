package com.a9ski.barrier.servo;

/**
 * Common interface for the real (diozero) and mock gates.
 *
 * <p>Hobby servos have no position feedback, so {@link #state()} reflects the
 * <em>last commanded</em> angle.
 */
public interface Gate {

    int DEFAULT_PIN = 18;
    double DEFAULT_OPEN_ANGLE = 90.0;
    double DEFAULT_CLOSE_ANGLE = 0.0;
    double DEFAULT_MIN_ANGLE = -90.0;
    double DEFAULT_MAX_ANGLE = 90.0;
    double DEFAULT_MIN_PULSE_MS = 1.0;
    double DEFAULT_MAX_PULSE_MS = 2.0;
    double DEFAULT_MOVE_TIME_S = 0.5;

    double openAngle();

    double closeAngle();

    double minAngle();

    double maxAngle();

    /** Last commanded angle, or {@code null} if the servo has been released. */
    Double currentAngle();

    GateState state();

    GateEvent open();

    GateEvent open(double moveTimeSec);

    GateEvent close();

    GateEvent close(double moveTimeSec);

    GateEvent setAngle(double deg);

    GateEvent setAngle(double deg, double moveTimeSec);

    /** Stop the PWM signal and free GPIO resources. Idempotent. */
    GateEvent release();
}
