package com.bobincraft.combat;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class CombatEffects {
    public static final Identifier EXPLOSION_VISUAL = new Identifier("bobin_craft", "explosion_visual_v1");
    private CombatEffects() { }

    /** One bounded event per observer. All weapon explosion particles are constructed client-side. */
    public static void explosion(ServerWorld world, ServerPlayerEntity owner, ExplosionVisualEvent event) {
        var center = new Vec3d(event.x(), event.y(), event.z());
        byte[] data = event.encode();
        for (var player : world.getPlayers(p -> p == owner || p.squaredDistanceTo(center) <= 256 * 256
                || com.bobincraft.drone.DroneNetworking.viewOrigin(p).squaredDistanceTo(center) <= 256 * 256)) {
            if (!ServerPlayNetworking.canSend(player, EXPLOSION_VISUAL)) continue;
            var buf = PacketByteBufs.create(); buf.writeBytes(data);
            ServerPlayNetworking.send(player, EXPLOSION_VISUAL, buf);
        }
    }

    public static void blood(ServerWorld world, LivingEntity target, int count) {
        Vec3d pos = target.getPos().add(0, Math.min(1.2, target.getHeight() * 0.65), 0);
        CombatMarks.blood(world, target, count);
        for (ServerPlayerEntity player : world.getPlayers(p -> p.squaredDistanceTo(pos) <= 96 * 96
                || com.bobincraft.drone.DroneNetworking.viewOrigin(p).squaredDistanceTo(pos) <= 96 * 96))
            world.spawnParticles(player, CombatContent.BLOOD_DROPLET, true, pos.x, pos.y, pos.z,
                    Math.max(2, Math.min(48, count * 3)), .22, .12, .22, .14);
    }
}
