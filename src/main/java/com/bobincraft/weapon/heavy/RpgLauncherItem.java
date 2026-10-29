package com.bobincraft.weapon.heavy;

import com.bobincraft.combat.PlayerInjuries;
import com.bobincraft.raid.HubSafety;
import com.bobincraft.vehicle.ArmorContent;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Heavy RPG launcher item with separate rocket ammunition, reload cycle, shot animation,
 * backblast/muzzle effect, and projectile launch (Section 11).
 */
public final class RpgLauncherItem extends Item {
    private static final String LOADED_KEY = "RpgLoaded";
    private static final String RELOAD_UNTIL_KEY = "RpgReloadUntil";

    public RpgLauncherItem(Settings settings) {
        super(settings);
    }

    public static boolean isLoaded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt == null || !nbt.contains(LOADED_KEY) || nbt.getBoolean(LOADED_KEY);
    }

    public static void setLoaded(ItemStack stack, boolean loaded) {
        if (stack != null && !stack.isEmpty()) {
            stack.getOrCreateNbt().putBoolean(LOADED_KEY, loaded);
        }
    }

    public static boolean isReloading(ItemStack stack, long worldTime) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt()) return false;
        long until = stack.getNbt().getLong(RELOAD_UNTIL_KEY);
        return until > worldTime;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 72000;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(RELOAD_UNTIL_KEY)) {
            long until = nbt.getLong(RELOAD_UNTIL_KEY);
            if (world.getTime() >= until) {
                nbt.remove(RELOAD_UNTIL_KEY);
                setLoaded(stack, true);
                world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 0.8f, 0.9f);
                player.sendMessage(Text.translatable("item.bobin_craft.rpg_launcher.reloaded"), true);
            }
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!user.isAlive() || user.isSpectator() || ArmorContent.inHub(world)
                || !PlayerInjuries.usableHand(user, Hand.MAIN_HAND)
                || !PlayerInjuries.usableHand(user, Hand.OFF_HAND)) {
            return TypedActionResult.fail(stack);
        }

        if (isReloading(stack, world.getTime())) {
            return TypedActionResult.fail(stack);
        }

        if (!isLoaded(stack)) {
            if (!consumeRocketAmmo(user)) {
                if (!world.isClient) {
                    user.sendMessage(Text.translatable("item.bobin_craft.rpg_launcher.no_ammo"), true);
                    world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.7f, 1.2f);
                }
                return TypedActionResult.fail(stack);
            }
            if (!world.isClient) {
                stack.getOrCreateNbt().putLong(RELOAD_UNTIL_KEY, world.getTime() + RpgContent.reloadTicks);
                user.getItemCooldownManager().set(this, RpgContent.reloadTicks);
                world.playSound(null, user.getBlockPos(), RpgContent.RPG_RELOAD_SOUND, SoundCategory.PLAYERS, 0.85f, 0.95f);
                user.sendMessage(Text.translatable("item.bobin_craft.rpg_launcher.reloading"), true);
            }
            return TypedActionResult.consume(stack);
        }

        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
            if (HubSafety.isHub(serverWorld)) return TypedActionResult.fail(stack);
            fireRocket(serverWorld, serverPlayer, stack, hand);
        }
        return TypedActionResult.consume(stack);
    }

    public static boolean fireRocket(ServerWorld world, LivingEntity shooter, ItemStack launcherStack, Hand hand) {
        RpgProjectileEntity rocket = RpgContent.RPG_PROJECTILE.create(world);
        if (rocket == null) return false;
        Vec3d look = shooter.getRotationVec(1.0f).normalize();
        Vec3d spawnPos = shooter.getEyePos().add(look.multiply(0.75)).add(0.0, -0.10, 0.0);
        Vec3d velocity = look.multiply(RpgContent.projectileSpeed).add(shooter.getVelocity().multiply(0.25));
        rocket.launch(shooter, spawnPos, velocity);
        if (!world.spawnEntity(rocket)) return false;

        if (shooter instanceof PlayerEntity player) {
            if (!player.getAbilities().creativeMode) {
                setLoaded(launcherStack, false);
                launcherStack.damage(1, player, p -> p.sendToolBreakStatus(hand));
            }
            player.getItemCooldownManager().set(RpgContent.RPG_LAUNCHER, RpgContent.fireCooldownTicks);
        }

        // Limited muzzle & backblast smoke puff (no extra entities!)
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                spawnPos.x, spawnPos.y, spawnPos.z, 4, 0.12, 0.12, 0.12, 0.03);
        Vec3d backblast = shooter.getEyePos().subtract(look.multiply(0.9));
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                backblast.x, backblast.y - 0.15, backblast.z, 3, 0.15, 0.10, 0.15, 0.04);
        world.playSound(null, shooter.getBlockPos(), RpgContent.RPG_FIRE_SOUND, SoundCategory.PLAYERS, 1.4f, 0.85f);
        world.playSound(null, shooter.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.1f, 0.55f);
        return true;
    }

    private static boolean consumeRocketAmmo(PlayerEntity player) {
        if (player.getAbilities().creativeMode) return true;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack inv = player.getInventory().getStack(i);
            if (inv.isOf(RpgContent.RPG_ROCKET) && !inv.isEmpty()) {
                inv.decrement(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(isLoaded(stack)
                ? "item.bobin_craft.rpg_launcher.status.loaded"
                : "item.bobin_craft.rpg_launcher.status.empty"));
        tooltip.add(Text.translatable("item.bobin_craft.rpg_launcher.tooltip"));
    }
}
