package com.a9ski.barrier.servo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MockGateTest {

    @Test
    void openCloseCycleUpdatesState() {
        MockGate gate = new MockGate();
        gate.open();
        assertEquals(GateState.OPEN, gate.state());
        gate.close();
        assertEquals(GateState.CLOSED, gate.state());
        assertEquals(2, gate.history().size());
        assertEquals("open", gate.history().get(0).action());
        assertEquals("close", gate.history().get(1).action());
        gate.release();
    }

    @Test
    void setAngleIntermediateState() {
        MockGate gate = new MockGate();
        gate.setAngle(45.0);
        assertEquals(GateState.INTERMEDIATE, gate.state());
        assertEquals(45.0, gate.currentAngle(), 1e-9);
        gate.release();
    }

    @Test
    void releaseDetachesAndIsIdempotent() {
        MockGate gate = new MockGate();
        gate.open();
        GateEvent release = gate.release();
        assertEquals(GateState.UNKNOWN, release.state());
        assertNull(gate.currentAngle());
        gate.release(); // idempotent for hardware; both releases are logged
        assertEquals(3, gate.history().size());
        assertNull(gate.currentAngle());
    }

    @Test
    void commandAfterReleaseThrows() {
        MockGate gate = new MockGate();
        gate.release();
        assertThrows(IllegalStateException.class, gate::open);
    }

    @Test
    void angleOutOfRangeThrows() {
        MockGate gate = new MockGate();
        assertThrows(IllegalArgumentException.class, () -> gate.setAngle(200.0));
    }

    @Test
    void angleToPulseMsMapsEndpoints() {
        float min = AbstractGate.angleToPulseMs(-90, -90, 90, 1.0, 2.0);
        float max = AbstractGate.angleToPulseMs(90, -90, 90, 1.0, 2.0);
        assertEquals(1.0f, min, 1e-6f);
        assertEquals(2.0f, max, 1e-6f);
    }
}
