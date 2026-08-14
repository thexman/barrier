package com.a9ski.barrier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Build metadata baked into the fat JAR as {@code /build-info.properties}.
 * GitHub Actions writes the real git SHA into that file before {@code mvn package}.
 */
public final class BuildInfo {

    public static final String RESOURCE = "/build-info.properties";

    private final Properties properties;

    private BuildInfo(Properties properties) {
        this.properties = properties;
    }

    public static BuildInfo load() {
        Properties props = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read " + RESOURCE, e);
        }
        return new BuildInfo(props);
    }

    public String gitCommit() {
        return value("git.commit");
    }

    public String gitRef() {
        return value("git.ref");
    }

    public String builtAt() {
        return value("built.at");
    }

    public Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    private String value(String key) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim();
    }

    @Override
    public String toString() {
        return "BuildInfo{git.commit=" + gitCommit() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildInfo that)) {
            return false;
        }
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(properties);
    }
}
