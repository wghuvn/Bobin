package com.bobincraft.warzone;

import static com.bobincraft.warzone.WarzoneNoise.lerp;
import static com.bobincraft.warzone.WarzoneNoise.smoothstep;

/** Seeded drainage network with asymmetric point-bar shoals, deep cut-bank pools, and natural riffle crossings. */
final class NaturalWaterways {
    private final double cos, sin, phase, offset;
    private final int size;
    private final WarzoneNoise banks;
    private final WarzoneNoise meander;
    private final WarzoneNoise sloughs;
    private final double[] joins, sides, bends, widths;

    NaturalWaterways(long seed, int size, double cos, double sin, double phase, double offset) {
        this.size = size;
        this.cos = cos;
        this.sin = sin;
        this.phase = phase;
        this.offset = offset;
        banks = new WarzoneNoise(WarzoneNoise.mix(seed + 151));
        meander = new WarzoneNoise(WarzoneNoise.mix(seed + 153));
        sloughs = new WarzoneNoise(WarzoneNoise.mix(seed + 157));
        int count = 4 + (int) (WarzoneNoise.mix(seed + 152) & 1);
        joins = new double[count];
        sides = new double[count];
        bends = new double[count];
        widths = new double[count];
        for (int i = 0; i < count; i++) {
            long hash = WarzoneNoise.mix(seed + 160 + i);
            joins[i] = size * (-0.34 + i * 0.68 / (count - 1)
                    + (WarzoneNoise.unit(hash) - 0.5) * 0.055);
            sides[i] = i % 2 == 0 ? 1 : -1;
            bends[i] = (WarzoneNoise.unit(WarzoneNoise.mix(hash + 1)) - 0.5) * 0.65;
            widths[i] = 4.0 + WarzoneNoise.unit(WarzoneNoise.mix(hash + 2)) * 2.8;
        }
    }

    int tributaryCount() { return joins.length; }

    private double center(double u) {
        return offset + Math.sin(u / 210.0 + phase) * 68
                + Math.sin(u / 82.0 - phase * 0.7) * 24
                + Math.sin(u / 37.0 + phase * 1.3) * 7;
    }

    private double derivative(double u) {
        return Math.cos(u / 210.0 + phase) * 68 / 210
                + Math.cos(u / 82.0 - phase * 0.7) * 24 / 82
                + Math.cos(u / 37.0 + phase * 1.3) * 7 / 37;
    }

