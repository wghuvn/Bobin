package com.bobincraft.armament.api;

import com.bobincraft.armament.validation.Checks;

/** Canonical, explicit namespace:path. Never defaults a missing namespace. */
public record WeaponId(String namespace, String path) implements Comparable<WeaponId> {
    public WeaponId {
        Checks.present(namespace, "$.id.namespace");
        Checks.present(path, "$.id.path");
        Checks.require(namespace.matches("[a-z0-9_-]+(?:[.][a-z0-9_-]+)*"), "$.id.namespace", "invalid namespace; empty dot segments are forbidden");
        Checks.require(path.matches("[a-z0-9_-]+(?:[./][a-z0-9_-]+)*"), "$.id.path",
                "invalid resource path; empty, absolute and traversal segments are forbidden");
        Checks.require(namespace.length() + path.length() + 1 <= 255, "$.id", "must be at most 255 characters");
    }

    public static WeaponId parse(String value) {
        Checks.present(value, "$.id");
        int colon = value.indexOf(':');
        Checks.require(colon > 0 && colon == value.lastIndexOf(':'), "$.id", "expected explicit namespace:path");
        return new WeaponId(value.substring(0, colon), value.substring(colon + 1));
    }

    @Override public String toString() { return namespace + ":" + path; }
    @Override public int compareTo(WeaponId other) { return toString().compareTo(other.toString()); }
}
