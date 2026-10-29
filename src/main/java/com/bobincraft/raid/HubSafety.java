package com.bobincraft.raid;

import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/** Bounded hub safety rules; no global difficulty, inventory, or world-border changes. */
public final class HubSafety {
    private HubSafety() { }

    public static boolean isHub(ServerWorld world) {
        return world.getChunkManager().getChunkGenerator() instanceof HubChunkGenerator;
    }

    public static void register() {
        HubDesertion.register();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                entity instanceof ServerPlayerEntity && HubDesertion.isStrike(source)
                        || !(entity.getWorld() instanceof ServerWorld world) || !isHub(world)
                        || !(entity instanceof PlayerEntity || HubNpcs.isServiceNpc(entity)
                        || entity instanceof WarzoneGuardEntity guard && guard.isHubGuard()));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isHub(world) && entity instanceof HostileEntity
                    && !(entity instanceof WarzoneGuardEntity guard && guard.isHubGuard())) entity.discard();
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerWorld serverWorld && isHub(serverWorld)
                    && world.getBlockState(hit.getBlockPos()).getBlock() instanceof BedBlock) {
                player.sendMessage(Text.translatable("raid.bobin_craft.hub_bed"), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}
