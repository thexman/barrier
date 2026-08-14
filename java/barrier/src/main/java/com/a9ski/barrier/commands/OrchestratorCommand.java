package com.a9ski.barrier.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import com.a9ski.barrier.BarrierCli;
import com.a9ski.barrier.camera.Camera;
import com.a9ski.barrier.camera.MockCamera;
import com.a9ski.barrier.camera.RpiCamera;
import com.a9ski.barrier.orchestrator.Allowlist;
import com.a9ski.barrier.orchestrator.Event;
import com.a9ski.barrier.orchestrator.FixedPlateRecognizer;
import com.a9ski.barrier.orchestrator.Orchestrator;
import com.a9ski.barrier.orchestrator.OrchestratorConfig;
import com.a9ski.barrier.orchestrator.PlateRecognizer;
import com.a9ski.barrier.proximity.HcSr04ProximitySensor;
import com.a9ski.barrier.proximity.MockProximitySensor;
import com.a9ski.barrier.proximity.ProximitySensor;
import com.a9ski.barrier.recognizer.RecognizerClient;
import com.a9ski.barrier.servo.Gate;
import com.a9ski.barrier.servo.MockGate;
import com.a9ski.barrier.servo.ServoGate;

/**
 * {@code barrier orchestrator} — end-to-end parking gate loop.
 *
 * <p>Wires proximity, camera, the Python ALPR sidecar (or {@code --mock-plate}),
 * and servo into one state machine.
 */
@Command(
        name = "orchestrator",
        description =
                "Run the barrier loop: proximity trigger, camera capture, ALPR, allowlist check, servo gate.")
public final class OrchestratorCommand implements Runnable {

    @ParentCommand
    private BarrierCli root;

    // behaviour
    @Option(names = "--allowlist", description = "Path to a plates allowlist (one plate per line, '#' for comments).")
    private Path allowlistPath;

    @Option(names = "--allow-all", description = "Bypass the allowlist (testing only).")
    private boolean allowAll;

    @Option(names = "--photos-dir", description = "Directory for captured frames (default: ./photos).")
    private Path photosDir = Path.of("photos");

    @Option(names = "--open-seconds", description = "Hold gate open after a match (default: 8).")
    private double openSeconds = 8.0;

    @Option(names = "--cooldown-seconds", description = "Idle time after each cycle (default: 3).")
    private double cooldownSeconds = 3.0;

    @Option(names = "--debounce-count", description = "Consecutive NEAR reads to trigger (default: 3).")
    private int debounceCount = 3;

    @Option(names = "--capture-count", description = "Frames per trigger (default: 3).")
    private int captureCount = 3;

    @Option(names = "--capture-interval", description = "Delay between captures in seconds (default: 0.15).")
    private double captureInterval = 0.15;

    @Option(names = "--min-ocr-confidence", description = "Minimum OCR confidence (default: 0.6).")
    private double minOcrConfidence = 0.6;

    @Option(names = "--extend-hold-on-near", description = "Extend open-hold while proximity is still NEAR.")
    private boolean extendHoldOnNear;

    @Option(names = "--max-cycles", description = "Stop after N cycles. 0 = forever (default).")
    private int maxCycles;

    @Option(names = "--dry-run", description = "Log every step but never command the servo.")
    private boolean dryRun;

    @Option(names = "--reload-allowlist", negatable = true, fallbackValue = "true",
            description = "Reload allowlist when the file changes (default: true).")
    private boolean reloadAllowlist = true;

    @Option(names = "--recognizer-url", description = "ALPR sidecar base URL (default: http://127.0.0.1:8765).")
    private String recognizerUrl = "http://127.0.0.1:8765";

    // mocks
    @Option(names = "--mock", description = "Enable all hardware mocks (proximity, camera, servo).")
    private boolean mock;

    @Option(names = "--mock-proximity", description = "Use mock proximity sensor.")
    private boolean mockProximity;

    @Option(names = "--mock-camera", description = "Use mock camera.")
    private boolean mockCamera;

    @Option(names = "--mock-servo", description = "Use mock gate.")
    private boolean mockServo;

    @Option(names = "--mock-plate", description = "Stub recognizer to always return this plate text.")
    private String mockPlate;

    // proximity
    @Option(names = "--trigger-pin", description = "BCM trigger pin (default: 23).")
    private int triggerPin = ProximitySensor.DEFAULT_TRIGGER_PIN;

    @Option(names = "--echo-pin", description = "BCM echo pin (default: 24).")
    private int echoPin = ProximitySensor.DEFAULT_ECHO_PIN;

    @Option(names = "--proximity-threshold", description = "NEAR threshold in cm (default: 100).")
    private double proximityThreshold = ProximitySensor.DEFAULT_THRESHOLD_CM;

    @Option(names = "--proximity-max", description = "Max range in cm (default: 400).")
    private double proximityMax = ProximitySensor.DEFAULT_MAX_DISTANCE_CM;

    @Option(names = "--proximity-interval", description = "Proximity poll interval in seconds (default: 0.2).")
    private double proximityInterval = 0.2;

    // servo
    @Option(names = "--servo-pin", description = "BCM servo signal pin (default: 18).")
    private int servoPin = Gate.DEFAULT_PIN;

    @Option(names = "--open-angle", description = "Open angle in degrees (default: 90).")
    private double openAngle = Gate.DEFAULT_OPEN_ANGLE;

