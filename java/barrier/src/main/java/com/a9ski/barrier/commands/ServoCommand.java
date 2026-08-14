package com.a9ski.barrier.commands;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import com.a9ski.barrier.BarrierCli;
import com.a9ski.barrier.servo.Gate;
import com.a9ski.barrier.servo.GateEvent;
import com.a9ski.barrier.servo.MockGate;
import com.a9ski.barrier.servo.ServoGate;

/**
 * {@code barrier servo} — open, close and cycle a hobby-servo driven gate.
 *
 * <p>Subcommands: {@code open}, {@code close}, {@code angle}, {@code cycle}.
 * JSON output is controlled by the inherited root flag {@code --log-json}.
 */
@Command(
        name = "servo",
        description = "Open/close a barrier gate driven by a hobby servo on a Raspberry Pi.",
        subcommands = {
                ServoCommand.OpenCommand.class,
                ServoCommand.CloseCommand.class,
                ServoCommand.AngleCommand.class,
                ServoCommand.CycleCommand.class,
        })
public final class ServoCommand {

    @Spec
    CommandSpec spec;

    @ParentCommand
    private BarrierCli root;

    @Option(names = "--pin", description = "BCM pin wired to the servo signal (default: ${DEFAULT-VALUE}).")
    int pin = Gate.DEFAULT_PIN;

    @Option(names = "--open-angle", description = "Angle for the OPEN position in degrees (default: ${DEFAULT-VALUE}).")
    double openAngle = Gate.DEFAULT_OPEN_ANGLE;

    @Option(names = "--close-angle", description = "Angle for the CLOSED position in degrees (default: ${DEFAULT-VALUE}).")
    double closeAngle = Gate.DEFAULT_CLOSE_ANGLE;

    @Option(names = "--min-angle", description = "Physical minimum angle in degrees (default: ${DEFAULT-VALUE}).")
    double minAngle = Gate.DEFAULT_MIN_ANGLE;

    @Option(names = "--max-angle", description = "Physical maximum angle in degrees (default: ${DEFAULT-VALUE}).")
    double maxAngle = Gate.DEFAULT_MAX_ANGLE;

    @Option(names = "--min-pulse-ms", description = "Pulse width for min-angle in ms (default: ${DEFAULT-VALUE}).")
    double minPulseMs = Gate.DEFAULT_MIN_PULSE_MS;

    @Option(names = "--max-pulse-ms", description = "Pulse width for max-angle in ms (default: ${DEFAULT-VALUE}).")
    double maxPulseMs = Gate.DEFAULT_MAX_PULSE_MS;

    @Option(names = "--move-time", description = "Seconds to wait for the servo to reach the target (default: ${DEFAULT-VALUE}).")
    double moveTimeSec = Gate.DEFAULT_MOVE_TIME_S;

    @Option(names = "--mock", description = "Use a software mock gate; no GPIO required.")
    boolean mock;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter PRETTY_TS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    Gate buildGate() {
        if (moveTimeSec < 0) {
            throw new IllegalArgumentException("--move-time must be >= 0");
        }
        if (minPulseMs <= 0 || maxPulseMs <= minPulseMs) {
            throw new IllegalArgumentException("Require 0 < --min-pulse-ms < --max-pulse-ms");
        }
        if (mock) {
            return new MockGate(openAngle, closeAngle, minAngle, maxAngle, moveTimeSec);
        }
        return new ServoGate(
                pin, openAngle, closeAngle, minAngle, maxAngle,
                minPulseMs, maxPulseMs, moveTimeSec);
    }

    void emit(GateEvent event) {
        boolean asJson = root != null && root.isLogJson();
        if (asJson) {
            try {
                System.out.println(JSON.writeValueAsString(event.toJsonMap()));
            } catch (IOException e) {
                throw new RuntimeException("failed to serialise gate event", e);
            }
        } else {
            String ts = PRETTY_TS.format(event.timestamp().atZone(java.time.ZoneId.systemDefault()));
            String angleStr = event.angleDeg() == null ? "  --" : String.format(Locale.ROOT, "%6.1f", event.angleDeg());
            System.out.printf(Locale.ROOT,
                    "[%s] %-7s angle=%s deg  state=%s%n",
                    ts,
                    event.action(),
                    angleStr,
                    event.state().value().toUpperCase(Locale.ROOT));
        }
        System.out.flush();
    }

    static void sleepSeconds(double sec) {
        if (sec <= 0) {
            return;
        }
        LockSupport.parkNanos((long) (sec * 1_000_000_000L));
    }

    // ---------------------------------------------------------------------
    // Subcommands
    // ---------------------------------------------------------------------

    @Command(name = "open", description = "Move the gate to the OPEN position.")
    static final class OpenCommand implements Runnable {

        @ParentCommand
        ServoCommand parent;

        @Option(names = "--auto-close-after",
                description = "After opening, wait N seconds and then close again.")
        Double autoCloseAfter;

        @Override
        public void run() {
            if (autoCloseAfter != null && autoCloseAfter < 0) {
                throw new IllegalArgumentException("--auto-close-after must be >= 0");
            }
            Gate gate = parent.buildGate();
            try {
                parent.emit(gate.open());
                if (autoCloseAfter != null) {
                    sleepSeconds(autoCloseAfter);
                    parent.emit(gate.close());
                }
            } finally {
                gate.release();
            }
        }
    }

    @Command(name = "close", description = "Move the gate to the CLOSED position.")
    static final class CloseCommand implements Runnable {

        @ParentCommand
        ServoCommand parent;

        @Override
        public void run() {
            Gate gate = parent.buildGate();
            try {
                parent.emit(gate.close());
            } finally {
                gate.release();
            }
        }
    }

    @Command(name = "angle", description = "Move the gate to an arbitrary angle.")
    static final class AngleCommand implements Runnable {

        @ParentCommand
        ServoCommand parent;

        @Parameters(index = "0", description = "Target angle in degrees.")
        double value;

        @Override
        public void run() {
            Gate gate = parent.buildGate();
            try {
                parent.emit(gate.setAngle(value));
            } finally {
                gate.release();
            }
        }
    }

    @Command(name = "cycle", description = "Open/close repeatedly (self-test / demo).")
    static final class CycleCommand implements Runnable {

        @ParentCommand
        ServoCommand parent;

        @Option(names = "--count", description = "Number of open-close cycles (default: ${DEFAULT-VALUE}).")
        int count = 3;

        @Option(names = "--dwell", description = "Seconds to hold each position (default: ${DEFAULT-VALUE}).")
        double dwellSec = 1.0;

        @Override
        public void run() {
            if (count < 0) {
                throw new IllegalArgumentException("--count must be >= 0");
            }
            if (dwellSec < 0) {
                throw new IllegalArgumentException("--dwell must be >= 0");
            }
            Gate gate = parent.buildGate();
            try {
                for (int i = 0; i < count; i++) {
                    parent.emit(gate.open());
                    sleepSeconds(dwellSec);
                    parent.emit(gate.close());
                    if (dwellSec > 0) {
                        sleepSeconds(dwellSec);
                    }
                }
            } finally {
                gate.release();
            }
        }
    }
}
