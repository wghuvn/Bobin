package com.bobincraft.drone;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The reusable handset left behind when a survival vehicle kit is deployed. */
public final class DroneControllerItem extends Item {
    public DroneControllerItem(Settings settings) { super(settings); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        ServerPlayerEntity player = (ServerPlayerEntity) user;
        DroneEntity drone = DroneNetworking.owned(player);
        if (drone == null) {
            user.sendMessage(Text.translatable("drone.bobin_craft.no_drone"), true);
            return TypedActionResult.fail(stack);
        }
        DroneNetworking.begin(player, drone);
        return TypedActionResult.success(stack);
    }

    @Override public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("drone.bobin_craft.tooltip.controller"));
    }
}
