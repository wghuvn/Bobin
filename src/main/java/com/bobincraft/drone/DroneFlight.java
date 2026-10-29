package com.bobincraft.drone;

/** Small deterministic game flight model shared with the regression tests. No client positions are trusted. */
public final class DroneFlight {
    public static final double GRAVITY = 0.06;
    public static final double HOVER_THROTTLE = 0.5;
    public static final double FPV_ASSIST_CRUISE = 0.46;
    public static final float FPV_ASSIST_PITCH = 10;
    public static final float FPV_ASSIST_BANK = 12;

    /** Fixed-wing (FP-1) dedicated aerodynamic game constants (scaled for 2x airframe & 5x deep-strike range). */
    public static final double FIXED_WING_MIN_CRUISE = 0.52;
    public static final double FIXED_WING_MAX_CRUISE = 1.35;
    public static final float FIXED_WING_MAX_BANK = 36.0f;
    public static final float FIXED_WING_MAX_PITCH = 28.0f;

    private DroneFlight() { }

    public record Input(float pitch, float roll, float yaw, float throttle) {
        public static final Input IDLE = new Input(0, 0, 0, 0);
        public boolean valid() {
            return Float.isFinite(pitch) && Float.isFinite(roll) && Float.isFinite(yaw)
                    && Float.isFinite(throttle) && Math.abs(pitch) <= 1 && Math.abs(roll) <= 1
                    && Math.abs(yaw) <= 1 && throttle >= 0 && throttle <= 1;
        }
    }

    public record State(double x, double y, double z, float yaw, float pitch, float bank, float throttle) { }

    /**
     * Dedicated fixed-wing flight profile for the long-range FP-1 aircraft (Section 3.3):
     * - Maintains continuous forward motion along heading when armed (cannot hover stationary like a quadcopter).
     * - Smooth coordinated turns where banking the wings drives heading change with realistic roll inertia.
     * - Smooth climb/dive transitions governed by pitch input, throttle, and look pitch.
     */
    public static State fixedWingStep(State state, Input input, float lookPitch, boolean armed, boolean assisted) {
        if (!input.valid()) input = armed ? new Input(0, 0, 0, 0.58f) : Input.IDLE;
        if (!armed) {
            float pitch = state.pitch * 0.88f;
            float bank = state.bank * 0.88f;
            double x = state.x * 0.86;
            double z = state.z * 0.86;
            double y = Math.max(-0.35, state.y * 0.90 - GRAVITY * 0.5);
            return new State(x, y, z, state.yaw, pitch, bank, 0.0f);
        }

        // Throttle controls airspeed between minimum stall-free cruise and dash speed
        float commandedThrottle = (Math.abs(input.throttle - 0.5f) < 0.05f && Math.abs(input.pitch) < 0.05f)
                ? 0.62f
                : (float) clamp(0.32 + input.throttle * 0.68 + Math.max(0.0f, input.pitch) * 0.18f, 0.32, 1.0);
        float throttle = state.throttle + (commandedThrottle - state.throttle) * 0.09f;

        // Visual bank (roll) from roll stick + coordinated yaw input, with aircraft roll inertia
        float targetBank = (float) clamp((input.roll * 0.78f + input.yaw * 0.58f) * FIXED_WING_MAX_BANK,
                -FIXED_WING_MAX_BANK, FIXED_WING_MAX_BANK);
        float bank = state.bank + (targetBank - state.bank) * 0.11f;

        // Coordinated turn rate driven by wing bank angle + rudder authority
        float coordinatedTurn = (bank / FIXED_WING_MAX_BANK) * 2.15f + input.yaw * 0.95f;
        float yaw = wrap(state.yaw + coordinatedTurn);

        // Continuous forward airspeed with smooth longitudinal inertia
        double currentHoriz = Math.hypot(state.x, state.z);
        double targetSpeed = FIXED_WING_MIN_CRUISE + throttle * (FIXED_WING_MAX_CRUISE - FIXED_WING_MIN_CRUISE);
        double airspeed = Math.max(FIXED_WING_MIN_CRUISE, currentHoriz + (targetSpeed - currentHoriz) * 0.12);

        // Smooth climb/dive control from elevator (pitch stick / throttle vertical bias / camera look pitch)
        double lookRad = Math.toRadians(assisted ? clamp(lookPitch, -45.0, 55.0) : 0.0);
        double climbCmd = (input.throttle - 0.5) * 0.36 - Math.sin(lookRad) * 0.36 - input.pitch * 0.18;
        double targetY = clamp(climbCmd, -0.56, 0.36);
        double y = state.y + (targetY - state.y) * 0.13;

        // Nose pitch tracks flight path angle (positive pitch = nose down in Minecraft convention)
        float targetPitch = (float) clamp(-Math.toDegrees(Math.atan2(y, Math.max(0.30, airspeed))) * 0.90,
                -FIXED_WING_MAX_PITCH, FIXED_WING_MAX_PITCH);
        float pitch = state.pitch + (targetPitch - state.pitch) * 0.14f;

        double yr = Math.toRadians(yaw);
        double forwardX = -Math.sin(yr) * airspeed;
        double forwardZ = Math.cos(yr) * airspeed;
        // High directional stability along the wing heading with subtle momentum carry through turns
        double x = state.x * 0.15 + forwardX * 0.85;
        double z = state.z * 0.15 + forwardZ * 0.85;

        double totalSpeed = Math.sqrt(x * x + y * y + z * z);
        if (totalSpeed > DroneType.FIXED_WING.maxSpeed) {
            double scale = DroneType.FIXED_WING.maxSpeed / totalSpeed;
            x *= scale; y *= scale; z *= scale;
        }
        return new State(x, y, z, yaw, pitch, bank, throttle);
    }

