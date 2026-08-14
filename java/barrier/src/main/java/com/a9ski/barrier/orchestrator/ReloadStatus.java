package com.a9ski.barrier.orchestrator;

/** Result of {@link Allowlist#reloadIfChanged()}. */
public record ReloadStatus(
        boolean changed,
        boolean ok,
        int sizeBefore,
        int sizeAfter,
        String message) {}
