package com.a9ski.barrier.commands;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import com.a9ski.barrier.BarrierCli;

/**
 * Diagnostic subcommand: prints a snapshot of the JVM / OS the CLI is running
 * on. Handy as a smoke test after copying the fat JAR to a fresh Pi.
 *
 * <pre>{@code
 *   $ java -jar barrier.jar hello
 *   barrier 0.1.0 running on Linux/aarch64 (Raspberry Pi 4 Model B)
 *     java.runtime = OpenJDK 17.0.10+7-Debian-1
 *     user.dir     = /home/pi/barrier
 *     pid          = 4711
 *
 *   $ java -jar barrier.jar --log-json hello
 *   {"cli":"barrier 0.1.0","os":"Linux/aarch64", ...}
 * }</pre>
 */
@Command(
        name = "hello",
        description = "Print JVM / OS diagnostics; useful after copying the JAR to a new machine.")
public final class HelloCommand implements Runnable {

    @ParentCommand
    private BarrierCli parent;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void run() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("cli", "barrier 0.1.0");
        info.put("os", System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
        info.put("os_version", System.getProperty("os.version"));
        info.put("java_runtime", System.getProperty("java.runtime.name")
                + " " + System.getProperty("java.runtime.version"));
        info.put("java_home", System.getProperty("java.home"));
        info.put("user_dir", System.getProperty("user.dir"));
        info.put("pid", ProcessHandle.current().pid());

        if (parent != null && parent.isLogJson()) {
            emitJson(info);
        } else {
            emitPretty(info);
        }
    }

    private static void emitJson(Map<String, Object> info) {
        try {
            System.out.println(JSON.writeValueAsString(info));
        } catch (Exception e) {
            throw new RuntimeException("failed to serialise diagnostics", e);
        }
    }

    private static void emitPretty(Map<String, Object> info) {
        System.out.printf("%s running on %s%n", info.get("cli"), info.get("os"));
        info.forEach((key, value) -> {
            if (!"cli".equals(key) && !"os".equals(key)) {
                System.out.printf("  %-14s = %s%n", key, value);
            }
        });
    }
}