    @Option(names = "--close-angle", description = "Close angle in degrees (default: 0).")
    private double closeAngle = Gate.DEFAULT_CLOSE_ANGLE;

    @Option(names = "--servo-min-angle", description = "Servo min angle (default: -90).")
    private double servoMinAngle = Gate.DEFAULT_MIN_ANGLE;

    @Option(names = "--servo-max-angle", description = "Servo max angle (default: 90).")
    private double servoMaxAngle = Gate.DEFAULT_MAX_ANGLE;

    @Option(names = "--min-pulse-ms", description = "Min pulse width in ms (default: 1).")
    private double minPulseMs = Gate.DEFAULT_MIN_PULSE_MS;

    @Option(names = "--max-pulse-ms", description = "Max pulse width in ms (default: 2).")
    private double maxPulseMs = Gate.DEFAULT_MAX_PULSE_MS;

    @Option(names = "--servo-move-time", description = "Servo move wait in seconds (default: 0.5).")
    private double servoMoveTime = Gate.DEFAULT_MOVE_TIME_S;

    // camera
    @Option(names = "--resolution", description = "Camera resolution WxH (default: 2592x1944).")
    private String resolution = Camera.DEFAULT_WIDTH + "x" + Camera.DEFAULT_HEIGHT;

    @Option(names = "--camera-warmup", description = "Camera warm-up seconds (default: 1).")
    private double cameraWarmup = Camera.DEFAULT_WARMUP_S;

    @Option(
            names = "--rotate",
            description = "Pass --rotation DEG to rpicam-still (0, 90, 180 or 270; default: ${DEFAULT-VALUE}).")
    private int rotate = Camera.DEFAULT_ROTATION;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter PRETTY_TS =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void run() {
        if (mock) {
            mockProximity = true;
            mockCamera = true;
            mockServo = true;
        }
        if (!allowAll && allowlistPath == null) {
            throw new IllegalArgumentException("either --allowlist FILE or --allow-all is required");
        }

        Allowlist allowlist = buildAllowlist();
        ProximitySensor proximity = buildProximity();
        Camera camera = buildCamera();
        Gate gate = buildGate();
        PlateRecognizer recognizer = buildRecognizer();

        OrchestratorConfig config = new OrchestratorConfig(
                photosDir,
                captureCount,
                captureInterval,
                proximityInterval,
                debounceCount,
                openSeconds,
                cooldownSeconds,
                extendHoldOnNear,
                minOcrConfidence,
                maxCycles,
                dryRun,
                reloadAllowlist);

        Orchestrator orchestrator = new Orchestrator(config, proximity, camera, gate, recognizer, allowlist);
        Runtime.getRuntime().addShutdownHook(new Thread(orchestrator::requestStop, "barrier-shutdown"));

        try {
            orchestrator.run(this::emit);
        } catch (RuntimeException e) {
            System.err.println("error: " + e.getMessage());
            throw e;
        }
    }

    private Allowlist buildAllowlist() {
        try {
            if (allowAll) {
                return Allowlist.allowAll();
            }
            return Allowlist.fromFile(allowlistPath);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private ProximitySensor buildProximity() {
        if (mockProximity) {
            return new MockProximitySensor(proximityThreshold, proximityMax);
        }
        return new HcSr04ProximitySensor(triggerPin, echoPin, proximityThreshold, proximityMax);
    }

    private Camera buildCamera() {
        int[] res = CameraCommand.parseResolution(resolution);
        Camera.requireValidRotation(rotate);
        if (mockCamera) {
            return new MockCamera(res[0], res[1], rotate);
        }
        return new RpiCamera(res[0], res[1], cameraWarmup, rotate);
    }

    private Gate buildGate() {
        if (mockServo) {
            return new MockGate(openAngle, closeAngle, servoMinAngle, servoMaxAngle, servoMoveTime);
        }
        return new ServoGate(
                servoPin, openAngle, closeAngle, servoMinAngle, servoMaxAngle,
                minPulseMs, maxPulseMs, servoMoveTime);
    }

    private PlateRecognizer buildRecognizer() {
        if (mockPlate != null && !mockPlate.isBlank()) {
            return new FixedPlateRecognizer(mockPlate);
        }
        RecognizerClient client = new RecognizerClient(recognizerUrl);
        return client::recognize;
    }

    private void emit(Event event) {
        boolean asJson = root != null && root.isLogJson();
        if (asJson) {
            try {
                System.out.println(JSON.writeValueAsString(event.toJsonMap()));
            } catch (IOException e) {
                throw new RuntimeException("failed to serialise event", e);
            }
        } else {
            String ts = PRETTY_TS.format(event.timestamp().atZone(java.time.ZoneId.systemDefault()));
            String state = event.state().value().toUpperCase(Locale.ROOT);
            System.out.printf(Locale.ROOT, "[%s] %-12s %s%s%n",
                    ts, state, event.message(), formatDetail(event.detail()));
        }
        System.out.flush();
    }

    private static String formatDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("  (");
        boolean first = true;
        for (Map.Entry<String, Object> e : detail.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            Object v = e.getValue();
            if (v instanceof Double d) {
                sb.append(e.getKey()).append('=').append(String.format(Locale.ROOT, "%g", d));
            } else {
                sb.append(e.getKey()).append('=').append(v);
            }
        }
        sb.append(')');
        return sb.toString();
    }
}
