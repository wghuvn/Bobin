package com.bobincraft.drone;

import com.bobincraft.combat.CombatDamage;
import com.bobincraft.combat.HitRegion;
import com.bobincraft.combat.InjurySeverity;
import com.bobincraft.combat.InjurySystem;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Instant, invisible fragments. No projectile entities, client hit reports, or continuing tasks. */
public final class DroneShrapnel {
    private static final int MAX_TARGETS = 128;
    /** Armor HE shells tear people apart inside this radius (blocks from the burst to the body's nearest point). */
    private static final double HEAVY_RADIUS = 3.6;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    private DroneShrapnel() { }

    public static List<Impact> trace(ServerWorld world, Entity source, Vec3d center, DroneBlastType type, boolean water) {
        double range = type.fragmentRange * (water ? 0.25 : 1);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(range),
                e -> InjurySystem.vulnerable(e));
        targets.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center)));
        if (targets.size() > MAX_TARGETS) targets = new ArrayList<>(targets.subList(0, MAX_TARGETS));
        Map<LivingEntity, Impact> impacts = new LinkedHashMap<>();
        final boolean heavy = type == DroneBlastType.ARMOR_HIGH_EXPLOSIVE && !water;
        double phase = world.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < type.fragmentCount; i++) {
            double y = 1 - 2.0 * (i + 0.5) / type.fragmentCount;
            double ring = Math.sqrt(Math.max(0, 1 - y * y));
            double angle = i * GOLDEN_ANGLE + phase;
            Vec3d end = center.add(Math.cos(angle) * ring * range, y * range, Math.sin(angle) * ring * range);
            // Sample at most 24 cosmetic block impacts, even in an empty field. Fragments remain invisible.
            if (i % Math.max(1, (type.fragmentCount + 23) / 24) == 0)
                com.bobincraft.combat.CombatMarks.fragmentRay(world, source, center, end);
            LivingEntity nearest = null;
            Vec3d point = null;
            double nearestDistance = range * range;
            for (LivingEntity target : targets) {
                Box body = target.getBoundingBox();
                Vec3d hit = body.contains(center) ? center : body.raycast(center, end).orElse(null);
                if (hit == null || com.bobincraft.vehicle.ArmorGeometry.blocksCrewHit(target,center,hit)) continue;
                double distance = hit.squaredDistanceTo(center);
                if (distance < nearestDistance) { nearestDistance = distance; nearest = target; point = hit; }
            }
            if (nearest == null) continue;
            double transmission = BlastRaycast.transmission(world, source, center, point);
            double energy = HitRegion.fragmentEnergy(Math.sqrt(nearestDistance), range, transmission * (water ? 0.4 : 1));
            if (energy < 0.025) continue;
            HitRegion region = nearest instanceof WarzoneGuardEntity guard && guard.isInjuryProne()
                    ? com.bobincraft.combat.GuardInjuryPose.hitRegion(point.x - nearest.getX(), point.y - nearest.getY(),
                            point.z - nearest.getZ(), nearest.getBodyYaw())
                    : HitRegion.classify(point.x - nearest.getX(), point.y - nearest.getY(), point.z - nearest.getZ(),
                            nearest.getWidth(), nearest.getHeight(), nearest.getBodyYaw(), nearest.getHeadYaw());
            Impact impact = impacts.computeIfAbsent(nearest, t -> new Impact(t, heavy));
            impact.closest = Math.min(impact.closest, Math.sqrt(nearestDistance));
            impact.exposure = Math.max(impact.exposure, transmission);
            impact.energy.merge(region, energy, Math::max);
            impact.damage = type.addFragmentDamage(impact.damage, energy);
        }
        if (heavy) {
            // A tank shell must not depend on a golden-angle ray happening to graze a limb: anything standing
            // close to the burst with an open line to it is always an impact candidate.
            for (LivingEntity target : targets) {
                Box box = target.getBoundingBox();
                double dx = Math.max(Math.max(box.minX - center.x, 0), center.x - box.maxX);
                double dy = Math.max(Math.max(box.minY - center.y, 0), center.y - box.maxY);
                double dz = Math.max(Math.max(box.minZ - center.z, 0), center.z - box.maxZ);
                double gap = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (gap > HEAVY_RADIUS) continue;
                Vec3d mid = box.getCenter();
                if (com.bobincraft.vehicle.ArmorGeometry.blocksCrewHit(target, center, mid)) continue;
                double transmission = BlastRaycast.transmission(world, source, center, mid);
                if (transmission <= 0) continue;
                Impact impact = impacts.computeIfAbsent(target, t -> new Impact(t, true));
                impact.closest = Math.min(impact.closest, gap);
                impact.exposure = Math.max(impact.exposure, transmission);
            }
        }
        return new ArrayList<>(impacts.values());
    }

    /** Chance that a hit region comes off. Ordinary fragments need real energy; an armor HE burst nearby does not. */
    private static boolean severs(Impact impact, double regionEnergy, net.minecraft.util.math.random.Random random) {
        if (!impact.heavy || impact.closest >= HEAVY_RADIUS) return regionEnergy >= 0.34 && random.nextFloat() < 0.32f;
        double proximity = 1.0 - impact.closest / HEAVY_RADIUS;
        double chance = (0.30 + proximity * 0.62 + Math.min(1, regionEnergy) * 0.18) * Math.max(0.35, impact.exposure);
        return random.nextDouble() < Math.min(0.97, chance);
    }

    public static void apply(ServerWorld world, Entity source, ServerPlayerEntity owner, List<Impact> impacts) {
        for (Impact impact : impacts) {
            LivingEntity target = impact.target;
            boolean deadGuard = !target.isAlive() && (target instanceof WarzoneGuardEntity guard && !guard.isHubGuard()
                    || target instanceof net.minecraft.entity.player.PlayerEntity player && !player.isSpectator() && !player.isCreative());
            if (!InjurySystem.vulnerable(target) && !deadGuard) continue;
            // Aggregation plus a cooldown-bypassing damage type keeps pressure and fragments distinct,
            // without applying damage once per ray or resetting vanilla's hurt cooldown globally.
            if (!deadGuard && !target.damage(CombatDamage.source(world, CombatDamage.SHRAPNEL, source, owner), impact.damage)) continue;
            InjurySeverity severity = InjurySeverity.NONE;
            boolean face = false;
            int lost = 0;
            for (var hit : impact.energy.entrySet()) {
                InjurySeverity wound = InjurySeverity.fromEnergy(hit.getValue());
                if(target instanceof WarzoneGuardEntity guard&&target.isAlive()&&hit.getValue()>=.12)guard.noteRegionalWound(hit.getKey(),impact.damage);
                if (wound.ordinal() > severity.ordinal()) severity = wound;
                if (hit.getKey() == HitRegion.FACE && wound != InjurySeverity.NONE) face = true;
                int severBit = hit.getKey().severMask();
                if (target instanceof com.bobincraft.combat.InjuryCarrier && severBit != 0
                        && severs(impact, hit.getValue(), world.random)) {
                    lost |= severBit;
                    if (severity.ordinal() < InjurySeverity.SEVERE.ordinal()) severity = InjurySeverity.SEVERE;
                }
            }
            if (impact.heavy && target instanceof com.bobincraft.combat.InjuryCarrier && impact.closest < HEAVY_RADIUS * 0.45) {
                // Point blank: the pressure wave takes limbs the rays missed, and sometimes the head.
                int[] limbMasks = {HitRegion.LEFT_ARM.limbMask, HitRegion.RIGHT_ARM.limbMask,
                        HitRegion.LEFT_LEG.limbMask, HitRegion.RIGHT_LEG.limbMask};
                double near = 1.0 - impact.closest / (HEAVY_RADIUS * 0.45);
                for (int mask : limbMasks) if (world.random.nextDouble() < 0.35 + near * 0.45 * impact.exposure) lost |= mask;
                if (impact.closest < 1.4 && world.random.nextDouble() < 0.30 * impact.exposure) lost |= HitRegion.HEAD_SEVERED;
                if (lost != 0 && severity.ordinal() < InjurySeverity.SEVERE.ordinal()) severity = InjurySeverity.SEVERE;
            }
            if (target.isAlive()) {
                InjurySystem.wound(target, severity, face, lost);
                if ((lost & HitRegion.HEAD_SEVERED) != 0) {
                    target.damage(CombatDamage.source(world, CombatDamage.SHRAPNEL, source, owner), target.getHealth() + 100f);
                }
                if (target instanceof WarzoneGuardEntity && severity == InjurySeverity.NONE && lost == 0)
                    com.bobincraft.combat.CombatEffects.blood(world, target, 7);
            } else if (target instanceof net.minecraft.server.network.ServerPlayerEntity player && player instanceof com.bobincraft.combat.InjuryCarrier carrier) {
                carrier.bobinInjuries().wound(severity, face, lost);
                InjurySystem.sync(player);
                com.bobincraft.combat.CombatEffects.blood(world, player, lost != 0 ? 14 : 7);
            } else if (target instanceof WarzoneGuardEntity guard) {
                if (lost != 0) {
                    guard.bobinInjuries().wound(severity, face, lost);
                    guard.refreshInjuries(false);
                }
                com.bobincraft.combat.CombatEffects.blood(world, guard, lost != 0 ? 14 : 7);
            }
        }
    }

    public static final class Impact {
        private final LivingEntity target;
        private final EnumMap<HitRegion, Double> energy = new EnumMap<>(HitRegion.class);
        private float damage;
        private final boolean heavy;
        private double closest = Double.MAX_VALUE;
        private double exposure;

        private Impact(LivingEntity target, boolean heavy) { this.target = target; this.heavy = heavy; }
    }
}