    public static State step(DroneType type, State state, Input input, boolean armed, boolean angleMode) {
        if (!input.valid()) input = Input.IDLE;
        float pitch = state.pitch, bank = state.bank;
        float yawRate = type == DroneType.FPV ? 4.8f : type == DroneType.HEAVY ? 1.8f : 2.7f;
        float yaw = wrap(state.yaw + (armed ? input.yaw * yawRate : 0));
        if (armed && angleMode) {
            float limit = type == DroneType.FPV ? 55 : type == DroneType.HEAVY ? 20 : 28;
            float response = type == DroneType.HEAVY ? 0.11f : 0.17f;
            pitch += (input.pitch * limit - pitch) * response;
            bank += (input.roll * limit - bank) * response;
        } else if (armed) {
            float rateScale = type == DroneType.HEAVY ? 0.65f : 1.0f;
            pitch = wrap(pitch + input.pitch * 6.0f * rateScale);
            bank = wrap(bank + input.roll * 7.0f * rateScale);
        } else {
            pitch *= 0.9f;
            bank *= 0.9f;
        }
        float throttleGain = type == DroneType.HEAVY ? 0.18f : 0.24f;
        float throttle = armed ? state.throttle + (input.throttle - state.throttle) * throttleGain : 0;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch), br = Math.toRadians(bank);
        double thrust = throttle * GRAVITY * 2;
        // Thrust follows the tilted body, so altitude must be managed during turns.
        double dragXZ = type == DroneType.HEAVY ? 0.988 : 0.982;
        double x = state.x * dragXZ + (-Math.sin(yr) * Math.sin(pr) * Math.cos(br) - Math.cos(yr) * Math.sin(br)) * thrust;
        double y = state.y * 0.985 + Math.cos(pr) * Math.cos(br) * thrust - GRAVITY;
        double z = state.z * dragXZ + (Math.cos(yr) * Math.sin(pr) * Math.cos(br) - Math.sin(yr) * Math.sin(br)) * thrust;
        double speed = Math.sqrt(x * x + y * y + z * z);
        if (speed > type.maxSpeed) {
            double scale = type.maxSpeed / speed;
            x *= scale; y *= scale; z *= scale;
        }
        return new State(x, y, z, yaw, pitch, bank, throttle);
    }

    /** Flight assist controls velocity and actively returns to a captured hover position on released sticks. */
    public static State assistedStep(DroneType type, State state, Input input, float lookPitch,
                                     double holdErrorX, double holdErrorY, double holdErrorZ) {
        if (!input.valid()) input = new Input(0, 0, 0, 0.5f);
        float yawStep = type == DroneType.FPV ? 6f : type == DroneType.HEAVY ? 2.6f : 4f;
        float yaw = wrap(state.yaw + input.yaw * yawStep);
        double radians = Math.toRadians(yaw);
        double length = Math.max(1, Math.hypot(input.pitch, input.roll));
        double forward = input.pitch / length, right = input.roll / length;
        boolean fpv = type == DroneType.FPV;
        boolean heavy = type == DroneType.HEAVY;
        double cruise = fpv ? FPV_ASSIST_CRUISE : heavy ? 0.29 : 0.38;
        double climb = fpv ? 0.36 : heavy ? 0.21 : 0.25;
        double view = Math.toRadians(fpv ? lookPitch : 0);
        double targetX = (-Math.sin(radians) * forward * Math.cos(view) - Math.cos(radians) * right) * cruise;
        double targetZ = (Math.cos(radians) * forward * Math.cos(view) - Math.sin(radians) * right) * cruise;
        double targetY = (input.throttle * 2 - 1) * climb - Math.sin(view) * forward * cruise;
        if (neutralHorizontal(input)) {
            double holdGain = fpv ? 0.065 : heavy ? 0.14 : 0.18;
            targetX = clamp(holdErrorX * holdGain, -cruise * 0.5, cruise * 0.5);
            targetZ = clamp(holdErrorZ * holdGain, -cruise * 0.5, cruise * 0.5);
        }
        if (neutralVertical(type, input, lookPitch)) targetY = clamp(holdErrorY * (fpv ? 0.065 : 0.22), -climb, climb);
        // Assisted descent is a landing command, so keep it below the armed crash threshold.
        targetY = clamp(targetY, -0.18, climb);
        double horizontalResponse = fpv ? (neutralHorizontal(input) ? 0.32 : 0.18) : (heavy ? 0.14 : 0.22);
        double verticalResponse = fpv ? (neutralVertical(type, input, lookPitch) ? 0.32 : 0.18) : (heavy ? 0.18 : 0.22);
        double dx = (targetX - state.x) * horizontalResponse;
        double dz = (targetZ - state.z) * horizontalResponse;
        // The small FPV builds speed progressively; released controls still brake promptly for precision.
        double acceleration = Math.hypot(dx, dz);
        if (fpv && !neutralHorizontal(input) && acceleration > 0.045) {
            dx *= 0.045 / acceleration;
            dz *= 0.045 / acceleration;
        }
        double x = state.x + dx;
        double y = state.y + (fpv && !neutralVertical(type, input, lookPitch) ? clamp((targetY - state.y) * verticalResponse, -0.04, 0.04)
                : (targetY - state.y) * verticalResponse);
        double z = state.z + dz;
        float targetPitch = input.pitch * (fpv ? FPV_ASSIST_PITCH : heavy ? 8 : 10);
        float targetBank = fpv ? (float) clamp(input.roll * 10 + input.yaw * 4 * Math.min(1, Math.hypot(x, z) / cruise),
                -FPV_ASSIST_BANK, FPV_ASSIST_BANK) : input.roll * (heavy ? 9 : 12);
        float tiltResponse = fpv ? 0.14f : heavy ? 0.13f : 0.2f;
        float pitch = state.pitch + (targetPitch - state.pitch) * tiltResponse;
        float bank = state.bank + (targetBank - state.bank) * tiltResponse;
        float motorLoad = (float) clamp(0.5 + (y - state.y) / (2 * GRAVITY) + Math.abs(pitch) * 0.002, 0.12, 1);
        return new State(x, y, z, yaw, pitch, bank, motorLoad);
    }

    public static boolean neutralHorizontal(Input input) { return Math.abs(input.pitch) < 0.025 && Math.abs(input.roll) < 0.025; }

    /** Capture a nearby stopping point instead of pulling a moving FPV back behind its release position. */
    public static double stoppingOffset(DroneType type, double velocity) {
        return type == DroneType.FPV && Double.isFinite(velocity) ? clamp(velocity * 3.0, -1.4, 1.4) : 0;
    }

    public static boolean neutralVertical(DroneType type, Input input, float lookPitch) {
        return Math.abs(input.throttle - 0.5) < 0.025
                && (type.isCarrier() || Math.abs(input.pitch * Math.sin(Math.toRadians(lookPitch))) < 0.025);
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    public static float signal(double distance, double range, boolean obstructed) {
        if (!Double.isFinite(distance) || !Double.isFinite(range) || range <= 0 || distance >= range) return 0;
        return (float) (Math.max(0, 1 - Math.max(0, distance) / range) * (obstructed ? 0.55 : 1));
    }

    public static boolean fresh(int sequence, int previous) {
        return sequence >= 0 && sequence > previous;
    }

    public static boolean inputTimedOut(long now, long lastInputTick) {
        return now < lastInputTick || now - lastInputTick > 15;
    }

    /** Collision energy uses the blocked velocity, so fast lateral motion cannot turn a soft landing into a crash. */
    public static double impactSpeed(double requestedX, double requestedY, double requestedZ,
                                     double actualX, double actualY, double actualZ) {
        double x = requestedX - actualX, y = requestedY - actualY, z = requestedZ - actualZ;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }
}
