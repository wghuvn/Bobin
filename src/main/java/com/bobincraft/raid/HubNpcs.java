package com.bobincraft.raid;

import com.bobincraft.combat.CombatContent;
import com.bobincraft.drone.DroneContent;
import com.bobincraft.drone.DroneVariant;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import com.bobincraft.vehicle.ArmorContent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Spawns all camp NPCs: quartermaster, cook, medic, commander, and perimeter sentries. */
public final class HubNpcs {
    private static final Set<String> SERVICE_TAGS = Set.of(
            "bobin_hub_merchant", "bobin_hub_cook", "bobin_hub_medic", "bobin_hub_commander");

    private HubNpcs() { }

    public static boolean isServiceNpc(Entity entity) {
        return entity.getCommandTags().stream().anyMatch(SERVICE_TAGS::contains);
    }

    public static void spawn(ServerWorld world) {
        spawnQuartermaster(world);
        spawnCook(world);
        spawnMedic(world);
        spawnCommander(world);

        for (BlockPos post : HubLayout.GUARDS) {
            String tag = "bobin_hub_guard_" + post.getX() + "_" + post.getY() + "_" + post.getZ();
            world.getChunk(post);
            WarzoneGuardEntity existingGuard = find(world, tag, WarzoneGuardEntity.class);
            if (existingGuard != null) {
                existingGuard.setSilent(true);
                continue;
            }
            WarzoneGuardEntity guard = WarzoneEncounterManager.GUARD.create(world);
            if (guard == null) throw new IllegalStateException("Unable to create hub guard");
            guard.setUuid(identity(world, tag));
            guard.addCommandTag(tag);
            place(guard, post);
            guard.assignHubGuard(post);
            guard.setSilent(true);
            if (post.getY() > HubLayout.PLAYER_Y) {
                guard.setCustomName(Text.translatable("entity.bobin_craft.tower_sniper"));
            } else if (post.getZ() >= 40) {
                guard.setCustomName(Text.translatable("entity.bobin_craft.checkpoint_guard"));
            } else {
                guard.setCustomName(Text.translatable("entity.bobin_craft.hub_guard"));
            }
            if (!world.spawnEntity(guard)) throw new IllegalStateException("Unable to spawn hub guard at " + post);
        }
    }