    /** Writes the dominant reach's geometry and water condition without allocating per column. */
    double carve(int x, int z, double small, double height, WarzoneTerrain.Sample out) {
        // Multi-scale domain warp creates organic bends while staying within the centerline probe window.
        double warpX = meander.fbm(x / 60.0 + 5.1, z / 60.0 - 3.7, 3) * 11.8
                + banks.noise(x / 22.0 - 3.4, z / 22.0 + 6.1) * 1.8;
        double warpZ = meander.fbm(x / 60.0 - 9.4, z / 60.0 + 8.2, 3) * 11.8
                + banks.noise(x / 22.0 + 7.8, z / 22.0 - 4.2) * 1.8;
        double wx = x + warpX, wz = z + warpZ;

        double u = wx * cos + wz * sin, v = -wx * sin + wz * cos;
        double across = v - center(u), slope = derivative(u);
        double distance = Math.abs(across) / Math.sqrt(1 + slope * slope);
        double reachWave = 0.5 + 0.5 * Math.sin(u / 150.0 + phase);
        double reachNoise = meander.fbm(u / 92.0 + 4.2, v / 180.0 - 1.9, 2) * 0.5 + 0.5;
        double reach = reachWave * 0.68 + reachNoise * 0.32;
        double pulse = banks.fbm(u / 45.0, v / 45.0, 2);
        double pocket = Math.max(0.0, sloughs.noise(u / 33.0 - 5.0, v / 33.0 + 3.0));
        double fordWarp = banks.fbm(x / 13.0 + 4.0, z / 13.0 - 6.0, 2) * 6.5;
        // Broad, believable main river channel with wide pooled reaches and narrow rocky riffles.
        double width = 5.8 + reach * 6.8 + pulse * 2.2 + pocket * 1.8;
        double depth = 5.4 + reach * 3.1 + Math.max(0, pulse) * 1.4;
        double ford = ford(u + fordWarp, size * 0.19, 340);
        double flowU = 1, flowV = slope;
        double signedAcross = across;
        WarzoneTerrain.WaterState state = reach > .70 ? WarzoneTerrain.WaterState.SLOW
                : reach < .22 ? WarzoneTerrain.WaterState.RAPIDS : WarzoneTerrain.WaterState.FLOWING;

        for (int i = 0; i < joins.length; i++) {
            // t=0 is the parent's centerline, so every tributary ends inside the main river.
            double t = across * sides[i];
            if (t < -width || t > size * .76) continue;
            double bend = bends[i];
            double branchMeander = meander.fbm(t / 72.0 + i * 3.1, (u - joins[i]) / 130.0 - i * 1.7, 2) * 12.0;
            double branchU = joins[i] + t * bend
                    + Math.sin(t / 78.0 + i * 1.1) * 31
                    + Math.sin(t / 31.0 - i * 0.7) * 9
                    + branchMeander;
            double branchSlope = bend
                    + Math.cos(t / 78.0 + i * 1.1) * 31 / 78
                    + Math.cos(t / 31.0 - i * 0.7) * 9 / 31;
            double d = Math.abs(u - branchU) / Math.sqrt(1 + branchSlope * branchSlope);
            double confluenceFlare = 1.0 + 0.35 * (1.0 - smoothstep(0.0, 24.0, Math.max(0.0, t)));
            double taper = (1 - .32 * smoothstep(size * .10, size * .66, t)) * confluenceFlare;
            double w = widths[i] * taper * (1 + .28 * Math.sin(t / 61.0 + i) + .16 * pulse);
            double f = ford(t + fordWarp * 0.75, 95 + i * 29, 255);
            if (d / w < distance / width) {
                distance = d;
                width = w;
                depth = 4.4 + .95 * Math.sin(t / 73.0 + i);
                ford = f;
                flowU = -branchSlope;
                flowV = -slope * branchSlope - sides[i];
                signedAcross = u - branchU;
                state = w < 3.4 ? WarzoneTerrain.WaterState.RAPIDS : WarzoneTerrain.WaterState.FLOWING;
            }
            // A smaller feeder forks into every other tributary farther upstream.
            if (i % 2 == 0 && t > size * .15) {
                double feederT = t - size * .22;
                double joinMeander = meander.fbm((size * .22) / 72.0 + i * 3.1, -i * 1.7, 2) * 12.0;
                double joinU = joins[i] + size * .22 * bend
                        + Math.sin(size * .22 / 78.0 + i * 1.1) * 31
                        + Math.sin(size * .22 / 31.0 - i * 0.7) * 9
                        + joinMeander;
                double feederWander = banks.fbm(feederT / 45.0 - i * 2.3, u / 90.0, 2) * 7.5;
                double feederU = joinU + feederT * (bend + .58)
                        + Math.sin(feederT / 47.0 + i) * 16
                        + Math.sin(feederT / 21.0) * 5
                        + feederWander;
                double feederSlope = bend + .58
                        + Math.cos(feederT / 47.0 + i) * 16 / 47
                        + Math.cos(feederT / 21.0) * 5 / 21;
                double fd = Math.abs(u - feederU) / Math.sqrt(1 + feederSlope * feederSlope);
                double fw = 2.6 + .55 * Math.sin(feederT / 39.0 + i);
                if (feederT >= 0 && fd / fw < distance / width) {
                    distance = fd;
                    width = fw;
                    depth = 3.8 + .45 * Math.cos(feederT / 41.0);
                    ford = ford(feederT + fordWarp * 0.5, 74, 210);
                    flowU = -feederSlope;
                    flowV = -slope * feederSlope - sides[i];
                    signedAcross = u - feederU;
                    state = WarzoneTerrain.WaterState.FLOWING;
                }
            }
        }

        // Asymmetric bank shaping creates gentler point bars on one side and steeper cut banks on the other.
        double bankBias = banks.fbm(x / 37.0 - 11.0, z / 37.0 + 6.0, 2);
        double sideFactor = signedAcross >= 0 ? (1.0 + bankBias * 0.34) : (1.0 - bankBias * 0.34);
        double roughness = banks.fbm(x / 15.0 + 7, z / 15.0 - 4, 2) * 0.85
                + sloughs.noise(x / 7.0 - 3.0, z / 7.0 + 5.0) * 0.30;
        double effectiveDistance = Math.max(0, distance * sideFactor + roughness * Math.min(2.3, width * .27));

        double valleySpan = width + 56 + Math.abs(bankBias) * 22;
        double valley = 1 - smoothstep(width * .75, valleySpan, effectiveDistance);
        double channel = 1 - smoothstep(width * .42, width + 1.9, effectiveDistance);

        // Smoothly grade the riverbank from water level up into a wider, undulating valley floor.
        boolean pointBarSide = (signedAcross >= 0) == (bankBias >= 0);
        double bankRise = smoothstep(width * .82, valleySpan, effectiveDistance) * (pointBarSide ? 3.2 : 4.2);
        double bank = WarzoneTerrain.SEA_LEVEL + (pointBarSide ? 0.35 : 0.65) + bankRise + small * .38;
        height = lerp(height, Math.min(height, bank), valley * .95);

        // Asymmetric thalweg profile: point-bar side has a broader shallow shelf, cut-bank side drops to deep pool.
        double normalized = Math.min(1.0, effectiveDistance / Math.max(1.0, width));
        double shelfExp = pointBarSide ? (0.48 + 0.18 * Math.abs(bankBias)) : 0.78;
        double bedProfile = Math.pow(1.0 - normalized * normalized, shelfExp);
        double barRipple = sloughs.noise(x / 10.0 + 3.0, z / 10.0 - 2.0) * 0.35 * normalized;
        double localDepth = Math.max(0.8, depth * (0.46 + 0.54 * bedProfile) - barRipple);
        double bed = WarzoneTerrain.SEA_LEVEL - localDepth + ford * (localDepth - .35) + small * .22 * (1 - ford);
        height = lerp(height, Math.min(height, bed), channel);

        out.channel = channel;
        out.valley = Math.max(out.valley, valley);
        out.waterState = state;
        double length = Math.hypot(flowU, flowV);
        double speed = state == WarzoneTerrain.WaterState.RAPIDS ? 1 : state == WarzoneTerrain.WaterState.SLOW ? .18 : .55;
        out.flowX = (flowU * cos - flowV * sin) / length * speed;
        out.flowZ = (flowU * sin + flowV * cos) / length * speed;
        return height;
    }

    private static double ford(double coordinate, double offset, double spacing) {
        double nearest = Math.rint((coordinate - offset) / spacing) * spacing + offset;
        return 1 - smoothstep(10, 28, Math.abs(coordinate - nearest));
    }
}