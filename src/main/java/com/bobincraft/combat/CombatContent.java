package com.bobincraft.combat;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class CombatContent {
    public static final net.minecraft.particle.DefaultParticleType VOXEL_CLOUD = net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true);
    public static final net.minecraft.particle.DefaultParticleType BLOOD_DROPLET = net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true);
    // Plain soil blocks deliberately keep their burned appearance through saves and biome changes.
    public static final Block SCORCHED_GRASS = new Block(Block.Settings.copy(Blocks.DIRT));
    public static final Block PARTIALLY_SCORCHED_GRASS = new Block(Block.Settings.copy(Blocks.DIRT));
    public static final Item TOURNIQUET = new TourniquetItem(new FabricItemSettings().maxCount(16));

    private CombatContent() { }

    public static void register() {
        Registry.register(Registries.PARTICLE_TYPE, id("voxel_cloud"), VOXEL_CLOUD);
        Registry.register(Registries.PARTICLE_TYPE, id("blood_droplet"), BLOOD_DROPLET);
        com.bobincraft.drone.BlastTerrain.register();
        registerSoil("scorched_grass", SCORCHED_GRASS);
        registerSoil("partially_scorched_grass", PARTIALLY_SCORCHED_GRASS);
        Registry.register(Registries.ITEM, id("tourniquet"), TOURNIQUET);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(TOURNIQUET));
        InjurySystem.register();
        CombatMarks.register();
    }

    private static void registerSoil(String name, Block block) {
        Registry.register(Registries.BLOCK, id(name), block);
        Item item = Registry.register(Registries.ITEM, id(name), new BlockItem(block, new FabricItemSettings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.add(item));
    }

    private static Identifier id(String path) { return new Identifier("bobin_craft", path); }
}
