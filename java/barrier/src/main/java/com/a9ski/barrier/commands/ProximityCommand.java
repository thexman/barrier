package com.a9ski.barrier.commands;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import com.a9ski.barrier.BarrierCli;
import com.a9ski.barrier.proximity.HcSr04ProximitySensor;
import com.a9ski.barrier.proximity.MockProximitySensor;
import com.a9ski.barrier.proximity.ProximitySensor;
import com.a9ski.barrier.proximity.Reading;

/**
 * {@code barrier proximity} — continuously read an HC-SR04 sensor and print
 * each reading as a pretty line or a JSON object.
 *
 * <p>Supports {@code --only-changes}, {@code --count}, and {@code --mock}.
 * JSON output is controlled by the inherited root option {@code --log-json}.
 */
@Command(
        name = "proximity",
        description = "Continuously read the HC-SR04 distance sensor and print each measurement.")
public final class ProximityCommand implements Runnable {

    @ParentCommand
    private BarrierCli parent;

    @Option(names = "--trigger", description = "BCM pin wired to the sensor TRIG line (default: ${DEFAULT-VALUE}).")
    private int triggerPin = ProximitySensor.DEFAULT_TRIGGER_PIN;

    @Option(names = "--echo", description = "BCM pin wired to the sensor ECHO line (default: ${DEFAULT-VALUE}).")
    private int echoPin = ProximitySensor.DEFAULT_ECHO_PIN;

    @Option(names = "--max-distance",
            description = "Sensor range ceiling in cm; readings clamp here (default: ${DEFAULT-VALUE}).")
    private double maxDistanceCm = ProximitySensor.DEFAULT_MAX_DISTANCE_CM;

    @Option(names = "--threshold",
            description = "Distance at or below which a reading is NEAR (default: ${DEFAULT-VALUE}).")
    private double thresholdCm = ProximitySensor.DEFAULT_THRESHOLD_CM;

    @Option(names = "--interval",
            description = "Poll interval in seconds (default: ${DEFAULT-VALUE}).")
    private double intervalSec = 0.2;

    @Option(names = "--count",
            description = "Stop after N readings. 0 = run forever (default).")
    private long count = 0;

    @Option(names = "--only-changes",
            description = "After the first reading, only emit when the near/far state flips.")
    private boolean onlyChanges;

    @Option(names = "--mock",
            description = "Use the software mock sensor; no GPIO required.")
    private boolean mock;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void run() {
        if (intervalSec <= 0) {
            throw new IllegalArgumentException("--interval must be > 0");
        }
        if (thresholdCm <= 0 || maxDistanceCm <= 0) {
            throw new IllegalArgumentException("--threshold and --max-distance must be > 0");
        }

        boolean asJson = parent != null && parent.isLogJson();
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread mainThread = Thread.currentThread();
        // A shutdown hook flips the stop flag and unparks the polling thread
        // so Ctrl-C / SIGTERM don't have to wait out the current sleep.
        Thread hook = new Thread(() -> {
            stop.set(true);
            LockSupport.unpark(mainThread);
        }, "barrier-proximity-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);

        long intervalNanos = (long) (intervalSec * 1_000_000_000L);
        long emitted = 0;
        Boolean lastEmittedNear = null;

        try (ProximitySensor sensor = buildSensor()) {
            while (!stop.get() && (count == 0 || emitted < count)) {
                Reading r;
                try {
                    r = sensor.read();
                } catch (RuntimeException e) {
                    System.err.println("proximity read failed: " + e.getMessage());
                    parkNanos(intervalNanos, stop);
                    continue;
                }

                boolean shouldEmit = !onlyChanges
                        || lastEmittedNear == null
                        || lastEmittedNear != r.isNear();
                if (shouldEmit) {
                    emit(r, asJson);
                    lastEmittedNear = r.isNear();
                    emitted++;
                }

                if (count > 0 && emitted >= count) {
                    break;
                }
                parkNanos(intervalNanos, stop);
            }
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // JVM already shutting down — hook will run anyway.
            }
        }
    }

    private ProximitySensor buildSensor() {
        if (mock) {
            return new MockProximitySensor(thresholdCm, maxDistanceCm);
        }
        return new HcSr04ProximitySensor(triggerPin, echoPin, thresholdCm, maxDistanceCm);
    }

    private static void parkNanos(long nanos, AtomicBoolean stop) {
        long deadline = System.nanoTime() + nanos;
        while (!stop.get()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            LockSupport.parkNanos(remaining);
        }
    }

    private static final DateTimeFormatter PRETTY_TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private void emit(Reading r, boolean asJson) {
        if (asJson) {
            try {
                System.out.println(JSON.writeValueAsString(r.toJsonMap()));
            } catch (IOException e) {
                throw new RuntimeException("failed to serialise reading", e);
            }
        } else {
            String ts = PRETTY_TS.format(r.timestamp().atZone(java.time.ZoneId.systemDefault()));
            System.out.printf(Locale.ROOT, "[%s] %-4s  distance=%6.1f cm%n",
                    ts,
                    r.isNear() ? "NEAR" : "far",
                    r.distanceCm());
        }
        System.out.flush();
    }
}
