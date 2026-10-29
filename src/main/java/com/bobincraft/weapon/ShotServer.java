package com.bobincraft.weapon;

import com.bobincraft.raid.guard.NpcWeapon;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared server-side ballistic hit, regional injury, headshot and NPC effect handler. */
public final class ShotServer {
    private ShotServer() {}

    public static final float HEADSHOT_MULTIPLIER = 1.5f;

    private static final Map<UUID, Long> MARKED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> CHILL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FROZEN_UNTIL = new ConcurrentHashMap<>();

    public static boolean applyHit(LivingEntity shooter, ServerWorld world, LivingEntity target,
                                   Vec3d hit, float baseDamage, double range, double knockback) {
        if (!target.isAlive() || !Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)
                || !Float.isFinite(baseDamage) || baseDamage <= 0) return false;
        double maxDist = range + 20.0;
        if (shooter.squaredDistanceTo(target) > maxDist * maxDist) return false;

        if (com.bobincraft.vehicle.ArmorGeometry.blocksCrewHit(target, shooter.getEyePos(), hit)) return false;
        boolean nearTarget = target.getBoundingBox().expand(1.5).contains(hit);
        if (!nearTarget) return false;
        boolean headshot = hit.y >= target.getEyeY() - 0.25;
        float amount = headshot ? baseDamage * HEADSHOT_MULTIPLIER : baseDamage;
        // Keep the neutral default NPC profile's existing non-decapitating headshot behavior.
        boolean safeNpcHeadshot = shooter instanceof com.bobincraft.raid.guard.WarzoneGuardEntity guard
                && guard.firearmType() == NpcWeapon.STANDARD;

