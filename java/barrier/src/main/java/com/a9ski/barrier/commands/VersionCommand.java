package com.a9ski.barrier.commands;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import com.a9ski.barrier.BarrierCli;
import com.a9ski.barrier.BuildInfo;

/**
 * Print the git commit SHA (and related build metadata) embedded in the JAR.
 *
 * <p>CI writes {@code build-info.properties} before packaging; local builds may
 * show {@code unknown} until that file is generated.
 *
 * <pre>{@code
 *   $ java -jar barrier.jar version
 *   79a57f1dfecc663fc9f3cbd44a66275708c436e8
 *
 *   $ java -jar barrier.jar --log-json version
 *   {"git.commit":"79a57f1…","git.ref":"main","built.at":"2026-08-26T…"}
 * }</pre>
 */
@Command(
        name = "version",
        description = "Print the git commit SHA embedded in this JAR (from build-info.properties).")
public final class VersionCommand implements Runnable {

    @ParentCommand
    private BarrierCli parent;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public void run() {
        BuildInfo info = BuildInfo.load();
        if (parent != null && parent.isLogJson()) {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("git.commit", info.gitCommit());
            payload.put("git.ref", info.gitRef());
            payload.put("built.at", info.builtAt());
            try {
                System.out.println(JSON.writeValueAsString(payload));
            } catch (Exception e) {
                throw new RuntimeException("failed to serialise version info", e);
            }
            return;
        }

        if (parent != null && parent.isVerbose()) {
            System.out.println("git.commit=" + info.gitCommit());
            System.out.println("git.ref=" + info.gitRef());
            System.out.println("built.at=" + info.builtAt());
        } else {
            System.out.println(info.gitCommit());
        }
    }
}
