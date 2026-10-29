package com.bobincraft.destruction;

import com.bobincraft.drone.DroneBlastType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Authoritative server-side description of a structural damage event.
 * Supports deduplication by eventId so a single area explosion only damages each structure once.
 */
public final class StructureDamageSource {
    public enum Kind {
        ARMOR_SHELL("armor_shell", 1.35f, true),
        DRONE_PAYLOAD("drone_payload", 1.0f, true),
        HEAVY_DRONE_PAYLOAD("heavy_drone_payload", 1.65f, true),
        FIXED_WING_STRIKE("fixed_wing_strike", 1.65f, true),
        RPG_PROJECTILE("rpg_projectile", 1.45f, true),
        KAMIKAZE_DRONE("kamikaze_drone", 1.10f, true),
        DEMOLITION_CHARGE("demolition_charge", 1.0f, true),
        HAND_GRENADE("hand_grenade", 0.55f, false),
        SMALL_ARMS("small_arms", 0.05f, false),
        FIRE_TICK("fire_tick", 1.0f, false),
        ADMIN_COMMAND("admin_command", 1.0f, false),
        GENERIC_EXPLOSION("generic_explosion", 1.0f, true);

        public final String id;
        public final float structuralMultiplier;
        public final boolean canIgnite;

        Kind(String id, float structuralMultiplier, boolean canIgnite) {
            this.id = id;
            this.structuralMultiplier = structuralMultiplier;
            this.canIgnite = canIgnite;
        }
    }

    private final UUID eventId;
    private final Kind kind;
    private final UUID attackerUuid;
    private final Vec3d origin;
    private final double effectRadius;
    private Vec3d surfaceNormal;
    public StructureDamageSource withSurfaceNormal(Vec3d normal){if(normal!=null&&Double.isFinite(normal.lengthSquared())&&normal.lengthSquared()>1e-8)surfaceNormal=normal.normalize();return this;}
    public Vec3d surfaceNormal(){return surfaceNormal;}

    public StructureDamageSource(UUID eventId, Kind kind, UUID attackerUuid, Vec3d origin, double effectRadius) {
        this.eventId = eventId == null ? UUID.randomUUID() : eventId;
        this.kind = kind == null ? Kind.GENERIC_EXPLOSION : kind;
        this.attackerUuid = attackerUuid;
        this.origin = origin == null ? Vec3d.ZERO : origin;
        this.effectRadius = Double.isFinite(effectRadius) && effectRadius >= 0 ? effectRadius : 0.0;
    }

    public static StructureDamageSource direct(Kind kind, UUID attackerUuid, Vec3d impactPos) {
        return new StructureDamageSource(UUID.randomUUID(), kind, attackerUuid, impactPos, 2.5);
    }

    public static StructureDamageSource explosion(Kind kind, UUID attackerUuid, Vec3d center, double radius) {
        return new StructureDamageSource(UUID.randomUUID(), kind, attackerUuid, center, radius);
    }

    public static StructureDamageSource fromDroneBlast(Entity source, UUID ownerUuid, DroneBlastType blastType, Vec3d center) {
        // GROM-7 reuses the HE terrain envelope; its actual source must not become a dropped payload.
        Kind kind = source instanceof com.bobincraft.weapon.grom7.Grom7ChargeEntity ? Kind.DEMOLITION_CHARGE : switch (blastType) {
            case ARMOR_HIGH_EXPLOSIVE -> Kind.ARMOR_SHELL;
            case KAMIKAZE, KAMIKAZE_FRAGMENTATION, KAMIKAZE_BREACH -> Kind.KAMIKAZE_DRONE;
            case DROPPED_GRENADE -> Kind.HAND_GRENADE;
            case BOMB_HIGH_EXPLOSIVE, BOMB_FRAGMENTATION -> Kind.DRONE_PAYLOAD;
            case FP1_DEEP_STRIKE -> Kind.FIXED_WING_STRIKE;
            default -> {
                if (blastType.name().contains("HEAVY")) yield Kind.HEAVY_DRONE_PAYLOAD;
                if (blastType.name().contains("RPG")) yield Kind.RPG_PROJECTILE;
                yield Kind.GENERIC_EXPLOSION;
            }
        };
        return new StructureDamageSource(UUID.randomUUID(), kind, ownerUuid, center, Math.max(3.5, blastType.pressureRadius()));
    }

    public static float baseStructuralDamage(DroneBlastType blastType) {
        return switch (blastType) {
            case ARMOR_HIGH_EXPLOSIVE -> 340.0f;
            case KAMIKAZE -> 220.0f;
            case KAMIKAZE_BREACH -> 260.0f;
            case KAMIKAZE_FRAGMENTATION -> 110.0f;
            case BOMB_HIGH_EXPLOSIVE -> 280.0f;
            case BOMB_FRAGMENTATION -> 120.0f;
            case DROPPED_GRENADE -> 85.0f;
            case FP1_DEEP_STRIKE -> 580.0f;
            default -> {
                if (blastType.name().contains("HEAVY")) yield 460.0f;
                if (blastType.name().contains("RPG")) yield 360.0f;
                yield 180.0f;
            }
        };
    }

    public UUID eventId() { return eventId; }
    public Kind kind() { return kind; }
    public UUID attackerUuid() { return attackerUuid; }
    public Vec3d origin() { return origin; }
    public double effectRadius() { return effectRadius; }
}
