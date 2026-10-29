package com.bobincraft.weapon.grom7;

import net.minecraft.util.math.MathHelper;

/**
 * Deterministic animation timelines and pose weights for GROM-7 throw, manual surface attachment,
 * remote charge binding confirmation, successful remote activation, and the 6-stage misfire / failed
 * activation animation:
 * <ol>
 *   <li>Character presses the remote button with the thumb ({@code [0..8]}).</li>
 *   <li>A dry mechanical click plays, but no explosion occurs (tick {@code 5}).</li>
 *   <li>The remote's indicator LED blinks red several times ({@code [7..36]}).</li>
 *   <li>Character slightly turns the remote toward themselves, inspecting it ({@code [12..28]}).</li>
 *   <li>Slightly shakes the device ({@code [26..38]}) and presses the button a second time ({@code [39..47]}).</li>
 *   <li>After no reaction, the hand naturally lowers back to the rest position ({@code [48..58]}).</li>
 * </ol>
 */
public final class Grom7Timing {
    public enum Mode {
        THROW(22),
        ATTACH(18),
        REMOTE_SUCCESS(22),
        REMOTE_FAIL(58),
        REMOTE_BIND(12);

        public final int durationTicks;

        Mode(int durationTicks) {
            this.durationTicks = durationTicks;
        }

        public static Mode fromOrdinal(int ord) {
            Mode[] vals = values();
            return ord >= 0 && ord < vals.length ? vals[ord] : THROW;
        }
    }

    // Mode A: Throw timings
    public static final int THROW_WINDUP_TICKS = 9;
    public static final int THROW_RELEASE_TICKS = 13;
    public static final int THROW_END_TICKS = 22;

    // Mode B: Manual surface attachment timings
    public static final int ATTACH_REACH_TICKS = 6;
    public static final int ATTACH_FIX_TICKS = 11;
    public static final int ATTACH_END_TICKS = 18;

    // Remote successful activation timings
    public static final int SUCCESS_RAISE_TICKS = 5;
    public static final int SUCCESS_PRESS_TICKS = 11;
    public static final int SUCCESS_HOLD_TICKS = 15;
    public static final int SUCCESS_END_TICKS = 22;

    // Remote failed activation (6-stage misfire sequence, 58 ticks total)
    public static final int FAIL_PRESS1_TICKS = 8;     // 0..8: Stage 1 & 2 - Press button #1 with thumb + dry click
    public static final int FAIL_PAUSE_TICKS = 13;     // 8..13: Wait — LED starts blinking red
    public static final int FAIL_PRESS2_TICKS = 19;    // Legacy compatibility constant
    public static final int FAIL_INSPECT_TICKS = 27;   // 12..27: Stage 4 - Turn remote toward self to inspect
    public static final int FAIL_TILT_TICKS = 34;      // 26..38: Stage 5a - Shake the device slightly
    public static final int FAIL_SLAP_END_TICKS = 39;  // End of shake window before retry press
    public static final int FAIL_PRESS3_TICKS = 48;    // 39..48: Stage 5b - Second thumb button press + dry click
    public static final int FAIL_END_TICKS = 58;       // 48..58: Stage 6 - Naturally lower hand to rest position

    public static final int[] FAIL_SLAP_SOUND_TICKS = {29, 34};
    public static final int[] FAIL_BUTTON_SOUND_TICKS = {5, 42};
    public static final int FAIL_ERROR_BEEP_TICK = 9;

    private Grom7Timing() {}

