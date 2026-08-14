package com.a9ski.barrier.orchestrator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * In-memory set of allowed plates with optional hot-reload from a file.
 *
 * <p>Matching is case-insensitive and ignores non-alphanumeric characters.
 */
public final class Allowlist {

    private static final Pattern NORMALIZE = Pattern.compile("[^A-Za-z0-9]");

    private Set<String> plates;
    private final boolean allowAll;
    private final Path source;
    private Long mtimeMillis;

    private Allowlist(Set<String> plates, boolean allowAll, Path source, Long mtimeMillis) {
        this.plates = plates == null ? Set.of() : Set.copyOf(plates);
        this.allowAll = allowAll;
        this.source = source;
        this.mtimeMillis = mtimeMillis;
    }

    public static String normalizePlate(String text) {
        return NORMALIZE.matcher(text).replaceAll("").toUpperCase();
    }

    public static Allowlist fromFile(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
        String raw = Files.readString(path, StandardCharsets.UTF_8);
        Set<String> plates = new HashSet<>();
        for (String line : raw.split("\\R")) {
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }
            plates.add(normalizePlate(stripped));
        }
        if (plates.isEmpty()) {
            throw new IOException("allowlist " + path + " contains no plates");
        }
        return new Allowlist(plates, false, path, attrs.lastModifiedTime().toMillis());
    }

    public static Allowlist allowAll() {
        return new Allowlist(Set.of(), true, null, null);
    }

    public boolean matches(String text) {
        if (allowAll) {
            return true;
        }
        if (text == null) {
            return false;
        }
        return plates.contains(normalizePlate(text));
    }

    public boolean isAllowAll() {
        return allowAll;
    }

    /** Number of distinct normalized plates, or {@code -1} for allow-all. */
    public int size() {
        return allowAll ? -1 : plates.size();
    }

    public ReloadStatus reloadIfChanged() {
        int sizeBefore = plates.size();
        if (allowAll || source == null) {
            return new ReloadStatus(false, true, sizeBefore, sizeBefore, "");
        }
        try {
            if (!Files.exists(source)) {
                return new ReloadStatus(
                        false, false, sizeBefore, sizeBefore,
                        "file missing; keeping " + sizeBefore + " plates");
            }
            long newMtime = Files.getLastModifiedTime(source).toMillis();
            if (mtimeMillis != null && newMtime == mtimeMillis) {
                return new ReloadStatus(false, true, sizeBefore, sizeBefore, "");
            }
            Allowlist fresh = fromFile(source);
            int sizeAfter = fresh.plates.size();
            boolean changed = !plates.equals(fresh.plates);
            this.plates = fresh.plates;
            this.mtimeMillis = fresh.mtimeMillis;
            String message = changed
                    ? "reloaded (" + sizeBefore + " -> " + sizeAfter + " plates)"
                    : "reloaded (no plate changes)";
            return new ReloadStatus(changed, true, sizeBefore, sizeAfter, message);
        } catch (IOException e) {
            return new ReloadStatus(
                    false, false, sizeBefore, sizeBefore,
                    "reload failed: " + e.getMessage() + "; keeping " + sizeBefore + " plates");
        }
    }
}
