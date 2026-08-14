package com.a9ski.barrier.proximity;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Software-only proximity sensor. Nothing GPIO related is imported, so this
 * works on any OS and in unit tests.
 *
 * <p>Two distance sources are supported:
 *
 * <ul>
 *   <li>A {@link DoubleSupplier} that returns the current distance in cm.
 *       Handy for math-driven patterns (sine wave, ramp, etc.).</li>
 *   <li>A {@link List List&lt;Double&gt;} of distances that is consumed in
 *       order; when exhausted it either loops back to the start (default) or
 *       sticks on the last value.</li>
 * </ul>
 *
 * <p>The zero-arg constructor uses a slow cosine wave (20 cm ↔ ~150 cm over
 * ~6 s) so a laptop dev loop naturally exercises both NEAR and FAR states.
 */
public final class MockProximitySensor implements ProximitySensor {

    private final double thresholdCm;
    private final double maxDistanceCm;
    private final DoubleSupplier source;

    public MockProximitySensor() {
        this(ProximitySensor.DEFAULT_THRESHOLD_CM, ProximitySensor.DEFAULT_MAX_DISTANCE_CM);
    }

    public MockProximitySensor(double thresholdCm, double maxDistanceCm) {
        this(thresholdCm, maxDistanceCm, defaultTriangleWave(thresholdCm, maxDistanceCm));
    }

    public MockProximitySensor(double thresholdCm, double maxDistanceCm, DoubleSupplier source) {
        if (thresholdCm <= 0) {
            throw new IllegalArgumentException("thresholdCm must be > 0");
        }
        if (maxDistanceCm <= 0) {
            throw new IllegalArgumentException("maxDistanceCm must be > 0");
        }
        this.thresholdCm = thresholdCm;
        this.maxDistanceCm = maxDistanceCm;
        this.source = source;
    }

    public static MockProximitySensor fromSequence(List<Double> distancesCm, boolean loop,
                                                   double thresholdCm, double maxDistanceCm) {
        if (distancesCm == null || distancesCm.isEmpty()) {
            throw new IllegalArgumentException("distancesCm must be non-empty");
        }
        Double[] arr = distancesCm.toArray(new Double[0]);
        int[] index = {0};
        DoubleSupplier supplier = () -> {
            if (index[0] >= arr.length) {
                if (loop) {
                    index[0] = 0;
                } else {
                    return arr[arr.length - 1];
                }
            }
            return arr[index[0]++];
        };
        return new MockProximitySensor(thresholdCm, maxDistanceCm, supplier);
    }

    @Override
    public double distanceCm() {
        double raw = source.getAsDouble();
        return Math.max(0.0, Math.min(raw, maxDistanceCm));
    }

    @Override
    public double thresholdCm() {
        return thresholdCm;
    }

    @Override
    public double maxDistanceCm() {
        return maxDistanceCm;
    }

    @Override
    public void close() {
        // Nothing to release.
    }

    /**
     * Slow cosine wave between {@code near} and {@code far} with a 6 second
     * period.
     */
    private static DoubleSupplier defaultTriangleWave(double thresholdCm, double maxDistanceCm) {
        double near = 20.0;
        double far = Math.min(maxDistanceCm, Math.max(thresholdCm * 2.0, 150.0));
        double amplitude = (far - near) / 2.0;
        double midpoint = (far + near) / 2.0;
        double periodMs = 6000.0;
        long startNanos = System.nanoTime();
        return () -> {
            double tSec = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            double phase = 2.0 * Math.PI * (tSec * 1000.0) / periodMs;
            return midpoint - amplitude * Math.cos(phase);
        };
    }
}
