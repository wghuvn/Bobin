package com.bobincraft.armament.client.thirdperson;

/** Camera-independent semantic action. No bones, model or view transforms. */
public record PresentationAction(Action action, double started, boolean calibration) {
    public enum Action { IDLE, INSPECT, RAISE, LOWER, MOVE, AIM;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
        public static Action parse(String key) { return valueOf(key.toUpperCase(java.util.Locale.ROOT)); }
        public boolean loops() { return this == IDLE || this == MOVE; }
    }
    public PresentationAction {
        java.util.Objects.requireNonNull(action);
        if (!Double.isFinite(started) || started < 0) throw new IllegalArgumentException("invalid action clock");
    }
}
