package com.bobincraft.mixin;

import com.bobincraft.drone.client.DroneClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Decouples {@link WorldRenderer#setupTerrain} from {@code client.player} when a remote drone view is active.
 *
 * <p>In vanilla Minecraft 1.20.1, {@code WorldRenderer.setupTerrain} hardcodes {@code this.client.player.getX()/getY()/getZ()}
 * when calling {@code BuiltChunkStorage.updateCameraPosition(x, z)} even when {@code client.getCameraEntity()} is set to
 * a distant entity. Without redirecting those coordinate reads to the active drone camera entity, {@code BuiltChunkStorage}
 * keeps its render-section grid anchored around the stationary player body, causing {@code enqueueChunksInViewDistance}
 * to return {@code null} and rendering a black void once the drone flies beyond the player's local chunk box.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererDroneMixin {

    @Redirect(
            method = "setupTerrain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D")
    )
    private double bobin$droneTerrainCameraX(ClientPlayerEntity player) {
        Entity view = DroneClient.activeViewEntity(player);
        return view != null ? view.getX() : player.getX();
    }

    @Redirect(
            method = "setupTerrain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D")
    )
    private double bobin$droneTerrainCameraY(ClientPlayerEntity player) {
        Entity view = DroneClient.activeViewEntity(player);
        return view != null ? view.getY() : player.getY();
    }

    @Redirect(
            method = "setupTerrain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D")
    )
    private double bobin$droneTerrainCameraZ(ClientPlayerEntity player) {
        Entity view = DroneClient.activeViewEntity(player);
        return view != null ? view.getZ() : player.getZ();
    }
}
