package com.bobincraft.armament.validation;

import java.util.Collection;
import java.util.Map;

/** Shared structural checks; no game classes, registration or resource loading. */
public final class Checks {
    private Checks() {}

    public static void require(boolean condition, String path, String message) {
        if (!condition) throw new DefinitionValidationException(path, message);
    }

    public static <T> T present(T value, String path) {
        require(value != null, path, "must not be null");
        return value;
    }

    public static void version(int version) {
        require(version == 1, "$.schema_version", "unsupported version " + version + "; expected 1");
    }

    public static void name(String name, String path) {
        present(name, path);
        require(name.length() <= 64 && name.matches("[a-z][a-z0-9_]*"), path,
                "expected a lowercase name matching [a-z][a-z0-9_]*, at most 64 characters");
    }

    public static void size(Collection<?> values, int min, int max, String path) {
        present(values, path);
        require(values.size() >= min && values.size() <= max, path,
                "expected " + min + ".." + max + " entries");
        int index = 0;
        for (Object value : values) present(value, path + "[" + index++ + "]");
    }

    public static void map(Map<?, ?> values, int min, int max, String path) {
        present(values, path);
        require(values.size() >= min && values.size() <= max, path,
                "expected " + min + ".." + max + " entries");
        values.forEach((key, value) -> { present(key, path); present(value, path + "." + key); });
    }

    public static void nonNegative(long value, String path) {
        require(value >= 0, path, "must be non-negative");
    }
}
