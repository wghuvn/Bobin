package com.bobincraft.raid;

import java.util.Objects;
import java.util.UUID;

/** A conversation capability is private to one player, target, role and dimension. */
public final class HubDialogueSession {
    public static final int LIFETIME_TICKS = 20 * 60;
    public static final int ACTION_INTERVAL_TICKS = 6;
    public static final double MAX_DISTANCE_SQUARED = 36;
    private final UUID token;
    private final UUID entity;
    private final String dimension;
    private final String role;
    private long expiresAt;
    private long nextActionAt;

    public HubDialogueSession(UUID token, UUID entity, String dimension, String role, long now) {
        this.token = Objects.requireNonNull(token);
        this.entity = Objects.requireNonNull(entity);
        this.dimension = Objects.requireNonNull(dimension);
        this.role = Objects.requireNonNull(role);
        expiresAt = now + LIFETIME_TICKS;
    }

    public UUID token() { return token; }
    public UUID entity() { return entity; }
    public boolean expired(long now) { return now >= expiresAt; }

    public boolean valid(UUID suppliedToken, UUID target, String currentDimension, String currentRole,
                         double distanceSquared, long now) {
        return !expired(now) && token.equals(suppliedToken) && entity.equals(target)
                && dimension.equals(currentDimension) && role.equals(currentRole)
                && Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= MAX_DISTANCE_SQUARED;
    }

    public boolean accept(UUID suppliedToken, UUID target, String currentDimension, String currentRole,
                          double distanceSquared, long now) {
        if (now < nextActionAt || !valid(suppliedToken, target, currentDimension, currentRole, distanceSquared, now)) {
            return false;
        }
        nextActionAt = now + ACTION_INTERVAL_TICKS;
        expiresAt = now + LIFETIME_TICKS;
        return true;
    }
}
