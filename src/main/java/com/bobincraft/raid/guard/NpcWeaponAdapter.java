package com.bobincraft.raid.guard;

import com.bobincraft.weapon.BulletPenetration;
import com.bobincraft.weapon.ShotServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import java.util.HashSet;
import java.util.Set;

/** Server-authoritative NPC hitscan. Shared caliber traversal, hit validation, damage and effects. */
public final class NpcWeaponAdapter {
    private NpcWeaponAdapter() {}

    public static boolean canFire(WarzoneGuardEntity shooter, LivingEntity target, NpcWeapon weapon) {
        if (!shooter.isHostileTarget(target)
                || shooter.isHubGuard() || !shooter.canUseHands() || shooter.getMainHandStack().isEmpty()
                || shooter.squaredDistanceTo(target) > weapon.range() * weapon.range()) return false;
        Vec3d aim = target.getPos().add(0, target.getHeight() * 0.65, 0);
        return shooter.getWorld().raycast(new RaycastContext(shooter.getEyePos(), aim,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter)).getType() == HitResult.Type.MISS;
    }

    public static void fire(WarzoneGuardEntity shooter, LivingEntity target, NpcWeapon weapon, double spreadDegrees) {
        if (!(shooter.getWorld() instanceof ServerWorld world) || !canFire(shooter, target, weapon)) return;
        Vec3d start = shooter.getEyePos();
        Vec3d aim = target.getPos().add(0, target.getHeight() * 0.65, 0).subtract(start).normalize();
        double spread = Math.tan(Math.toRadians(Math.max(weapon.minSpreadDegrees(), spreadDegrees)));
        double shotRange = Math.min(weapon.range(), start.distanceTo(target.getEyePos()) + 2.0);
        for (int pellet = 0; pellet < weapon.pellets(); pellet++) {
            Vec3d direction = aim.add((shooter.getRandom().nextDouble() - 0.5) * spread * 2,
                    (shooter.getRandom().nextDouble() - 0.5) * spread * 2,
                    (shooter.getRandom().nextDouble() - 0.5) * spread * 2).normalize();
            Vec3d end = BulletPenetration.traceRayWithCaliber(world, start, start.add(direction), shotRange, weapon.caliber(), false);
            Entity hitEntity = null;
            Vec3d hitPoint = end;
            double nearest = start.squaredDistanceTo(end);
            Box search = new Box(start, end).expand(0.3);
            for (Entity candidate : world.getOtherEntities(shooter, search,
                    entity -> (entity instanceof LivingEntity || entity instanceof com.bobincraft.vehicle.ArmoredVehicleEntity) && entity.isAlive() && !entity.isSpectator())) {
                var intersection = com.bobincraft.vehicle.ArmorGeometry.intersection(candidate, start, end, .05);
                if (intersection.isEmpty()) continue;
                double distance = start.squaredDistanceTo(intersection.get());
                if (distance < nearest) {
                    nearest = distance;
                    hitEntity = candidate;
                    hitPoint = intersection.get();
                }
            }
            if (hitEntity instanceof LivingEntity living && shooter.isHostileTarget(living)) {
                float multiplier = penetrationMultiplier(world, start, hitPoint, weapon);
                if (multiplier >= BulletPenetration.MIN_DAMAGE_MULTIPLIER) {
                    if (weapon.hasSpecialEffect()) {
                        ShotServer.applyNpcEffects(shooter, world, living, hitPoint, weapon, multiplier);
                    } else {
                        ShotServer.applyHit(shooter, world, living, hitPoint,
                                weapon.damage() * multiplier, weapon.range(), weapon.knockback() * multiplier);
                    }
                }
            }
            if (hitEntity instanceof com.bobincraft.vehicle.ArmoredVehicleEntity vehicle
                    && vehicle.getFirstPassenger() instanceof LivingEntity occupant && shooter.isHostileTarget(occupant)) {
                vehicle.damage(world.getDamageSources().mobAttack(shooter), weapon.damage() * 0.4f);
            }
            if (hitEntity == null) {
                com.bobincraft.combat.ImpactDebris.shot(world, shooter, end, weapon.range());
            }
            if (pellet == 0) {
                spawnTracer(world, start, hitPoint, weapon);
            }
        }
        var sound = weapon == NpcWeapon.SPOTTER ? SoundEvents.ITEM_CROSSBOW_SHOOT
                : weapon == NpcWeapon.CRYO ? SoundEvents.ENTITY_SNOW_GOLEM_SHOOT : SoundEvents.ENTITY_GENERIC_EXPLODE;
        world.playSound(null, shooter.getBlockPos(), sound, SoundCategory.HOSTILE,
                weapon == NpcWeapon.SPOTTER ? 0.25F : 0.65F,
                weapon == NpcWeapon.SPOTTER ? 1.9F : 1.5F);
        shooter.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        com.bobincraft.raid.WarzoneEncounterManager.onNpcGunfire(shooter);
    }

