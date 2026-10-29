package com.bobincraft.destruction;

/** Structural-only policy. Stable source IDs are supplied by StructureDamageSource.Kind.
 * Entity pressure/shrapnel and ordinary terrain are deliberately outside this decision. */
public final class StructureDamagePolicy {
    private StructureDamagePolicy() {}

    public static boolean allows(String sourceId, String category) {
        boolean protectedTarget = "BRIDGE_SECTION".equals(category) || "REFINERY_TANK".equals(category);
        boolean droppedOrHandPayload = "hand_grenade".equals(sourceId)
                || "drone_payload".equals(sourceId) || "heavy_drone_payload".equals(sourceId);
        return !protectedTarget || !droppedOrHandPayload;
    }
}