        target.timeUntilRegen = 0;
        boolean inflicted = target.damage(shooter instanceof PlayerEntity p
                ? shooter.getDamageSources().playerAttack(p) : shooter.getDamageSources().mobAttack(shooter), amount);
        if (inflicted) {
            recordRegionalInjury(target, hit, amount);
            if (headshot && !safeNpcHeadshot && (!target.isAlive() || amount >= 15.0f)) {
                severHeadOnFatalShot(world, target);
            }
        }
        float yaw = shooter.getYaw();
        target.takeKnockback(knockback, -Math.sin(Math.toRadians(yaw)), Math.cos(Math.toRadians(yaw)));
        // Requirement 6: Removed "Убит" / "ХЭДШОТ" actionbar popups and arcade hit sound!
        return true;
    }

    public static void applyNpcEffects(LivingEntity shooter, ServerWorld world, LivingEntity target,
                                       Vec3d hit, NpcWeapon weapon, float damageMultiplier) {
        if (world.isClient || !target.isAlive() || !Float.isFinite(damageMultiplier)) return;
        if (com.bobincraft.vehicle.ArmorGeometry.blocksCrewHit(target, shooter.getEyePos(), hit)) return;
        float mult = MathHelper.clamp(damageMultiplier, BulletPenetration.MIN_DAMAGE_MULTIPLIER, 1.0f);
        long now = world.getTime();
        purge(now);
        float before = target.getHealth();

        switch (weapon) {
            case BREACHER -> {
                double dist = Math.sqrt(shooter.squaredDistanceTo(target));
                float falloff = (float) Math.max(0.3, 1.0 - dist / weapon.range());
                hurt(shooter, target, weapon.damage() * falloff * mult, now);
                target.takeKnockback(weapon.knockback() * mult, -Math.sin(Math.toRadians(shooter.getYaw())), Math.cos(Math.toRadians(shooter.getYaw())));
            }
            case SPOTTER -> {
                hurt(shooter, target, weapon.damage() * mult, now);
                MARKED_UNTIL.put(target.getUuid(), now + 200);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0, false, false), shooter);
                world.spawnParticles(ParticleTypes.WITCH, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.6f, 1.8f);
            }
            case CRYO -> cryo(shooter, world, target, weapon, mult, now);
            case MEDIC -> {
                if (isFriendly(shooter, target)) {
                    target.heal(4.0f * mult);
                    target.removeStatusEffect(StatusEffects.POISON);
                    target.removeStatusEffect(StatusEffects.WITHER);
                    target.removeStatusEffect(StatusEffects.SLOWNESS);
                    target.removeStatusEffect(StatusEffects.WEAKNESS);
                    world.spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(1.0), target.getZ(), 5, 0.3, 0.3, 0.3, 0.02);
                    world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.6f);
                } else {
                    hurt(shooter, target, weapon.damage() * mult, now);
                }
            }
            default -> applyHit(shooter, world, target, hit, weapon.damage() * mult, weapon.range(), weapon.knockback() * mult);
        }
        if (target.getHealth() < before) {
            recordRegionalInjury(target, hit, before - target.getHealth());
        }
    }

    private static void cryo(LivingEntity shooter, ServerWorld world, LivingEntity target, NpcWeapon weapon, float mult, long now) {
        UUID id = target.getUuid();
        long[] chill = CHILL.computeIfAbsent(id, k -> new long[]{0L, 0L});
        if (chill[1] < now) chill[0] = 0L;
        chill[0]++;
        chill[1] = now + 100;

        hurt(shooter, target, weapon.damage() * mult, now);
        target.extinguish();

        if (chill[0] >= 5) {
            chill[0] = 0L;
            FROZEN_UNTIL.put(id, now + 30);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 6, false, false), shooter);
            target.setFrozenTicks(160);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 25, 0.4, 0.6, 0.4, 0.08);
            world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8f, 1.6f);
        } else {
            int amp = (int) Math.min(3L, chill[0] - 1L);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, amp, false, true), shooter);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.5, 0.3, 0.03);
        }
    }

    private static boolean isFriendly(LivingEntity shooter, LivingEntity t) {
        if (!(shooter instanceof ServerPlayerEntity player)) return t == shooter || shooter.isTeammate(t);
        return (t instanceof PlayerEntity && t != shooter)
                || (t instanceof TameableEntity tame && tame.isTamed() && tame.isOwner(player))
                || (t instanceof AbstractHorseEntity horse && horse.isTame())
                || t instanceof MerchantEntity
                || t instanceof IronGolemEntity;
    }

    private static void hurt(LivingEntity shooter, LivingEntity target, float base, long now) {
        float amount = base;
        UUID id = target.getUuid();
        Long marked = MARKED_UNTIL.get(id);
        if (marked != null && marked > now) amount *= 1.3f;
        Long frozen = FROZEN_UNTIL.get(id);
        if (frozen != null && frozen > now) amount *= 1.5f;

        target.timeUntilRegen = 0;
        target.damage(shooter instanceof PlayerEntity p
                ? shooter.getDamageSources().playerAttack(p) : shooter.getDamageSources().mobAttack(shooter), amount);
    }

    private static void purge(long now) {
        if (MARKED_UNTIL.size() > 256) MARKED_UNTIL.values().removeIf(t -> t < now);
        if (FROZEN_UNTIL.size() > 256) FROZEN_UNTIL.values().removeIf(t -> t < now);
        if (CHILL.size() > 256) CHILL.values().removeIf(c -> c[1] < now);
    }

    private static void severHeadOnFatalShot(ServerWorld world, LivingEntity target) {
        if (target instanceof com.bobincraft.raid.guard.WarzoneGuardEntity guard && !guard.isHubGuard()) {
            guard.bobinInjuries().wound(com.bobincraft.combat.InjurySeverity.CRITICAL, true, com.bobincraft.combat.HitRegion.HEAD_SEVERED);
            guard.refreshInjuries(false);
            com.bobincraft.combat.CombatEffects.blood(world, guard, 16);
        } else if (target instanceof ServerPlayerEntity sp && sp instanceof com.bobincraft.combat.InjuryCarrier carrier && !sp.isCreative() && !sp.isSpectator()) {
            carrier.bobinInjuries().wound(com.bobincraft.combat.InjurySeverity.CRITICAL, true, com.bobincraft.combat.HitRegion.HEAD_SEVERED);
            com.bobincraft.combat.InjurySystem.sync(sp);
            com.bobincraft.combat.CombatEffects.blood(world, sp, 16);
        }
    }

    public static void recordRegionalInjury(LivingEntity target, Vec3d hit, float amount) {
        if (target instanceof com.bobincraft.raid.guard.WarzoneGuardEntity guard) {
            double x = hit.x - target.getX(), y = hit.y - target.getY(), z = hit.z - target.getZ();
            var region = guard.isInjuryProne()
                    ? com.bobincraft.combat.GuardInjuryPose.hitRegion(x, y, z, target.getBodyYaw())
                    : com.bobincraft.combat.HitRegion.classify(x, y, z, target.getWidth(), target.getHeight(), target.getBodyYaw(), target.getHeadYaw());
            guard.noteRegionalWound(region, amount);
        }
    }
}
