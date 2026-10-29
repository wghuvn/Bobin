package com.bobincraft.drone;

import com.bobincraft.combat.CombatDamage;
import com.bobincraft.combat.Concussion;
import com.bobincraft.combat.InjurySystem;
import com.bobincraft.vehicle.ArmorGeometry;
import com.bobincraft.vehicle.ArmoredVehicleEntity;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;

/** Bounded game pressure damage with native cover sampling, armour and physical impulse. */
public final class DronePressure {
    private DronePressure() { }

    public static void apply(ServerWorld world, Entity source, ServerPlayerEntity owner, Vec3d center, DroneBlastType type) {
        apply(world, source, owner, center, type, null);
    }

    public static void apply(ServerWorld world, Entity source, ServerPlayerEntity owner, Vec3d center, DroneBlastType type, com.bobincraft.vehicle.ArmorShockwave.Plan shock) {
        double radius = type.pressureRadius();
        boolean megaStrike = type == DroneBlastType.FP1_DEEP_STRIKE;
        double searchRadius = shock != null ? Math.max(radius, 18.0) : Math.max(radius, megaStrike ? 36.0 : 6.0);
        var damage = CombatDamage.source(world, CombatDamage.DRONE_BLAST, source, owner);
        for (Entity target : world.getOtherEntities(source, new Box(center, center).expand(searchRadius), e -> e.isAlive() && !e.isSpectator())) {
            if (target instanceof LivingEntity living && !InjurySystem.vulnerable(living)) continue;
            if (target instanceof ArmoredVehicleEntity armor) {
                boolean internal = ArmorGeometry.insideCabin(armor, center);
                double distance = internal ? 0.0 : ArmorGeometry.distanceToVehicle(armor, center);
                if (!internal && distance >= radius) continue;
                double exposure = internal ? 1.0 : Math.max(0.65, Explosion.getExposure(center, armor));
                float amount = internal
                        ? Math.max(25.0f, type.pressureDamage * 2.2f)
                        : type.pressureDamageAt(distance, exposure);
                if (amount > 0) armor.damage(damage, amount);
                continue;
            }
            double distance = target.getPos().distanceTo(center);
            double exposure = shock == null ? Explosion.getExposure(center, target) : com.bobincraft.vehicle.ArmorShockwave.exposure(world, source, target, shock);
            ArmoredVehicleEntity enclosing = ArmorGeometry.enclosingVehicle(target);
            boolean internalCrewBlast = false;
            if (enclosing != null && enclosing.armorHealth() > 0) {
                if (ArmorGeometry.insideCabin(enclosing, center)) {
                    internalCrewBlast = true;
                    exposure = 1.0;
                    distance = Math.min(distance, 0.25);
                } else {
                    exposure *= ArmorGeometry.crewExposure(enclosing, center, target);
                }
            }
            if (shock != null || megaStrike) {
                // FP-1 3x deep strike applies concussion across a 2x wider radius (up to 36 blocks)
                Concussion.apply(target, megaStrike ? distance * 0.5 : distance, Math.max(0.35, exposure));
            }
            if (!internalCrewBlast && distance >= radius) continue;
            float amount = type.pressureDamageAt(distance, exposure);
            if (internalCrewBlast) {
                // Confined blast inside the steel fighting compartment amplifies overpressure on the crew.
                amount = Math.max(amount * 1.85f, 16.0f);
            }
            if (amount <= 0 || !target.damage(damage, amount)) continue;
            double impulse = (1 - Math.min(distance, radius) / radius) * exposure * (megaStrike ? 2.1 : 1.0);
            if (target instanceof LivingEntity living) impulse = ProtectionEnchantment.transformExplosionKnockback(living, impulse);
            Vec3d direction = target.getBoundingBox().getCenter().subtract(center).normalize().multiply(impulse * .7);
            if (megaStrike) {
                direction = direction.add(0, Math.min(0.65, impulse * 0.28), 0);
            }
            target.addVelocity(direction.x, direction.y, direction.z);
            target.velocityModified = true;
        }
        if (megaStrike) {
            world.playSound(null, center.x, center.y, center.z, DroneContent.FP1_MEGA_EXPLOSION_SOUND,
                    SoundCategory.BLOCKS, 7.5f, 0.88f + world.random.nextFloat() * 0.14f);
            world.playSound(null, center.x, center.y, center.z, com.bobincraft.vehicle.ArmorContent.IMPACT,
                    SoundCategory.BLOCKS, 5.5f, 0.65f + world.random.nextFloat() * 0.15f);
        } else {
            world.playSound(null, center.x, center.y, center.z,
                    type == DroneBlastType.ARMOR_HIGH_EXPLOSIVE ? com.bobincraft.vehicle.ArmorContent.IMPACT : SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.BLOCKS, 4, .85f + world.random.nextFloat() * .2f);
        }
        world.emitGameEvent(source, net.minecraft.world.event.GameEvent.EXPLODE, center);
    }
}
