package com.bobincraft.weapon.grom7;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Handheld radio detonator for the GROM-7 demolition system ("Пульт подрыва «ГРОМ-7»", Section 10.7–10.8).
 *
 * <ul>
 *   <li><b>RMB (without Shift)</b>: Attempts radio detonation of the selected charge. Plays {@link Grom7Timing.Mode#REMOTE_SUCCESS}
 *       if the radio signal reaches the charge, or the humorous {@link Grom7Timing.Mode#REMOTE_FAIL} animation if signal is lost/absent.</li>
 *   <li><b>Shift + RMB</b>: Cycles selection among the player's active placed charges (#01, #02, #03...).</li>
 * </ul>
 */
public final class Grom7RemoteItem extends Item {
    public Grom7RemoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!Grom7Manager.canUse(user, hand)) {
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            Grom7Manager.beginRemoteUse(serverPlayer, hand);
        }
        return TypedActionResult.consume(stack);
    }
}
