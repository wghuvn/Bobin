package com.bobincraft.drone;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/** Release geometry, carrier-only separation protection and the three-second game fuse. */
public final class DronePayloadSafety {
    public static final int FUSE_TICKS = 60;
    public static final float EXPLOSION_POWER = 2.3f;
    public static final double RELEASE_GAP = 0.18;
    public static final double CARRIER_CLEAR_DISTANCE = EXPLOSION_POWER * 2 + 0.5;
    private DronePayloadSafety() { }

    public static Vec3d releasePosition(Box carrierBody, double payloadHeight) {
        return new Vec3d((carrierBody.minX + carrierBody.maxX) * 0.5,
                carrierBody.minY - payloadHeight - RELEASE_GAP, (carrierBody.minZ + carrierBody.maxZ) * 0.5);
    }

    public static boolean carrierSeparated(boolean alreadySeparated, double squaredDistance) {
        return carrierSeparated(alreadySeparated, squaredDistance, CARRIER_CLEAR_DISTANCE);
    }

    public static boolean carrierSeparated(boolean alreadySeparated, double squaredDistance, double clearDistance) {
        return alreadySeparated || squaredDistance > clearDistance * clearDistance;
    }

    public static boolean protectsCarrier(UUID carrier, UUID candidate, boolean separated) {
        return !separated && carrier != null && carrier.equals(candidate);
    }

    public static int tickFuse(int remaining) { return Math.max(0, remaining - 1); }

    public static boolean detonateFuse(boolean acceptedLiveRelease, int remaining, boolean alreadyDetonated) {
        return acceptedLiveRelease && remaining == 0 && !alreadyDetonated;
    }
}
