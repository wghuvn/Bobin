package com.bobincraft.drone;

import com.bobincraft.combat.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/** Weapon visuals have one owner: the versioned client event. Industrial fire is independent. */
public final class DroneExplosionEffects {
    private DroneExplosionEffects() { }
    public static void play(ServerWorld world, Entity source, ServerPlayerEntity owner, Vec3d center,
                            DroneBlastType type, boolean water, BlockState material, Vec3d surface) {
        var profile = ExplosionVisualProfile.select(type,
                source instanceof com.bobincraft.weapon.heavy.RpgProjectileEntity,
                source instanceof DronePayloadEntity payload && payload.handThrown());
        send(world, owner, source != null ? source.getUuid() : UUID.randomUUID(), center, profile, water, material, surface);
    }
    public static void playDemolitionBlast(ServerWorld world, ServerPlayerEntity owner, UUID eventId, Vec3d center,
                                           boolean water, BlockState material, Vec3d surface) {
        send(world, owner, eventId, center, ExplosionVisualProfile.GROM7_DEMOLITION, water, material, surface);
        // Retain the existing demolition material-debris event (no industrial particles).
        if (!water && material != null && !material.isAir()) {
            Vec3d outward = center.subtract(surface);
            outward = outward.lengthSquared() > .0001 ? outward.normalize() : new Vec3d(0,1,0);
            ImpactDebris.send(world, material, surface.add(outward.multiply(.12)), outward, 2.15f, owner);
        }
    }
    private static void send(ServerWorld world, ServerPlayerEntity owner, UUID id, Vec3d center,
                             ExplosionVisualProfile profile, boolean water, BlockState material, Vec3d surface) {
        CombatEffects.explosion(world, owner, new ExplosionVisualEvent(id, world.getRegistryKey().getValue().toString(),
                profile.id, center.x, center.y, center.z, world.random.nextLong(), water,
                material == null || material.isAir() ? -1 : Block.getRawIdFromState(material),
                surface.x, surface.y, surface.z));
    }
}
