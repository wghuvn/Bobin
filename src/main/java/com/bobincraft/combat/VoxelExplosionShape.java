package com.bobincraft.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Bounded local visual kernels for a rising, lobed dust/smoke cloud, custom demolition blasts, and deep-strike fireballs. */
public final class VoxelExplosionShape {
    public static final int MODE_STANDARD = 0;
    public static final int MODE_REFINERY_PLUME = 1;
    public static final int MODE_MEGA_STRIKE = 2;
    public static final int MODE_DEMOLITION_BLAST = 3;

    private VoxelExplosionShape() { }

    public static List<Puff> create(boolean kamikaze, long seed, int budget) {
        Random rng = new Random(seed);
        int count = Math.max(0, Math.min(kamikaze ? 82 : 54, budget));
        double scale = kamikaze ? 1.15 : 0.82;
        List<Puff> puffs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.3999632297 + rng.nextDouble() * 0.45;
            double radial = Math.sqrt(rng.nextDouble());
            boolean cap = i % 3 != 0;
            double radius = (cap ? 1.4 : 0.7) * radial * scale;
            double height = (cap ? 0.75 + rng.nextDouble() * 0.9 : rng.nextDouble() * 0.7) * scale;
            float size = (float) ((0.11 + rng.nextDouble() * 0.14) * scale);
            puffs.add(new Puff(Math.cos(angle) * radius, height, Math.sin(angle) * radius,
                    Math.cos(angle) * (0.022 + radial * 0.035) * scale,
                    (cap ? 0.032 : 0.056) * scale, Math.sin(angle) * (0.022 + radial * 0.035) * scale,
                    size, 52 + rng.nextInt(38), rng.nextFloat() * (i % 5 == 0 ? 0.32f : 0.14f), rng.nextLong(), MODE_STANDARD));
        }
        return List.copyOf(puffs);
    }

    /**
     * Distinct custom explosion profile for demolition charges (GROM-7) and structural breaches:
     * <ul>
     *   <li>Tier 0: Dense outward ground shock-smoke ring</li>
     *   <li>Tier 1: Sustained rolling fire burst core that cools into thick dark soot</li>
     *   <li>Tier 2: High-volume lingering smoke column</li>
     *   <li>Tier 3: Secondary masonry dust & debris cloud</li>
     * </ul>
     */
    public static List<Puff> createDemolitionBlast(long seed, int budget) {
        Random rng = new Random(seed);
        int count = Math.max(20, Math.min(72, budget * 2 + 12));
        List<Puff> puffs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.3999632297 + rng.nextDouble() * 0.38;
            double radial = Math.sqrt(rng.nextDouble());
            int tier = i % 4;
            if (tier == 0) {
                // Outward high-density shock smoke wave
                double r = 0.55 + radial * 2.45;
                double spd = 0.11 + radial * 0.16;
                float sz = 0.42f + rng.nextFloat() * 0.36f;
                puffs.add(new Puff(Math.cos(angle) * r, 0.20 + rng.nextDouble() * 0.55, Math.sin(angle) * r,
                        Math.cos(angle) * spd, 0.018 + rng.nextDouble() * 0.025, Math.sin(angle) * spd,
                        sz, 78 + rng.nextInt(42), 0.14f + rng.nextFloat() * 0.12f, rng.nextLong(), MODE_DEMOLITION_BLAST));
            } else if (tier == 1) {
                // Sustained central fire burst transitioning to dark smoke
                double r = radial * 1.65;
                double h = 0.35 + rng.nextDouble() * 1.85;
                double spd = 0.045 + radial * 0.075;
                float sz = 0.55f + rng.nextFloat() * 0.45f;
                puffs.add(new Puff(Math.cos(angle) * r, h, Math.sin(angle) * r,
                        Math.cos(angle) * spd, 0.065 + rng.nextDouble() * 0.055, Math.sin(angle) * spd,
                        sz, 68 + rng.nextInt(40), rng.nextFloat() * 0.06f, rng.nextLong(), MODE_DEMOLITION_BLAST));
            } else if (tier == 2) {
                // Lingering rising black/charcoal shock smoke column
                double r = radial * 1.35;
                double h = 0.85 + rng.nextDouble() * 2.75;
                float sz = 0.58f + rng.nextFloat() * 0.48f;
                puffs.add(new Puff(Math.cos(angle) * r, h, Math.sin(angle) * r,
                        Math.cos(angle) * 0.028, 0.085 + rng.nextDouble() * 0.065, Math.sin(angle) * 0.028,
                        sz, 115 + rng.nextInt(65), 0.09f + rng.nextFloat() * 0.08f, rng.nextLong(), MODE_DEMOLITION_BLAST));
            } else {
                // Secondary dust & expanding upper smoke lobe
                double r = 0.45 + radial * 2.15;
                double h = 1.45 + rng.nextDouble() * 3.10;
                double spd = 0.05 + radial * 0.08;
                float sz = 0.64f + rng.nextFloat() * 0.52f;
                puffs.add(new Puff(Math.cos(angle) * r, h, Math.sin(angle) * r,
                        Math.cos(angle) * spd, 0.095 + rng.nextDouble() * 0.075, Math.sin(angle) * spd,
                        sz, 130 + rng.nextInt(70), 0.18f + rng.nextFloat() * 0.14f, rng.nextLong(), MODE_DEMOLITION_BLAST));
            }
        }
        return List.copyOf(puffs);
    }

    /**
     * Multi-phase volumetric fireball, shockwave skirt, rising thermal column, and mushroom cap
     * for the 3x FP-1 deep-strike detonation.
     */
    public static List<Puff> createMegaStrike(long seed, int budget) {
        Random rng = new Random(seed);
        int count = Math.max(24, Math.min(96, budget * 3));
        List<Puff> puffs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.3999632297 + rng.nextDouble() * 0.35;
            double radial = Math.sqrt(rng.nextDouble());
            int tier = i % 4;
            if (tier == 0) {
                // Ground shockwave skirt expanding rapidly outward
                double r = (1.2 + radial * 4.5);
                double spd = 0.18 + radial * 0.28;
                float sz = 0.75f + rng.nextFloat() * 0.65f;
                puffs.add(new Puff(Math.cos(angle) * r, 0.35 + rng.nextDouble() * 0.8, Math.sin(angle) * r,
                        Math.cos(angle) * spd, 0.02 + rng.nextDouble() * 0.03, Math.sin(angle) * spd,
                        sz, 55 + rng.nextInt(35), 0.12f + rng.nextFloat() * 0.15f, rng.nextLong(), MODE_MEGA_STRIKE));
            } else if (tier == 1) {
                // Rolling incandescent base fireball dome
                double r = radial * 3.2;
                double h = 0.6 + rng.nextDouble() * 3.4;
                double spd = 0.06 + radial * 0.12;
                float sz = 1.05f + rng.nextFloat() * 0.95f;
                puffs.add(new Puff(Math.cos(angle) * r, h, Math.sin(angle) * r,
                        Math.cos(angle) * spd, 0.09 + rng.nextDouble() * 0.09, Math.sin(angle) * spd,
                        sz, 75 + rng.nextInt(45), rng.nextFloat() * 0.06f, rng.nextLong(), MODE_MEGA_STRIKE));
            } else if (tier == 2) {
                // Rising central stem of thick black/charcoal smoke
                double r = radial * 1.85;
                double h = 1.5 + rng.nextDouble() * 4.5;
                float sz = 0.95f + rng.nextFloat() * 0.85f;
                puffs.add(new Puff(Math.cos(angle) * r, h, Math.sin(angle) * r,
                        Math.cos(angle) * 0.035, 0.14 + rng.nextDouble() * 0.12, Math.sin(angle) * 0.035,
                        sz, 110 + rng.nextInt(60), 0.08f + rng.nextFloat() * 0.10f, rng.nextLong(), MODE_MEGA_STRIKE));
            } else {
                // High mushroom cloud cap billowing wide at the top
                double r = (0.8 + radial * 3.8);
                double h = 3.2 + rng.nextDouble() * 5.2;
                double spd = 0.07 + radial * 0.11;
                float sz = 1.25f + rng.nextFloat() * 1.15f;
                puffs.add(new Puff(Math.cos(angle) * r, h, Math.sin(angle) * r,
                        Math.cos(angle) * spd, 0.16 + rng.nextDouble() * 0.14, Math.sin(angle) * spd,
                        sz, 130 + rng.nextInt(70), rng.nextFloat() * 0.05f, rng.nextLong(), MODE_MEGA_STRIKE));
            }
        }
        return List.copyOf(puffs);
    }

    /** Short-lived weapon puffs. Never uses the refinery plume mode or its lifetime. */
    public static List<Puff> createWeapon(ExplosionVisualProfile profile, long seed, int budget) {
        Random rng = new Random(seed);
        int count = Math.max(0, Math.min(profile.smokeCount, budget));
        List<Puff> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = i * 2.3999632297 + rng.nextDouble() * .4;
            double radial = Math.sqrt(rng.nextDouble());
            double r = radial * profile.scale * profile.spread;
            boolean hot = i < profile.smokeCount * profile.heatFraction;
            double speed = (.045 + radial * .12) * profile.scale;
            result.add(new Puff(Math.cos(angle) * r, (.1 + rng.nextDouble() * .5) * profile.scale,
                    Math.sin(angle) * r, Math.cos(angle) * speed,
                    (.025 + rng.nextDouble() * .055) * profile.scale, Math.sin(angle) * speed,
                    (float)((.22 + rng.nextDouble() * .22) * profile.scale),
                    profile.smokeLife + rng.nextInt(12), hot ? 0 : .55f, rng.nextLong(), MODE_STANDARD));
        }
        return List.copyOf(result);
    }

    public static float radius(float initial, float progress) {
        float t = Math.max(0, Math.min(1, progress));
        float envelope = t <= 0.65f ? (float) Math.sqrt(t / 0.65f) : (1.0f - (t - 0.65f) * 0.45f);
        return initial * (0.48f + 0.38f * envelope);
    }

    public static float plumeRadius(float initial, float progress, int mode) {
        if (mode == MODE_STANDARD) return radius(initial, progress);
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        if (mode == MODE_REFINERY_PLUME) {
            // Starts tight at the tank breach (rolling fireball), then billows into massive cauliflower smoke lobes as it climbs
            return initial * (0.72f + 2.35f * (float) Math.pow(t, 0.68));
        }
        if (mode == MODE_DEMOLITION_BLAST) {
            float env = t <= 0.70f ? (float) Math.pow(t / 0.70f, 0.58) : (1.0f + (t - 0.70f) * 0.55f);
            return initial * (0.75f + 1.55f * env);
        }
        // MODE_MEGA_STRIKE: rapid fireball expansion into wide mushroom cloud lobes
        float env = t <= 0.75f ? (float) Math.pow(t / 0.75f, 0.55) : (1.0f + (t - 0.75f) * 0.45f);
        return initial * (0.65f + 1.45f * env);
    }

    public record Puff(double x, double y, double z, double vx, double vy, double vz,
                       float radius, int life, float cooling, long seed, int mode) {
        public Puff(double x, double y, double z, double vx, double vy, double vz,
                    float radius, int life, float cooling, long seed) {
            this(x, y, z, vx, vy, vz, radius, life, cooling, seed, MODE_STANDARD);
        }
    }
}
