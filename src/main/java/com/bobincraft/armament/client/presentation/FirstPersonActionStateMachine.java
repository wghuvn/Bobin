package com.bobincraft.armament.client.presentation;

import com.bobincraft.armament.client.animation.gltf.GltfAnimationController;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Visual action policy only. No Minecraft/Forge types, wall clock or rendering calls. */
public final class FirstPersonActionStateMachine {
    public enum Action {
        IDLE("idle", true, false), INSPECT("inspect", false, true),
        RAISE("raise", false, true), LOWER("lower", false, false), MOVE("move", true, false);

        private final String key;
        private final boolean loop, returnsToIdle;
        Action(String key, boolean loop, boolean returnsToIdle) {
            this.key = key;
            this.loop = loop;
            this.returnsToIdle = returnsToIdle;
        }
        public String key() { return key; }
        public GltfAnimationController.Mode mode() {
            return loop ? GltfAnimationController.Mode.LOOP : GltfAnimationController.Mode.HOLD;
        }
        public static Action parse(String key) {
            for (Action action : values()) if (action.key.equals(key)) return action;
            throw new IllegalArgumentException("unknown first-person animation: " + key);
        }
    }
    public enum Phase { STATIC, PLAYING, HOLDING }

    private final Map<Action, Double> durations;
    private Action action = Action.IDLE;
    private Phase phase = Phase.STATIC;
    private double started;

    public FirstPersonActionStateMachine(Map<Action, Double> durations) {
        Objects.requireNonNull(durations);
        EnumMap<Action, Double> copy = new EnumMap<>(Action.class);
        for (Action action : Action.values()) {
            Double seconds = durations.get(action);
            if (seconds == null || !Double.isFinite(seconds) || seconds <= 0)
                throw new IllegalArgumentException("missing/invalid duration for " + action.key());
            copy.put(action, seconds);
        }
        this.durations = Map.copyOf(copy);
    }
    public void calibration() {
        action = Action.IDLE;
        phase = Phase.STATIC;
        started = 0;
    }
    public void request(Action requested, double now) {
        Objects.requireNonNull(requested);
        time(now);
        action = requested;
        phase = Phase.PLAYING;
        started = now;
    }
    /** Called only by client simulation ticks; true means a new clip must be played. */
    public boolean tick(double now) {
        time(now);
        if (phase == Phase.STATIC || now < started + durations.get(action)) return false;
        if (action.returnsToIdle) {
            request(Action.IDLE, now);
            return true;
        }
        if (!action.loop) phase = Phase.HOLDING;
        return false;
    }
    /** Locomotion yields to actions and never duplicates an explicit MOVE clip. */
    public boolean allowsMovement() { return phase != Phase.STATIC && action == Action.IDLE; }
    public boolean staticIdle() { return phase == Phase.STATIC; }
    public Action action() { return action; }
    public Phase phase() { return phase; }
    public double started() { return started; }

    private static void time(double now) {
        if (!Double.isFinite(now) || now < 0 || now > 1e9)
            throw new IllegalArgumentException("visual time must be finite in 0..1e9 seconds");
    }
}
