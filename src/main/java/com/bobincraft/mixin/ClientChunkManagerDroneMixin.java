package com.bobincraft.mixin;

import com.bobincraft.drone.client.DroneClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Separates remote drone chunk streaming from the operator's protected local chunk safety cache on the client.
 *
 * <p>Why this is architecturally necessary:
 * Vanilla {@link ClientChunkManager} stores chunks in a single fixed-size modulo ring buffer centered on
 * {@code setChunkMapCenter(x, z)}. When the view center moves far away to follow a long-range drone, incoming
 * remote drone chunks share modulo indices with the player's body chunks and overwrite them in the ring buffer.
 * When drone signal is lost or control ends, the player's local chunks would otherwise be {@code null} until the
 * server re-streams them—causing the player to fall into an empty void on return.
 *
 * <p>This mixin maintains a bounded 5x5 protected chunk cache ({@link #bobin$protectedLocalChunks}) around the
 * operator's physical body while drone control or camera return is active:
 * <ul>
 *   <li>Before {@code setChunkMapCenter} shifts the ring buffer away from the player, the 5x5 chunks around the
 *       player's body are snapshotted via {@link ClientChunkManager#getChunk(int, int, ChunkStatus, boolean)}.</li>
 *   <li>Whenever {@code getChunk} returns {@code null} (either while the ring buffer is centered on a distant drone
 *       so client-side player physics ticks, or immediately upon camera return while the server re-streams),
 *       the protected local chunk is returned seamlessly in O(1) time.</li>
 *   <li>{@code unload} packets for protected player-local chunks are ignored while drone control is active.</li>
 * </ul>
 */
@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerDroneMixin {

    @Shadow @Final ClientWorld world;

    @Shadow public abstract WorldChunk getChunk(int x, int z, ChunkStatus leastStatus, boolean create);

    @Unique
    private final Map<Long, WorldChunk> bobin$protectedLocalChunks = new HashMap<>(32);

    @Unique
    private boolean bobin$snapshotting = false;

    @Inject(method = "setChunkMapCenter", at = @At("HEAD"))
    private void bobin$preservePlayerLocalChunksBeforeCenterShift(int x, int z, CallbackInfo ci) {
        if (!DroneClient.isActive() && !DroneClient.isReturningToPlayer()) {
            if (!bobin$protectedLocalChunks.isEmpty()) {
                bobin$protectedLocalChunks.clear();
            }
            return;
        }
        ChunkPos playerChunk = DroneClient.getProtectedPlayerChunk();
        if (playerChunk == null) return;

        bobin$snapshotting = true;
        try {
            // Snapshot any currently loaded chunks in the 5x5 safety zone around the player's physical body
            // before ClientChunkManager shifts its ring-buffer center to (x, z).
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int cx = playerChunk.x + dx;
                    int cz = playerChunk.z + dz;
                    WorldChunk existing = this.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (existing != null) {
                        bobin$protectedLocalChunks.put(ChunkPos.toLong(cx, cz), existing);
                    }
                }
            }
        } finally {
            bobin$snapshotting = false;
        }
    }

    @Inject(
            method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/WorldChunk;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void bobin$resolveProtectedLocalChunk(int x, int z, ChunkStatus leastStatus, boolean create,
                                                  CallbackInfoReturnable<WorldChunk> cir) {
        if (bobin$snapshotting || cir.getReturnValue() != null || bobin$protectedLocalChunks.isEmpty()) {
            return;
        }
        if (!DroneClient.isActive() && !DroneClient.isReturningToPlayer()) {
            bobin$protectedLocalChunks.clear();
            return;
        }
        WorldChunk cached = bobin$protectedLocalChunks.get(ChunkPos.toLong(x, z));
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "unload(II)V", at = @At("HEAD"), cancellable = true)
    private void bobin$preventUnloadingProtectedPlayerChunk(int x, int z, CallbackInfo ci) {
        if ((DroneClient.isActive() || DroneClient.isReturningToPlayer())
                && bobin$protectedLocalChunks.containsKey(ChunkPos.toLong(x, z))) {
            // Never unload the operator's protected 5x5 local safety zone while flying a drone or returning
            ci.cancel();
        }
    }

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void bobin$onLoadChunkFromPacketReturn(int x, int z, PacketByteBuf buf, NbtCompound nbt,
                                                   Consumer<ChunkData.BlockEntityVisitor> consumer,
                                                   CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk loaded = cir.getReturnValue();
        if (loaded == null) return;

        if (DroneClient.isActive() || DroneClient.isReturningToPlayer()) {
            ChunkPos playerChunk = DroneClient.getProtectedPlayerChunk();
            if (playerChunk != null && Math.abs(x - playerChunk.x) <= 2 && Math.abs(z - playerChunk.z) <= 2) {
                bobin$protectedLocalChunks.put(ChunkPos.toLong(x, z), loaded);
            }
            for (WorldChunk chunk : bobin$protectedLocalChunks.values()) {
                this.world.resetChunkColor(chunk.getPos());
            }
        }
    }
}
