package com.bobincraft.combat;

import com.bobincraft.drone.DroneNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-created cosmetic stains and fragment dents. Clients never submit marks or block edits. */
public final class CombatMarks {
    public static final Identifier SYNC = new Identifier("bobin_craft", "combat_marks");
    private static final Map<ServerWorld, CombatMarksState> STATES = new WeakHashMap<>();
    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private record Sent(ServerWorld world, long revision, Vec3d camera, Vec3d player) { }
    private CombatMarks() { }
    private static CombatMarksState state(ServerWorld world) {
        return STATES.computeIfAbsent(world, w -> w.getPersistentStateManager().getOrCreate(
                CombatMarksState::read, CombatMarksState::new, "bobin_craft_combat_marks"));
    }
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { STATES.clear(); SENT.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            if (server.getTicks() % 100 == 0) for (ServerWorld world : server.getWorlds()) {
                state(world).prune(mark -> {
                    if (!world.isChunkLoaded(mark.block())) return false;
                    var block = world.getBlockState(mark.block());
                    return !mark.matches(Registries.BLOCK.getId(block.getBlock()).toString())
                            || !mark.supportedBy(block.getCollisionShape(world, mark.block()).getBoundingBoxes());
                });
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) synchronize(player);
        });
    }
    private static void synchronize(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, SYNC)) return;
        ServerWorld world = player.getServerWorld();
        var state = state(world);
        Vec3d camera = DroneNetworking.viewOrigin(player), body = player.getPos();
        Sent previous = SENT.get(player.getUuid());
        if (previous != null && previous.world == world && previous.revision == state.revision()
                && previous.camera.squaredDistanceTo(camera) < 8 * 8 && previous.player.squaredDistanceTo(body) < 8 * 8) return;
        var nearest = state.marks().stream().filter(mark -> mark.position().squaredDistanceTo(camera) < 128 * 128
                        || mark.position().squaredDistanceTo(body) < 128 * 128)
                .sorted(Comparator.comparingDouble(mark -> Math.min(mark.position().squaredDistanceTo(camera),
                        mark.position().squaredDistanceTo(body)))).limit(SurfaceMark.MAX_VISIBLE).toList();
        var buffer = PacketByteBufs.create();
        buffer.writeIdentifier(world.getRegistryKey().getValue()); buffer.writeVarInt(nearest.size());
        for (var mark : nearest) mark.write(buffer);
        ServerPlayNetworking.send(player, SYNC, buffer);
        SENT.put(player.getUuid(), new Sent(world, state.revision(), camera, body));
    }
    public static void blood(ServerWorld world, LivingEntity target, int amount) {
        int patches = MathHelper.clamp((amount + 4) / 5, 1, 5);
        Vec3d origin = target.getPos().add(0, Math.min(1.2, target.getHeight() * .6), 0);
        for (int i = 0; i < patches; i++) {
            Vec3d start = origin.add((world.random.nextDouble() - .5) * .85, .1,
                    (world.random.nextDouble() - .5) * .85);
            placeRay(world, target, start, start.add(0, -3, 0), SurfaceMark.BLOOD, .12f + world.random.nextFloat() * .15f);
        }
        // A little lateral spray reaches nearby walls, but cannot pass through cover.
        if (amount >= 6) for (int i = 0; i < 2; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            placeRay(world, target, origin, origin.add(Math.cos(angle) * 1.6, -.4, Math.sin(angle) * 1.6),
                    SurfaceMark.BLOOD, .1f + world.random.nextFloat() * .1f);
        }
    }
    private static void placeRay(ServerWorld world, Entity source, Vec3d start, Vec3d end, int kind, float radius) {
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2));
        for (int i = 0; i <= steps; i++) {
            BlockPos sample = BlockPos.ofFloored(start.lerp(end, i / (double) steps));
            if (!world.isInBuildLimit(sample) || !world.isChunkLoaded(sample)) return;
        }
        BlockHitResult hit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, source));
        if (hit.getType() == HitResult.Type.BLOCK) place(world, hit, kind, radius);
    }
    public static void fragment(ServerWorld world, BlockHitResult hit) {
        place(world, hit, SurfaceMark.FRAGMENT, .021f + world.random.nextFloat() * .007f);
    }
    public static void fragmentRay(ServerWorld world, Entity source, Vec3d from, Vec3d to) {
        placeRay(world, source, from, to, SurfaceMark.FRAGMENT, .021f + world.random.nextFloat() * .007f);
    }
    private static void place(ServerWorld world, BlockHitResult hit, int kind, float radius) {
        BlockPos block = hit.getBlockPos();
        if (hit.getType() != HitResult.Type.BLOCK || !world.isChunkLoaded(block)) return;
        var material = world.getBlockState(block);
        Vec3d local = hit.getPos().subtract(Vec3d.of(block));
        local = new Vec3d(MathHelper.clamp(local.x, 0, 1), MathHelper.clamp(local.y, 0, 1), MathHelper.clamp(local.z, 0, 1));
        Direction face = hit.getSide();
        for (Box box : material.getCollisionShape(world, block).getBoundingBoxes()) {
            if (!box.expand(.003).contains(local)) continue;
            double plane = switch (face) {
                case DOWN -> box.minY; case UP -> box.maxY; case NORTH -> box.minZ;
                case SOUTH -> box.maxZ; case WEST -> box.minX; case EAST -> box.maxX;
            };
            double hitPlane = face.getAxis().choose(local.x, local.y, local.z);
            if (Math.abs(plane - hitPlane) > .01) continue;
            boolean x = face.getAxis() == Direction.Axis.X, y = face.getAxis() == Direction.Axis.Y;
            SurfaceMark mark = new SurfaceMark(block.toImmutable(), face, local, radius, world.random.nextInt(4), kind,
                    Registries.BLOCK.getId(material.getBlock()).toString(),
                    (float) Math.max(0, x ? box.minZ : box.minX), (float) Math.max(0, y ? box.minZ : box.minY),
                    (float) Math.min(1, x ? box.maxZ : box.maxX), (float) Math.min(1, y ? box.maxZ : box.maxY));
            state(world).add(mark);
            return;
        }
    }
}
