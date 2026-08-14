package com.a9ski.barrier.proximity;

import java.time.Instant;

/**
 * Common interface for the real (diozero) and mock proximity sensors.
 *
 * <p>Implementations must provide {@link #distanceCm()} and the two calibration
 * values. Everything else has a default implementation on top so the mock
 * doesn't have to repeat threshold logic.
 *
 * <p>{@link AutoCloseable#close()} is intentionally overridden without a
 * checked exception — hardware release is best-effort and callers should not
 * be forced into a {@code try/catch} boilerplate that they'd swallow anyway.
 */
public interface ProximitySensor extends AutoCloseable {

    /** Default BCM pin numbers matching the wiring in the top-level README. */
    int DEFAULT_TRIGGER_PIN = 23;
    int DEFAULT_ECHO_PIN = 24;
    double DEFAULT_MAX_DISTANCE_CM = 400.0;
    double DEFAULT_THRESHOLD_CM = 100.0;

    /** Return a fresh distance measurement in cm, clamped to {@link #maxDistanceCm()}. */
    double distanceCm();

    /** Distance at (or below) which readings are classified as NEAR. */
    double thresholdCm();

    /** Sensor range ceiling; readings are clamped here. */
    double maxDistanceCm();

    /** Take one measurement and package it as a {@link Reading}. */
    default Reading read() {
        double d = distanceCm();
        return new Reading(Instant.now(), d, d <= thresholdCm());
    }

    /** Convenience: is the sensor currently reading NEAR? */
    default boolean isNear() {
        return read().isNear();
    }

    @Override
    void close();
}
