package com.bobincraft.weapon.grom7;

/**
 * Lifecycle state of a GROM-7 remote demolition charge (Section 10.4).
 */
public enum Grom7State {
    /** In ballistic flight after normal right-click throw (Mode A). */
    THROWN("в полёте"),
    /** Falling vertically/ballistically after its supporting block was broken (Section 10.6). */
    FALLING("падает"),
    /** Settled on a horizontal surface after being thrown or falling. */
    RESTING("лежит"),
    /** Firmly attached flush to a block's VoxelShape surface via Shift + RMB (Mode B). */
    ATTACHED("установлен"),
    /** Received a valid radio detonation command and counting down its signal delay. */
    ACTIVATED("активирован"),
    /** Detonated or destroyed. */
    DESTROYED("уничтожен");

    public final String hudName;

    Grom7State(String hudName) {
        this.hudName = hudName;
    }

    public boolean isStaticOnSurface() {
        return this == RESTING || this == ATTACHED;
    }

    public boolean canDetonate() {
        return this != DESTROYED;
    }

    public static Grom7State fromName(String name) {
        if (name == null || name.isEmpty()) return RESTING;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return RESTING;
        }
    }
}
