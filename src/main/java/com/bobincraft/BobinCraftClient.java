package com.bobincraft;

import com.bobincraft.raid.HubContent;
import com.bobincraft.raid.client.RaidClient;
import com.bobincraft.warzone.NaturalAmbientLife;
import com.bobincraft.warzone.client.FlySwarmRenderer;
import com.bobincraft.warzone.client.SkyBirdRenderer;
import com.bobincraft.warzone.client.ZoneToadRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.entity.EntityType;

@Environment(EnvType.CLIENT)
public class BobinCraftClient implements ClientModInitializer {
    public static final String MOD_ID = "bobin_craft";

    @Override
    public void onInitializeClient() {
        com.bobincraft.combat.client.InjuryClient.register();
        com.bobincraft.combat.client.VoxelBlastClient.register();
        com.bobincraft.combat.client.ImpactDebrisClient.register();
        com.bobincraft.combat.client.ConcussionClient.register();
        com.bobincraft.combat.client.SurfaceMarksClient.register();
        com.bobincraft.drone.client.DroneClient.register();
        RaidClient.register();
        com.bobincraft.vehicle.client.ArmorClient.register();
        com.bobincraft.vehicle.client.GrenadeClient.register();
        com.bobincraft.weapon.grom7.client.Grom7Client.register();
        com.bobincraft.destruction.client.StructureDestructionClient.register();
        com.bobincraft.destruction.client.StructureDebrisRenderer.register();
        EntityRendererRegistry.register(com.bobincraft.weapon.heavy.RpgContent.RPG_PROJECTILE, com.bobincraft.weapon.heavy.RpgProjectileRenderer::new);
        EntityRendererRegistry.register(com.bobincraft.weapon.grom7.Grom7Content.GROM7_CHARGE_ENTITY, com.bobincraft.weapon.grom7.client.Grom7ChargeRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
                com.bobincraft.warzone.CombatZoneBlocks.CONTAINER_GREEN,
                com.bobincraft.warzone.CombatZoneBlocks.CONTAINER_BLUE,
                com.bobincraft.warzone.CombatZoneBlocks.CONTAINER_RED,
                com.bobincraft.warzone.CombatZoneBlocks.METAL_GARAGE_DOOR);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(),
                com.bobincraft.warzone.CombatZoneBlocks.BROKEN_WINDOW_GLASS,
                com.bobincraft.warzone.CombatZoneBlocks.OLD_WINDOW_FRAME);
        com.bobincraft.raid.client.PlayerWoundFeature.register();
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.CombatZoneBlocks.ANTI_DRONE_NET, RenderLayer.getCutout());
        BlockEntityRendererFactories.register(com.bobincraft.warzone.CombatZoneBlocks.COMPOSITE_SLAB_ENTITY, com.bobincraft.warzone.client.CompositeSlabRenderer::new);
        BlockEntityRendererFactories.register(com.bobincraft.warzone.CombatZoneBlocks.CABLE_SPAN_ENTITY, com.bobincraft.warzone.client.CableSpanRenderer::new);
        BlockEntityRendererFactories.register(com.bobincraft.warzone.CombatZoneBlocks.MANHOLE_ENTITY, com.bobincraft.warzone.client.ManholeRenderer::new);
        com.bobincraft.raid.client.HubNpcClient.register();
        EntityRendererRegistry.register(EntityType.VILLAGER, com.bobincraft.raid.client.HubServiceNpcRenderer::new);
        EntityRendererRegistry.register(NaturalAmbientLife.ZONE_TOAD, ZoneToadRenderer::new);
        EntityRendererRegistry.register(NaturalAmbientLife.FLY_SWARM, FlySwarmRenderer::new);
        EntityRendererRegistry.register(NaturalAmbientLife.SKY_BIRD, SkyBirdRenderer::new);

        BlockRenderLayerMap.INSTANCE.putBlock(BobinCraft.ICICLE, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(HubContent.CAMOUFLAGE_NET, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HubContent.OUTPOST_BANNER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HubContent.HUB_BARBED_WIRE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.CombatZoneBlocks.FACTION_MARKER, RenderLayer.getCutout());

        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.DRY_UNDERGROWTH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.FOREST_GRASS, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.TALL_FOREST_GRASS, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.PALE_SEDGE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.LOW_FOREST_BUSH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.NEEDLE_UNDERGROWTH, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(com.bobincraft.warzone.ForestBlocks.RIVER_REED, RenderLayer.getCutout());
    }
}
