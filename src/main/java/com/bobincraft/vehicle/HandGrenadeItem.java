package com.bobincraft.vehicle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;

/**
 * PUBG-style Hand Grenade item:
 * <ul>
 *   <li>Hold LMB: Pulls pin with left hand and holds throwing windup (displays ballistic trajectory arc).</li>
 *   <li>Release LMB: Executes full throw animation with arm/torso/camera inertia.</li>
 *   <li>RMB: Toggles between High Trajectory (Overhand) and Low Trajectory (Underhand) throw modes.</li>
 *   <li>R key: Releases safety lever and starts the 5-second (100-tick) cooking timer even while held in hand.</li>
 * </ul>
 */
public final class HandGrenadeItem extends Item {
    public HandGrenadeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        var stack = user.getStackInHand(hand);
        if (!GrenadeThrow.valid(user, hand)) {
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            GrenadeThrow.toggleMode(player, hand);
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
    }

    @Override
    public boolean allowNbtUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }
}