    public static float smooth(float value) {
        float t = MathHelper.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public record ThrowPose(float windup, float release, float recovery) {}

    public static ThrowPose throwPose(float age) {
        return new ThrowPose(
                smooth(age / THROW_WINDUP_TICKS),
                smooth((age - THROW_WINDUP_TICKS) / (THROW_RELEASE_TICKS - THROW_WINDUP_TICKS)),
                smooth((age - THROW_RELEASE_TICKS) / (THROW_END_TICKS - THROW_RELEASE_TICKS))
        );
    }

    public record AttachPose(float reach, float press, float recovery) {}

    public static AttachPose attachPose(float age) {
        return new AttachPose(
                smooth(age / ATTACH_REACH_TICKS),
                smooth((age - ATTACH_REACH_TICKS) / (ATTACH_FIX_TICKS - ATTACH_REACH_TICKS)),
                smooth((age - ATTACH_FIX_TICKS) / (ATTACH_END_TICKS - ATTACH_FIX_TICKS))
        );
    }

    public record RemoteSuccessPose(
            float raise,
            float press,
            float lower,
            float anticipation,
            float clickRecoil,
            float followThrough
    ) {
        public RemoteSuccessPose(float raise, float press, float lower) {
            this(raise, press, lower, 0.0f, press, 0.0f);
        }
    }

    public static RemoteSuccessPose successPose(float age) {
        float raise = smooth(age / SUCCESS_RAISE_TICKS);
        float anticipation = pulse(age, 1.5f, 4.5f, 6.8f);
        float press = pulse(age, 4.2f, 7.6f, 13.5f);
        float clickRecoil = pulse(age, 6.2f, 7.8f, 11.2f);
        float followThrough = pulse(age, 8.0f, 12.5f, 17.5f);
        float lower = smooth((age - SUCCESS_HOLD_TICKS) / (SUCCESS_END_TICKS - SUCCESS_HOLD_TICKS));
        return new RemoteSuccessPose(raise, press, lower, anticipation, clickRecoil, followThrough);
    }

    /**
     * Evaluates the 6-stage misfire / failed activation pose at fractional tick {@code age} (0..58):
     * <ul>
     *   <li>{@code raise}: bringing remote up and returning to rest at the end</li>
     *   <li>{@code thumbPress}: thumb button press #1 (ticks 3..8) and retry press #2 (ticks 39..46)</li>
     *   <li>{@code inspect}: turning remote toward player's face to check it (ticks 12..36)</li>
     *   <li>{@code tiltForSlap}: wrist/device shake envelope (ticks 25..38)</li>
     *   <li>{@code slapWave}: oscillatory wrist/device shake impulse (ticks 26..38)</li>
     *   <li>{@code disappoint}: natural downward settling after second press yields no reaction (ticks 47..58)</li>
     * </ul>
     */
    public record RemoteFailPose(
            float raise,
            float thumbPress,
            float inspect,
            float tiltForSlap,
            float slapWave,
            float disappoint
    ) {}

    public static RemoteFailPose failPose(float age) {
        float raiseIn = smooth(age / 4.0f);
        float lowerOut = smooth((age - FAIL_PRESS3_TICKS) / (FAIL_END_TICKS - FAIL_PRESS3_TICKS));
        float raise = raiseIn * (1.0f - lowerOut);

        // Stage 1 & Stage 5b: Two distinct thumb button presses:
        // Press #1 at [2.5..8.0], Retry Press #2 (after shaking) at [39.0..46.5]
        float press1 = pulse(age, 2.5f, 5.2f, 8.2f);
        float press2 = pulse(age, 39.0f, 42.2f, 46.5f) * 1.12f;
        float thumbPress = MathHelper.clamp(Math.max(press1, press2), 0.0f, 1.12f);

        // Stage 4: Slightly turn remote toward self to inspect it [11..38]
        float inspectIn = smooth((age - 11.0f) / 5.0f);
        float inspectOut = smooth((age - 35.0f) / 4.5f);
        float inspect = inspectIn * (1.0f - inspectOut);

        // Stage 5a: Shake envelope [25..38]
        float shakeIn = smooth((age - 25.0f) / 3.0f);
        float shakeOut = smooth((age - 36.0f) / 3.0f);
        float tiltForSlap = shakeIn * (1.0f - shakeOut);

        // Smooth damped wrist & device shake oscillation (2.5 cycles during [25.5..38.0])
        float slapWave = 0.0f;
        if (age >= 25.5f && age <= 38.0f) {
            float u = (age - 25.5f) / 12.5f;
            slapWave = (float) Math.sin(u * Math.PI * 5.0) * tiltForSlap;
        }

        // Stage 6: Natural downward relaxation after no reaction [47..58]
        float disappoint = pulse(age, 47.0f, 52.5f, 58.0f);

        return new RemoteFailPose(raise, thumbPress, inspect, tiltForSlap, slapWave, disappoint);
    }

    /**
     * Returns whether the remote's red error LED is currently illuminated during the misfire sequence.
     * Blinks red 4 times between ticks 7 and 37, and twice more after the retry press at ticks 43..50.
     */
    public static boolean isFailRedLedOn(float age) {
        if (age >= 7.0f && age <= 35.0f) {
            int phase = (int) ((age - 7.0f) / 3.5f);
            return (phase % 2) == 0;
        }
        if (age >= 43.0f && age <= 50.0f) {
            int phase = (int) ((age - 43.0f) / 2.5f);
            return (phase % 2) == 0;
        }
        return false;
    }

    private static float pulse(float t, float start, float peak, float end) {
        if (t <= start || t >= end) return 0.0f;
        if (t <= peak) {
            return smooth((t - start) / Math.max(0.001f, peak - start));
        }
        return 1.0f - smooth((t - peak) / Math.max(0.001f, end - peak));
    }
}
