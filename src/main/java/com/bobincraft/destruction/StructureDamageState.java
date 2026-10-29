package com.bobincraft.destruction;

/**
 * Discrete structural damage states as specified in Section 2.3 and Section 5.
 * <ul>
 *   <li>{@link #INTACT}: 100–71% HP</li>
 *   <li>{@link #DAMAGED}: 70–31% HP (wear/cracks only, NO collapse animation)</li>
 *   <li>{@link #CRITICAL}: 30–1% HP (severe wear/fire/sparks, NO collapse animation)</li>
 *   <li>{@link #DESTROYING}: 0% HP transition state while collapse animation executes</li>
 *   <li>{@link #DESTROYED}: 0% HP permanent collapsed ruin state</li>
 * </ul>
 */
public enum StructureDamageState {
    INTACT(0, "intact", 1.0f, 0.71f),
    DAMAGED(1, "damaged", 0.70f, 0.31f),
    CRITICAL(2, "critical", 0.30f, 0.0001f),
    DESTROYING(3, "destroyed", 0.0f, 0.0f),
    DESTROYED(4, "destroyed", 0.0f, 0.0f);

    public final int index;
    public final String modelName;
    public final float maxFraction;
    public final float minFraction;

    StructureDamageState(int index, String modelName, float maxFraction, float minFraction) {
        this.index = index;
        this.modelName = modelName;
        this.maxFraction = maxFraction;
        this.minFraction = minFraction;
    }

    public boolean isTerminalOrDestroying() {
        return this == DESTROYING || this == DESTROYED;
    }

    /**
     * Normalizes {@link #DESTROYING} to {@link #DESTROYED} for blueprint template lookups
     * and chunk generation snapshots.
     */
    public StructureDamageState templateStage() {
        return this == DESTROYING ? DESTROYED : this;
    }

    public static StructureDamageState fromFraction(float fraction) {
        if (!Float.isFinite(fraction) || fraction <= 0.0f) return DESTROYED;
        if (fraction <= 0.30f) return CRITICAL;
        if (fraction <= 0.70f) return DAMAGED;
        return INTACT;
    }

    public static StructureDamageState fromHealth(float currentHealth, float maxHealth) {
        if (!Float.isFinite(currentHealth) || !Float.isFinite(maxHealth) || maxHealth <= 0.0f || currentHealth <= 0.0f) {
            return DESTROYED;
        }
        return fromFraction(currentHealth / maxHealth);
    }

    public static StructureDamageState byIndex(int idx) {
        if (idx == 3) return DESTROYED; // backwards-compatible with saved byte 3 -> DESTROYED
        for (StructureDamageState state : values()) {
            if (state.index == idx) return state;
        }
        return INTACT;
    }

    public static StructureDamageState byName(String name) {
        if (name == null) return null;
        String lower = name.trim().toLowerCase(java.util.Locale.ROOT);
        if ("destroying".equals(lower)) return DESTROYING;
        if ("destroyed".equals(lower) || "3".equals(lower) || "4".equals(lower)) return DESTROYED;
        for (StructureDamageState state : values()) {
            if (state.modelName.equals(lower) || state.name().toLowerCase(java.util.Locale.ROOT).equals(lower)
                    || Integer.toString(state.index).equals(lower)) {
                return state;
            }
        }
        return null;
    }
}
