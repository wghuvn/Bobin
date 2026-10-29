package com.bobincraft.drone;

/** Stable item IDs; the aircraft entity IDs and flight families remain compatible. */
public enum DroneVariant {
    FPV("fpv_drone", DroneType.FPV, DroneBlastType.KAMIKAZE, 0xc28c38),
    BOMBER("bomber_drone", DroneType.BOMBER, DroneBlastType.DROPPED_GRENADE, 0x7c8747),
    FPV_FRAGMENTATION("fpv_fragmentation_drone", DroneType.FPV, DroneBlastType.KAMIKAZE_FRAGMENTATION, 0x638d53),
    FPV_BREACH("fpv_breacher_drone", DroneType.FPV, DroneBlastType.KAMIKAZE_BREACH, 0xbf6848),
    BOMBER_HIGH_EXPLOSIVE("bomber_he_drone", DroneType.BOMBER, DroneBlastType.BOMB_HIGH_EXPLOSIVE, 0xc39439),
    BOMBER_FRAGMENTATION("bomber_fragmentation_drone", DroneType.BOMBER, DroneBlastType.BOMB_FRAGMENTATION, 0x588f75),
    HEAVY_BOMBER("heavy_bomber_drone", DroneType.HEAVY, DroneBlastType.HEAVY_STRUCTURAL_BOMB, 0x8f5b3c),
    FP1("fp1_drone", DroneType.FIXED_WING, DroneBlastType.FP1_DEEP_STRIKE, 0x6f7668);

    public final String id;
    public final DroneType type;
    public final DroneBlastType blast;
    public final int colour;

    DroneVariant(String id, DroneType type, DroneBlastType blast, int colour) {
        this.id = id; this.type = type; this.blast = blast; this.colour = colour;
    }

    public static DroneVariant standard(DroneType type) {
        return switch (type) {
            case FPV -> FPV;
            case HEAVY -> HEAVY_BOMBER;
            case FIXED_WING -> FP1;
            default -> BOMBER;
        };
    }
    public static DroneVariant tracked(int index, DroneType type) {
        return index >= 0 && index < values().length && values()[index].type == type ? values()[index] : standard(type);
    }
    public static DroneVariant saved(String id, DroneType type) {
        for (DroneVariant variant : values()) if (variant.id.equals(id) && variant.type == type) return variant;
        return standard(type);
    }
    public String tooltipKey() { return "drone.bobin_craft.variant." + id; }
    public int payloadCapacity() {
        if (type == DroneType.FPV || type == DroneType.FIXED_WING) return 0;
        if (type == DroneType.HEAVY) return 2;
        return this == BOMBER_HIGH_EXPLOSIVE ? 2 : 3;
    }
    public int preparationCost() {
        return (type == DroneType.HEAVY || type == DroneType.FIXED_WING) ? 3 : (type == DroneType.BOMBER ? 2 : 1);
    }
    public String itemKey() { return "item.bobin_craft." + id; }
    public float red() { return (colour >> 16 & 255) / 255f; }
    public float green() { return (colour >> 8 & 255) / 255f; }
    public float blue() { return (colour & 255) / 255f; }
}
