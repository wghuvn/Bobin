package com.bobincraft.vehicle;

import net.minecraft.util.math.MathHelper;

/**
 * Deterministic animation timelines and pose weights for PUBG-style hand grenade mechanics:
 * <ul>
 *   <li>Equip & two-handed pin pull ({@code PIN_TICKS = 9})</li>
 *   <li>Interactive LMB windup hold (overhand high trajectory vs underhand low trajectory toggled via RMB)</li>
 *   <li>5-second cooking fuse on {@code R} ({@code FUSE_TICKS = 100}, 5.0s at 20 TPS)</li>
 *   <li>Full-body & arm throw release and follow-through recovery upon LMB release</li>
 * </ul>
 */
public final class GrenadeTiming {
    public static final int PIN_TICKS = 9;
    public static final int WINDUP_TICKS = 18;
    public static final int RELEASE_TICKS = 22;
    public static final int END_TICKS = 32;
    /** Exact 5-second fuse countdown (100 ticks at 20 TPS), active both in hand after pressing R and in flight. */
    public static final int FUSE_TICKS = 100;

    /** Ticks from LMB release until the grenade physically leaves the palm. */
    public static final int THROW_RELEASE_DELAY_TICKS = 4;
    /** Total duration of the throw release + follow-through recovery after LMB is released. */
    public static final int THROW_FOLLOW_THROUGH_TICKS = 14;

    private GrenadeTiming() {}

    public record Pose(float pin, float windup, float release, float recovery) {}

    public static float smooth(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Evaluates pose weights for legacy single-click timeline (0..32 ticks).
     */
    public static Pose pose(float age) {
        return new Pose(
                smooth(age / PIN_TICKS),
                smooth((age - PIN_TICKS) / (WINDUP_TICKS - PIN_TICKS)),
                smooth((age - WINDUP_TICKS) / (RELEASE_TICKS - WINDUP_TICKS)),
                smooth((age - RELEASE_TICKS) / (END_TICKS - RELEASE_TICKS))
        );
    }

    /**
     * Evaluates interactive PUBG-style pose weights given:
     * @param prepAge ticks since LMB windup started
     * @param releaseAge ticks since LMB was released (-1 if still holding windup)
     */
    public static Pose interactivePose(float prepAge, float releaseAge) {
        float pin = smooth(prepAge / PIN_TICKS);
        float windup = smooth((prepAge - PIN_TICKS * 0.65f) / (WINDUP_TICKS - PIN_TICKS * 0.65f));
        if (releaseAge < 0.0f) {
            return new Pose(pin, windup, 0.0f, 0.0f);
        }
        float release = smooth(releaseAge / THROW_RELEASE_DELAY_TICKS);
        float recovery = smooth((releaseAge - THROW_RELEASE_DELAY_TICKS) / (THROW_FOLLOW_THROUGH_TICKS - THROW_RELEASE_DELAY_TICKS));
        return new Pose(1.0f, 1.0f, release, recovery);
    }
}
