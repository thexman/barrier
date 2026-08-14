package com.a9ski.barrier.proximity;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MockProximitySensorTest {

    @Test
    void sequenceIsConsumedInOrderAndLoops() {
        MockProximitySensor sensor = MockProximitySensor.fromSequence(
                List.of(300.0, 200.0, 50.0), true, 100.0, 400.0);
        try (sensor) {
            assertEquals(300.0, sensor.distanceCm(), 1e-9);
            assertEquals(200.0, sensor.distanceCm(), 1e-9);
            assertEquals(50.0, sensor.distanceCm(), 1e-9);
            assertEquals(300.0, sensor.distanceCm(), 1e-9); // loop
        }
    }

    @Test
    void readingClassifiesAgainstThreshold() {
        MockProximitySensor sensor = MockProximitySensor.fromSequence(
                List.of(150.0, 80.0), false, 100.0, 400.0);
        try (sensor) {
            Reading far = sensor.read();
            assertFalse(far.isNear());
            assertEquals(150.0, far.distanceCm(), 1e-9);
            Reading near = sensor.read();
            assertTrue(near.isNear());
            assertEquals(80.0, near.distanceCm(), 1e-9);
        }
    }

    @Test
    void distanceIsClampedToMax() {
        MockProximitySensor sensor = MockProximitySensor.fromSequence(
                List.of(999.0, -50.0), false, 100.0, 400.0);
        try (sensor) {
            assertEquals(400.0, sensor.distanceCm(), 1e-9);
            assertEquals(0.0, sensor.distanceCm(), 1e-9);
        }
    }

    @Test
    void defaultTriangleWaveEventuallyFlipsNear() throws Exception {
        MockProximitySensor sensor = new MockProximitySensor();
        try (sensor) {
            boolean sawNear = false, sawFar = false;
            long deadline = System.nanoTime() + 7L * 1_000_000_000L;
            while (System.nanoTime() < deadline && !(sawNear && sawFar)) {
                if (sensor.isNear()) {
                    sawNear = true;
                } else {
                    sawFar = true;
                }
                Thread.sleep(50);
            }
            assertTrue(sawNear, "expected at least one NEAR reading from the default wave");
            assertTrue(sawFar, "expected at least one FAR reading from the default wave");
        }
    }
}
