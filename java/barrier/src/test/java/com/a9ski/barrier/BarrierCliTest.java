package com.a9ski.barrier;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the root CLI wiring: parsing, help text, and dispatch to the
 * {@code hello} subcommand. These do not touch any hardware.
 */
final class BarrierCliTest {

    @Test
    void helpExitsZero() {
        int rc = new CommandLine(new BarrierCli()).execute("--help");
        assertEquals(0, rc);
    }

    @Test
    void versionMentionsBarrier() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int rc = new CommandLine(new BarrierCli())
                .setOut(new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true))
                .execute("--version");
        assertEquals(0, rc);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("barrier"),
                "version output should mention 'barrier' but was: " + out);
    }

    @Test
    void helloPrettyOutputMentionsPid() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            int rc = new CommandLine(new BarrierCli()).execute("hello");
            assertEquals(0, rc);
            String text = out.toString();
            assertTrue(text.contains("pid"), "hello should print pid; was:\n" + text);
            assertTrue(text.contains("java_runtime"),
                    "hello should print java_runtime; was:\n" + text);
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void helloJsonOutputIsValidJson() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            int rc = new CommandLine(new BarrierCli()).execute("--log-json", "hello");
            assertEquals(0, rc);
            String text = out.toString().trim();
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
            assertTrue(text.contains("\"cli\""),
                    "json should contain 'cli' key; was:\n" + text);
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void versionCommandPrintsGitCommit() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            int rc = new CommandLine(new BarrierCli()).execute("version");
            assertEquals(0, rc);
            String text = out.toString(StandardCharsets.UTF_8).trim();
            assertTrue(!text.isEmpty(), "version should print a commit id");
            assertTrue(text.equals("unknown") || text.matches("[0-9a-f]{7,40}"),
                    "version should be 'unknown' or a git sha; was: " + text);
        } finally {
            System.setOut(original);
        }
    }

    @Test
    void versionCommandJsonIncludesGitCommitKey() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            int rc = new CommandLine(new BarrierCli()).execute("--log-json", "version");
            assertEquals(0, rc);
            String text = out.toString(StandardCharsets.UTF_8).trim();
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
            assertTrue(node.has("git.commit"), "json should contain git.commit; was:\n" + text);
        } finally {
            System.setOut(original);
        }
    }
}
