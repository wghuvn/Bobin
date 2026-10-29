package com.bobincraft.weapon.grom7;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Throwable & attachable demolition charge item "ГРОМ-7" (GROM-7).
 * <ul>
 *   <li>Normal RMB: initiates 0.55s throw wind-up and launches a ballistic charge.</li>
 *   <li>Shift + RMB: raycasts up to 3.85m and begins a 0.65s manual surface attachment sequence.</li>
 * </ul>
 */
public final class Grom7ChargeItem extends Item {

    public Grom7ChargeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user == null) {
            return TypedActionResult.pass(ItemStack.EMPTY);
        }
        ItemStack stack = user.getStackInHand(hand);
        if (world == null || !Grom7Manager.canUse(user, hand)) {
            return TypedActionResult.fail(stack);
        }

        if (user.isSneaking()) {
            // Mode B: Manual surface attachment (Shift + RMB)
            // Server independently computes & validates on ServerWorld; Client performs local preview check.
            if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
                if (!Grom7Manager.beginAttach(serverPlayer, hand)) {
                    return TypedActionResult.fail(stack);
                }
                return TypedActionResult.consume(stack);
            }
            Grom7Placement.PlacementResult preview = Grom7Placement.raycastAndEvaluate(world, user);
            if (!preview.valid()) {
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.consume(stack);
        }

        // Mode A: Normal ballistic throw (RMB)
        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            if (!Grom7Manager.beginThrow(serverPlayer, hand)) {
                return TypedActionResult.fail(stack);
            }
        }
        return TypedActionResult.consume(stack);
    }
}
