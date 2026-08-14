package com.a9ski.barrier.proximity;

import com.diozero.devices.HCSR04;

/**
 * HC-SR04 ultrasonic distance sensor backed by diozero.
 *
 * <p>diozero picks its own native GPIO backend at runtime — {@code lgpio} on
 * Pi 5, {@code pigpio} on Pi 4 (needs {@code sudo apt install pigpio}), or
 * the built-in {@code sysfs} fallback. Nothing to configure here.
 *
 * <p>Wiring (see {@code README.md} for the full table): trigger to BCM 23,
 * echo to BCM 24 through a resistor divider (5 V → 3.3 V; e.g. 1 kΩ + 2 kΩ).
 * The raw echo pin is 5 V — do not feed it straight into the Pi's GPIO.
 */
public final class HcSr04ProximitySensor implements ProximitySensor {

    private final HCSR04 device;
    private final double thresholdCm;
    private final double maxDistanceCm;

    public HcSr04ProximitySensor(int triggerPin, int echoPin, double thresholdCm, double maxDistanceCm) {
        if (thresholdCm <= 0) {
            throw new IllegalArgumentException("thresholdCm must be > 0");
        }
        if (maxDistanceCm <= 0) {
            throw new IllegalArgumentException("maxDistanceCm must be > 0");
        }
        this.thresholdCm = thresholdCm;
        this.maxDistanceCm = maxDistanceCm;
        this.device = new HCSR04(triggerPin, echoPin);
    }

    @Override
    public double distanceCm() {
        // diozero returns cm; clamp to our configured max so a "no echo"
        // reading (which comes back as the sensor's timeout distance) does
        // not leak weird values downstream.
        double raw = device.getDistanceCm();
        if (raw < 0 || Double.isNaN(raw)) {
            return maxDistanceCm;
        }
        return Math.min(raw, maxDistanceCm);
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
        try {
            device.close();
        } catch (RuntimeException ignored) {
            // best-effort release
        }
    }
}
