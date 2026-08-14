package com.a9ski.barrier;

import com.a9ski.barrier.commands.CameraCommand;
import com.a9ski.barrier.commands.HelloCommand;
import com.a9ski.barrier.commands.OrchestratorCommand;
import com.a9ski.barrier.commands.ProximityCommand;
import com.a9ski.barrier.commands.ServoCommand;
import com.a9ski.barrier.commands.VersionCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * Root command for the {@code barrier} CLI.
 *
 * <p>Every module (proximity, servo, camera, orchestrator, ...) is exposed as a
 * sub-command, so end-to-end operation is a single fat-JAR invocation:
 *
 * <pre>{@code
 * java -jar barrier.jar orchestrator --allowlist plates.txt
 * java -jar barrier.jar proximity   --mock
 * java -jar barrier.jar servo open  --mock
 * java -jar barrier.jar camera      --count 3 --mock /tmp/photos
 * }</pre>
 *
 * <p>The root command itself is a no-op: it prints the help text when invoked
 * without a subcommand. Common flags that every subcommand honors (currently
 * only {@code --log-json} and {@code --verbose}) are declared here with
 * {@link ScopeType#INHERIT} so they show up under each subcommand's help.
 */
@Command(
        name = "barrier",
        mixinStandardHelpOptions = true,
        version = "barrier 0.1.0",
        description =
                "Run the barrier parking-gate: proximity, servo, camera, ALPR "
                        + "and orchestration in a single fat JAR.",
        subcommands = {
                HelloCommand.class,
                VersionCommand.class,
                ProximityCommand.class,
                ServoCommand.class,
                CameraCommand.class,
                OrchestratorCommand.class,
                CommandLine.HelpCommand.class,
        })
public final class BarrierCli implements Runnable {

    @Option(
            names = "--log-json",
            description = "Emit newline-delimited JSON events instead of pretty text.",
            scope = ScopeType.INHERIT)
    private boolean logJson;

    @Option(
            names = {"-v", "--verbose"},
            description = "Verbose logging (DEBUG level).",
            scope = ScopeType.INHERIT)
    private boolean verbose;

    public boolean isLogJson() {
        return logJson;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void run() {
        // No subcommand: show the help and exit non-zero so shell scripts notice.
        new CommandLine(this).usage(System.err);
        System.exit(2);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BarrierCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}
