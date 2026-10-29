package com.bobincraft.combat;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

public final class TourniquetItem extends Item {
    public TourniquetItem(Settings settings) { super(settings); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(player instanceof InjuryCarrier carrier) || !carrier.bobinInjuries().isBleeding()) {
            if (!world.isClient) player.sendMessage(Text.translatable("injury.bobin_craft.no_bleeding"), true);
            return TypedActionResult.fail(stack);
        }
        player.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && InjurySystem.treat(user)
                && (!(user instanceof PlayerEntity player) || !player.getAbilities().creativeMode)) stack.decrement(1);
        return stack;
    }

    @Override public ActionResult useOnEntity(ItemStack stack, PlayerEntity player, LivingEntity target, Hand hand) {
        if (!(target instanceof InjuryCarrier)) return ActionResult.PASS;
        if (target.getWorld().isClient) return ActionResult.SUCCESS;
        if (!InjurySystem.treat(target)) return ActionResult.PASS;
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        player.sendMessage(Text.translatable("injury.bobin_craft.treated_other"), true);
        return ActionResult.CONSUME;
    }

    @Override public int getMaxUseTime(ItemStack stack) { return 40; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.bobin_craft.tourniquet.tooltip"));
    }
}