    private static void spawnQuartermaster(ServerWorld world) {
        VillagerEntity merchant = service(world, "bobin_hub_merchant", HubLayout.MERCHANT,
                VillagerProfession.WEAPONSMITH, "entity.bobin_craft.quartermaster");
        if (merchant == null) return;

        TradeOfferList offers = merchant.getOffers();
        offers.clear();
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 4), new ItemStack(ArmorContent.GRENADE, 2), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 6), new ItemStack(DroneContent.DRONE_CONTROLLER), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 8), new ItemStack(DroneContent.FPV_CONTROLLER), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 10), new ItemStack(DroneContent.itemFor(DroneVariant.FPV_FRAGMENTATION)), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 12), new ItemStack(DroneContent.BOMBER_CONTROLLER), 12, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 8), new ItemStack(ArmorContent.SHELL, 4), 24, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 3), new ItemStack(CombatContent.TOURNIQUET, 2), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 6), new ItemStack(Items.IRON_CHESTPLATE), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.IRON_HELMET), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 4), new ItemStack(Items.IRON_LEGGINGS), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.IRON_BOOTS), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.EMERALD), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.GUNPOWDER, 12), new ItemStack(Items.EMERALD), 64, 1, 0.0f));

        if (!world.spawnEntity(merchant)) throw new IllegalStateException("Unable to spawn hub quartermaster");
    }

    private static void spawnCook(ServerWorld world) {
        VillagerEntity cook = service(world, "bobin_hub_cook", HubLayout.COOK,
                VillagerProfession.BUTCHER, "entity.bobin_craft.camp_cook");
        if (cook == null) return;

        TradeOfferList offers = cook.getOffers();
        offers.clear();
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.BREAD, 12), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.COOKED_BEEF, 8), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.BAKED_POTATO, 16), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.GOLDEN_APPLE), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.GOLDEN_CARROT, 6), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.HONEY_BOTTLE, 2), 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.COAL, 16), new ItemStack(Items.EMERALD), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.WHEAT, 24), new ItemStack(Items.EMERALD), 64, 1, 0.0f));

        if (!world.spawnEntity(cook)) throw new IllegalStateException("Unable to spawn camp cook");
    }

    private static void spawnMedic(ServerWorld world) {
        VillagerEntity medic = service(world, "bobin_hub_medic", HubLayout.MEDIC,
                VillagerProfession.CLERIC, "entity.bobin_craft.field_medic");
        if (medic == null) return;

        TradeOfferList offers = medic.getOffers();
        offers.clear();
        ItemStack healPot = PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
        ItemStack regenPot = PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.REGENERATION);
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 2), healPot, 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 3), regenPot, 32, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.LEATHER, 8), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.MILK_BUCKET), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.GLASS_BOTTLE, 16), new ItemStack(Items.EMERALD), 64, 1, 0.0f));

        if (!world.spawnEntity(medic)) throw new IllegalStateException("Unable to spawn field medic");
    }

    private static void spawnCommander(ServerWorld world) {
        VillagerEntity commander = service(world, "bobin_hub_commander", HubLayout.COMMANDER,
                VillagerProfession.ARMORER, "entity.bobin_craft.tactical_commander");
        if (commander == null) return;

        TradeOfferList offers = commander.getOffers();
        offers.clear();
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.SPYGLASS), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.COMPASS), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.CLOCK), 16, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.EMERALD), new ItemStack(Items.TORCH, 24), 64, 1, 0.0f));
        offers.add(new TradeOffer(new ItemStack(Items.PAPER, 24), new ItemStack(Items.EMERALD), 64, 1, 0.0f));

        if (!world.spawnEntity(commander)) throw new IllegalStateException("Unable to spawn tactical commander");
    }

    /** Existing service entities retain their UUIDs, used offers and inventory on a retry. */
    private static VillagerEntity service(ServerWorld world, String tag, BlockPos pos,
                                         VillagerProfession profession, String name) {
        world.getChunk(pos);
        VillagerEntity existing = find(world, tag, VillagerEntity.class);
        if (existing != null) {
            existing.setCustomName(Text.translatable(name));
            existing.setCustomNameVisible(true);
            existing.setSilent(true);
            return null;
        }
        VillagerEntity villager = EntityType.VILLAGER.create(world);
        if (villager == null) throw new IllegalStateException("Unable to create hub service " + tag);
        villager.setUuid(identity(world, tag));
        place(villager, pos);
        villager.setVillagerData(new VillagerData(VillagerType.TAIGA, profession, 5));
        villager.setAiDisabled(true);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setPersistent();
        villager.setCustomName(Text.translatable(name));
        villager.setCustomNameVisible(true);
        villager.addCommandTag(tag);
        return villager;
    }

    private static <T extends Entity> T find(ServerWorld world, String tag, Class<T> type) {
        Entity stable = world.getEntity(identity(world, tag));
        if (type.isInstance(stable) && !stable.isRemoved()) return type.cast(stable);
        for (Entity entity : world.iterateEntities()) {
            if (type.isInstance(entity) && !entity.isRemoved() && entity.getCommandTags().contains(tag)) {
                return type.cast(entity);
            }
        }
        return null;
    }

    private static UUID identity(ServerWorld world, String tag) {
        return UUID.nameUUIDFromBytes(("bobin_craft:" + world.getSeed() + ":" + tag)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void place(Entity entity, BlockPos pos) {
        float yaw = (float) Math.toDegrees(Math.atan2(pos.getX(), -pos.getZ()));
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
    }
}
