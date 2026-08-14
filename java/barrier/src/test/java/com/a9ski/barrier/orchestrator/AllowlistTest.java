package com.a9ski.barrier.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AllowlistTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizeStripsNonAlphanumerics() {
        assertEquals("AB1234", Allowlist.normalizePlate("ab-12 34"));
    }

    @Test
    void reloadIfChangedUpdatesPlates() throws Exception {
        Path file = tempDir.resolve("plates.txt");
        Files.writeString(file, "AAA111\n");
        Allowlist al = Allowlist.fromFile(file);
        assertTrue(al.matches("aaa111"));

        Thread.sleep(50);
        Files.writeString(file, "AAA111\nBBB222\n");
        ReloadStatus status = al.reloadIfChanged();
        assertTrue(status.ok());
        assertTrue(status.changed());
        assertTrue(al.matches("BBB222"));
    }

    @Test
    void reloadKeepsPlatesOnEmptyFile() throws Exception {
        Path file = tempDir.resolve("plates.txt");
        Files.writeString(file, "AAA111\n");
        Allowlist al = Allowlist.fromFile(file);

        Thread.sleep(50);
        Files.writeString(file, "# only comments\n\n");
        ReloadStatus status = al.reloadIfChanged();
        assertFalse(status.ok());
        assertTrue(al.matches("AAA111"));
    }

    @Test
    void allowAllMatchesEverything() {
        Allowlist al = Allowlist.allowAll();
        assertEquals(-1, al.size());
        assertTrue(al.matches("ANYTHING"));
    }
}