    public static boolean canFireDrone(WarzoneGuardEntity shooter, com.bobincraft.drone.DroneEntity drone, NpcWeapon weapon) {
        return drone != null && drone.isAlive() && drone.isArmed() && shooter.canUseHands() && !shooter.isHubGuard()
                && !shooter.getMainHandStack().isEmpty()
                && shooter.squaredDistanceTo(drone) <= Math.min(48, weapon.range()) * Math.min(48, weapon.range())
                && shooter.getWorld().raycast(new RaycastContext(shooter.getEyePos(), drone.getBoundingBox().getCenter(),
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter)).getType() == HitResult.Type.MISS;
    }

    /** Drone shots consume the guard's existing finite firearm state and respect intervening allies and walls. */
    public static void fireDrone(WarzoneGuardEntity shooter, com.bobincraft.drone.DroneEntity target, NpcWeapon weapon, double spread) {
        if (!(shooter.getWorld() instanceof ServerWorld world) || !canFireDrone(shooter, target, weapon)) return;
        Vec3d start = shooter.getEyePos(), aim = target.getBoundingBox().getCenter().subtract(start).normalize();
        double cone = Math.tan(Math.toRadians(Math.max(.8, spread)));
        Vec3d direction = aim.add((shooter.getRandom().nextDouble() - .5) * cone, (shooter.getRandom().nextDouble() - .5) * cone, (shooter.getRandom().nextDouble() - .5) * cone).normalize();
        Vec3d end = start.add(direction.multiply(Math.min(48, weapon.range())));
        var wall = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getPos();
        Entity struck = null;
        double closest = start.squaredDistanceTo(end);
        for (var candidate : world.getOtherEntities(shooter, new Box(start, end).expand(.1), e -> e.isAlive() && e.canHit())) {
            var hit = candidate.getBoundingBox().expand(.04).raycast(start, end);
            if (hit.isPresent() && start.squaredDistanceTo(hit.get()) < closest) {
                struck = candidate;
                closest = start.squaredDistanceTo(hit.get());
                end = hit.get();
            }
        }
        if (struck == target) target.damage(world.getDamageSources().mobAttack(shooter), weapon == NpcWeapon.STANDARD ? 2.5f : 4f);
        spawnTracer(world, start, end, weapon);
        world.playSound(null, shooter.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.HOSTILE, .55f, 1.7f);
        shooter.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        com.bobincraft.raid.WarzoneEncounterManager.onNpcGunfire(shooter);
    }

    private static void spawnTracer(ServerWorld world, Vec3d start, Vec3d end, NpcWeapon weapon) {
        int color = weapon.traceColor();
        DustParticleEffect dust = new DustParticleEffect(new Vector3f(((color >> 16) & 255) / 255F,
                ((color >> 8) & 255) / 255F, (color & 255) / 255F), 0.8F);
        for (int i = 1; i <= 6; i++) {
            Vec3d point = start.lerp(end, i / 7.0);
            world.spawnParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    /** Same caliber loss rules as universal ballistics. NPCs never mutate terrain or break player blocks. */
    private static float penetrationMultiplier(ServerWorld world, Vec3d start, Vec3d hit, NpcWeapon weapon) {
        double distance = start.distanceTo(hit);
        Vec3d step = hit.subtract(start).normalize().multiply(0.25);
        Vec3d point = start;
        float multiplier = 1;
        int soil = 0;
        Set<BlockPos> visited = new HashSet<>();
        visited.add(BlockPos.ofFloored(start));
        for (double traveled = 0.25; traveled < distance; traveled += 0.25) {
            point = point.add(step);
            BlockPos pos = BlockPos.ofFloored(point);
            if (!world.isChunkLoaded(pos)) return 0;
            if (!visited.add(pos)) continue;
            var result = BulletPenetration.evaluateBlockStep(world.getBlockState(pos), weapon.caliber(), multiplier, soil);
            if (!result.penetrated()) return 0;
            multiplier = result.newDamageMultiplier();
            soil = result.newSoilBlocksPierced();
        }
        return multiplier;
    }
}
