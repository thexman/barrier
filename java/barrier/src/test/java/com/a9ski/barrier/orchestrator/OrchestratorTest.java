package com.a9ski.barrier.orchestrator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.a9ski.barrier.camera.MockCamera;
import com.a9ski.barrier.proximity.MockProximitySensor;
import com.a9ski.barrier.servo.MockGate;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void fullMockCycleOpensGateForAllowedPlate() {
        MockProximitySensor proximity = MockProximitySensor.fromSequence(
                List.of(50.0, 50.0, 50.0), false, 100.0, 400.0);
        MockCamera camera = new MockCamera(160, 120);
        MockGate gate = new MockGate();
        Allowlist allowlist = Allowlist.allowAll();
        PlateRecognizer recognizer = new FixedPlateRecognizer("TEST123");

        OrchestratorConfig config = new OrchestratorConfig(
                tempDir.resolve("photos"),
                1,
                0.0,
                0.05,
                2,
                0.01,
                0.01,
                false,
                0.5,
                1,
                false,
                false,
                0.01);

        Orchestrator orch = new Orchestrator(config, proximity, camera, gate, recognizer, allowlist);
        List<Event> events = new ArrayList<>();
        orch.run(events::add);

        String log = events.stream()
                .map(e -> e.state().value() + ":" + e.message())
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(log.contains("opening"), "expected OPENING; log:\n" + log);
        assertTrue(gate.history().stream().anyMatch(e -> "open".equals(e.action())));
    }
}
