package com.bobincraft.vehicle;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optional diagnostic recorder for tank hatch geometry, colliders, drone trajectories, and detonation centers.
 * Disabled by default in normal gameplay; can be enabled in diagnostic/test runs.
 */
public final class ArmorHitboxDebug {
    public record TraceSample(Vec3d from, Vec3d to, Vec3d detonationCenter, String outcome, boolean insideCabin) { }

    private static volatile boolean enabled = false;
    private static final List<TraceSample> SAMPLES = Collections.synchronizedList(new ArrayList<>());

    private ArmorHitboxDebug() { }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean state) {
        enabled = state;
        if (!state) SAMPLES.clear();
    }

    public static void record(Vec3d from, Vec3d to, Vec3d detonationCenter, String outcome, boolean insideCabin) {
        if (!enabled) return;
        if (SAMPLES.size() >= 256) SAMPLES.remove(0);
        SAMPLES.add(new TraceSample(from, to, detonationCenter, outcome, insideCabin));
    }

    public static List<TraceSample> samples() {
        synchronized (SAMPLES) {
            return List.copyOf(SAMPLES);
        }
    }

    public static Box openHatchWindowInTurretSpace() {
        return new Box(0.14, 1.14, -0.38, 1.22, 1.30, 0.70);
    }
}
