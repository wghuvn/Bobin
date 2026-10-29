package com.bobincraft;

import com.bobincraft.block.IcicleBlock;
import com.bobincraft.raid.HubContent;
import com.bobincraft.raid.HubSafety;
import com.bobincraft.raid.RaidManager;
import com.bobincraft.raid.WarzoneEncounterManager;
import com.bobincraft.warzone.NaturalAmbientLife;
import com.bobincraft.warzone.NaturalWaterEffects;
import com.bobincraft.warzone.WarzoneDimension;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class BobinCraft implements ModInitializer {
    public static final String MOD_ID = "bobin_craft";

    // Ледяная сосулька (прозрачный аналог капельника) — украшение ледяных тоннелей горных крепостей
    public static final Block ICICLE = new IcicleBlock(FabricBlockSettings.create()
            .mapColor(MapColor.LIGHT_BLUE)
            .strength(0.4f)
            .sounds(BlockSoundGroup.GLASS)
            .nonOpaque());
    public static final Item ICICLE_ITEM = new BlockItem(ICICLE, new FabricItemSettings());

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "icicle"), ICICLE);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "icicle"), ICICLE_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.add(ICICLE_ITEM));

        com.bobincraft.warzone.ForestBlocks.register();
        com.bobincraft.warzone.CombatZoneBlocks.register();
        WarzoneDimension.register();
        NaturalWaterEffects.register();
        NaturalAmbientLife.register();
        HubContent.register();
        com.bobincraft.raid.HubAudio.init();
        WarzoneEncounterManager.register();
        RaidManager.register();
        HubSafety.register();
        com.bobincraft.combat.CombatContent.register();
        com.bobincraft.drone.DroneContent.register();
        com.bobincraft.vehicle.ArmorContent.register();
        com.bobincraft.weapon.heavy.RpgContent.register();
        com.bobincraft.weapon.grom7.Grom7Content.register();
        com.bobincraft.destruction.DestructionParticles.register();
        com.bobincraft.destruction.StructureNetworkSync.register();
        com.bobincraft.raid.HubSigns.registerCleanup();
        com.bobincraft.raid.HubNpcInteraction.register();
    }
}
