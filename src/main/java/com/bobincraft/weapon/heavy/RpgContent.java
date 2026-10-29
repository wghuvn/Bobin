package com.bobincraft.weapon.heavy;

import com.bobincraft.BobinCraft;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Heavy RPG launcher, ammunition, projectile entity, and game balance settings (Section 11).
 * All ballistic, reload, and damage values are configurable game parameters rather than real-world specs.
 */
public final class RpgContent {
    public static float projectileSpeed = 2.85f;
    public static float projectileGravity = 0.012f;
    public static int maxFlightTicks = 120;
    public static int reloadTicks = 50;
    public static int fireCooldownTicks = 20;
    public static float directEntityDamage = 38.0f;
    public static float structuralGameDamage = 360.0f;
    public static double blastEffectRadius = 5.6;

    public static final SoundEvent RPG_FIRE_SOUND = registerSound("rpg_fire");
    public static final SoundEvent RPG_RELOAD_SOUND = registerSound("rpg_reload");

    public static final EntityType<RpgProjectileEntity> RPG_PROJECTILE = registerProjectileType();

    public static final Item RPG_ROCKET = new Item(new FabricItemSettings().maxCount(8));
    public static final Item RPG_LAUNCHER = new RpgLauncherItem(new FabricItemSettings().maxCount(1).maxDamage(128));

    private RpgContent() {}

    private static SoundEvent registerSound(String name) {
        Identifier id = new Identifier(BobinCraft.MOD_ID, name);
        SoundEvent event = SoundEvent.of(id);
        try {
            return Registry.register(Registries.SOUND_EVENT, id, event);
        } catch (Throwable ignored) {
            return event;
        }
    }

    private static EntityType<RpgProjectileEntity> registerProjectileType() {
        Identifier id = new Identifier(BobinCraft.MOD_ID, "rpg_projectile");
        EntityType<RpgProjectileEntity> type = FabricEntityTypeBuilder.<RpgProjectileEntity>create(SpawnGroup.MISC, RpgProjectileEntity::new)
                .dimensions(EntityDimensions.fixed(0.24f, 0.24f))
                .trackRangeChunks(12)
                .trackedUpdateRate(1)
                .build();
        try {
            return Registry.register(Registries.ENTITY_TYPE, id, type);
        } catch (Throwable ignored) {
            return type;
        }
    }

    public static void register() {
        try {
            Registry.register(Registries.ITEM, new Identifier(BobinCraft.MOD_ID, "rpg_rocket"), RPG_ROCKET);
            Registry.register(Registries.ITEM, new Identifier(BobinCraft.MOD_ID, "rpg_launcher"), RPG_LAUNCHER);
        } catch (Throwable ignored) {
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(RPG_LAUNCHER);
            entries.add(RPG_ROCKET);
        });
    }
}
