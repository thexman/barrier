package com.a9ski.barrier.servo;

import com.diozero.devices.PwmServo;

/**
 * Real gate driven by diozero's {@link PwmServo} PWM device.
 *
 * <p>Angles are mapped linearly to pulse widths between {@code minPulseMs} and
 * {@code maxPulseMs}.
 */
public final class ServoGate extends AbstractGate {

    private static final int SERVO_PWM_HZ = 50;

    private final double minPulseMs;
    private final double maxPulseMs;
    private PwmServo servo;

    public ServoGate(
            int pin,
            double openAngle,
            double closeAngle,
            double minAngle,
            double maxAngle,
            double minPulseMs,
            double maxPulseMs,
            double moveTimeSec) {
        super(openAngle, closeAngle, minAngle, maxAngle, moveTimeSec);
        if (minPulseMs <= 0 || maxPulseMs <= minPulseMs) {
            throw new IllegalArgumentException("Require 0 < minPulseMs < maxPulseMs");
        }
        this.minPulseMs = minPulseMs;
        this.maxPulseMs = maxPulseMs;
        float centrePulse = angleToPulseMs(
                (minAngle + maxAngle) / 2.0, minAngle, maxAngle, minPulseMs, maxPulseMs);
        this.servo = new PwmServo(pin, centrePulse, SERVO_PWM_HZ);
    }

    @Override
    protected void applyAngle(Double deg) {
        if (servo == null) {
            return;
        }
        if (deg == null) {
            return;
        }
        float pulse = angleToPulseMs(deg, minAngle(), maxAngle(), minPulseMs, maxPulseMs);
        servo.setPulseWidthMs(pulse);
    }

    @Override
    protected void teardown() {
        PwmServo s = servo;
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (RuntimeException ignored) {
            // best-effort release
        } finally {
            servo = null;
        }
    }
}
