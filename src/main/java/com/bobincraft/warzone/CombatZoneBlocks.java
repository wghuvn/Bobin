package com.bobincraft.warzone;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.GlassBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntityType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Static full-cube materials using the supplied project's existing, previously unbound zone art and Eastern European architectural blocks. */
public final class CombatZoneBlocks {
    private CombatZoneBlocks() { }
    private static Block metal(){return new ConnectedSurfaceBlock(FabricBlockSettings.create().mapColor(MapColor.GRAY).sounds(BlockSoundGroup.METAL).strength(3,6).requiresTool());}
    private static Block concrete(){return new Block(FabricBlockSettings.create().mapColor(MapColor.STONE_GRAY).sounds(BlockSoundGroup.STONE).strength(1.5f,6).requiresTool());}
    private static Block tarp(){return new ConnectedSurfaceBlock(FabricBlockSettings.create().mapColor(MapColor.GREEN).sounds(BlockSoundGroup.WOOL).strength(.8f));}
    private static Block wood(){return new Block(FabricBlockSettings.create().mapColor(MapColor.OAK_TAN).sounds(BlockSoundGroup.WOOD).strength(1.6f,3));}
    private static Block glass(){return new GlassBlock(FabricBlockSettings.create().mapColor(MapColor.CLEAR).sounds(BlockSoundGroup.GLASS).strength(0.4f).nonOpaque());}
    private static Block frame(){return new Block(FabricBlockSettings.create().mapColor(MapColor.SPRUCE_BROWN).sounds(BlockSoundGroup.WOOD).strength(1.2f,2).nonOpaque());}
    private static Block solidMetal(){return new Block(FabricBlockSettings.create().mapColor(MapColor.IRON_GRAY).sounds(BlockSoundGroup.METAL).strength(2.5f,6).requiresTool());}

    /**
     * Non-flowing liquid crude oil inside refinery storage tanks.
     * Does not spread/flow out when a breach hole is punched in the tank wall, slows entities wading in it like thick oil,
     * and is consumed 1 block every few seconds while the tank is burning.
     */
    public static final Block CRUDE_OIL = new Block(FabricBlockSettings.create()
            .mapColor(MapColor.BLACK)
            .sounds(BlockSoundGroup.HONEY)
            .strength(50.0f, 100.0f)
            .nonOpaque()
            .dropsNothing()) {
        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
            if (context instanceof EntityShapeContext esc && esc.getEntity() instanceof LivingEntity) {
                return VoxelShapes.empty();
            }
            return VoxelShapes.empty();
        }

        @Override
        public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
            entity.slowMovement(state, new Vec3d(0.42, 0.30, 0.42));
        }
    };

    public static final Block CHECKPOINT_ROOF=new CheckpointRoofBlock(FabricBlockSettings.create().mapColor(MapColor.GREEN).sounds(BlockSoundGroup.METAL).strength(1,3).nonOpaque());
    public static final Block BURLAP_SANDBAGS=new Block(FabricBlockSettings.create().mapColor(MapColor.BROWN).sounds(BlockSoundGroup.GRAVEL).strength(1.2f,2).nonOpaque()){
        @Override public net.minecraft.util.shape.VoxelShape getOutlineShape(net.minecraft.block.BlockState state,net.minecraft.world.BlockView world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.ShapeContext context){return Block.createCuboidShape(0,0,0,16,14,16);}
    };
    public static final Block RUSTED_SHEET_METAL=metal();
    public static final Block CRACKED_CONCRETE=concrete();
    public static final Block MOSSY_CONCRETE=concrete();
    public static final Block CONTAINER_GREEN=metal();
    public static final Block CONTAINER_BLUE=metal();
    public static final Block CONTAINER_RED=metal();
    public static final Block TARP_OLIVE=tarp();
    public static final Block TARP_BLUE=tarp();
    public static final Block EQUIPMENT_PANEL=metal();
    public static final Block UTILITY_CABLE=new UtilityCableBlock(FabricBlockSettings.create().mapColor(MapColor.BLACK).sounds(BlockSoundGroup.METAL).strength(.8f).noCollision().nonOpaque());
    public static final Block WEATHERED_ASPHALT_SLAB=new SlabBlock(FabricBlockSettings.create().mapColor(MapColor.BLACK).sounds(BlockSoundGroup.STONE).strength(1.5f,6).requiresTool());
    public static final Block CABLE_SPAN=new CableSpanBlock(FabricBlockSettings.create().strength(-1,3600000).noCollision().nonOpaque());
    public static final Block MANHOLE=new ManholeBlock(FabricBlockSettings.create().mapColor(MapColor.GRAY).sounds(BlockSoundGroup.METAL).strength(4,8).nonOpaque().dynamicBounds());
    public static final Block UTILITY_PIPE=new UtilityCableBlock(FabricBlockSettings.create().mapColor(MapColor.GRAY).sounds(BlockSoundGroup.METAL).strength(2,5).nonOpaque());
    public static final Block DRAIN_OUTLET=new DrainOutletBlock(FabricBlockSettings.create().mapColor(MapColor.GRAY).sounds(BlockSoundGroup.METAL).strength(2,5).nonOpaque().noCollision());
    public static final BlockEntityType<CableSpanBlockEntity> CABLE_SPAN_ENTITY=FabricBlockEntityTypeBuilder.create(CableSpanBlockEntity::new,CABLE_SPAN).build();
    public static final BlockEntityType<ManholeBlockEntity> MANHOLE_ENTITY=FabricBlockEntityTypeBuilder.create(ManholeBlockEntity::new,MANHOLE).build();
    public static final Block ANTI_DRONE_NET=new DroneBarrierBlock(FabricBlockSettings.create().mapColor(MapColor.DARK_GREEN).sounds(BlockSoundGroup.WOOL).strength(1.2f).nonOpaque());
    public static final Block FACTION_MARKER=new FactionMarkerBlock(FabricBlockSettings.create().mapColor(MapColor.BROWN).sounds(BlockSoundGroup.WOOL).strength(.4f).nonOpaque().noCollision());
    public static final Block BUSBAR=new UtilityCableBlock(FabricBlockSettings.create().mapColor(MapColor.GRAY).sounds(BlockSoundGroup.METAL).strength(1,4).nonOpaque());
    public static final Block INSULATOR=new Block(FabricBlockSettings.create().mapColor(MapColor.BROWN).sounds(BlockSoundGroup.STONE).strength(1,3).nonOpaque());
    public static final Block COMPOSITE_SLAB=new CompositeSlabBlock(FabricBlockSettings.create().mapColor(MapColor.GRAY).sounds(BlockSoundGroup.STONE).strength(2,6).nonOpaque());
    public static final BlockEntityType<CompositeSlabBlockEntity> COMPOSITE_SLAB_ENTITY=FabricBlockEntityTypeBuilder.create(CompositeSlabBlockEntity::new,COMPOSITE_SLAB).build();

    // New Eastern European / post-Soviet architectural blocks (Part 7)
    public static final Block WEATHERED_PLASTER=concrete();
    public static final Block CRACKED_RED_BRICK=concrete();
    public static final Block OLD_CINDERBLOCK=concrete();
    public static final Block DARK_CONCRETE_PANEL=concrete();
    public static final Block CORRUGATED_SLATE=concrete();
    public static final Block METAL_GARAGE_DOOR=metal();
    public static final Block BROKEN_WINDOW_GLASS=glass();
    public static final Block OLD_WINDOW_FRAME=frame();
    public static final Block RUSTED_VENT_GRILLE=solidMetal();
    public static final Block TRANSFORMER_CABINET=solidMetal();
    public static final Block OLD_WALL_TILE=concrete();
    public static final Block PEELING_PAINT_WALL=concrete();
    public static final Block DAMAGED_WOOD_PANEL=wood();

    public static void register() {
        register("crude_oil",CRUDE_OIL);
        register("burlap_sandbags",BURLAP_SANDBAGS);
        register("checkpoint_roof",CHECKPOINT_ROOF);
        Registry.register(Registries.BLOCK,new Identifier("bobin_craft","cable_span"),CABLE_SPAN);
        register("manhole_cover",MANHOLE);
        Registry.register(Registries.BLOCK,new Identifier("bobin_craft","composite_slab"),COMPOSITE_SLAB);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,new Identifier("bobin_craft","composite_slab"),COMPOSITE_SLAB_ENTITY);
        CompositeSlabBlock.registerInteraction();
        register("utility_pipe",UTILITY_PIPE);
        register("faction_marker",FACTION_MARKER);
        register("anti_drone_net",ANTI_DRONE_NET);
        register("busbar",BUSBAR);register("insulator",INSULATOR);
        register("drain_outlet",DRAIN_OUTLET);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,new Identifier("bobin_craft","cable_span"),CABLE_SPAN_ENTITY);
        Registry.register(Registries.BLOCK_ENTITY_TYPE,new Identifier("bobin_craft","manhole_cover"),MANHOLE_ENTITY);
        register("rusted_sheet_metal",RUSTED_SHEET_METAL);
        register("cracked_concrete",CRACKED_CONCRETE);
        register("mossy_concrete",MOSSY_CONCRETE);
        register("container_green",CONTAINER_GREEN);
        register("container_blue",CONTAINER_BLUE);
        register("container_red",CONTAINER_RED);
        register("tarp_olive",TARP_OLIVE);
        register("tarp_blue",TARP_BLUE);
        register("equipment_panel",EQUIPMENT_PANEL);
        register("utility_cable",UTILITY_CABLE);
        register("weathered_asphalt_slab",WEATHERED_ASPHALT_SLAB);
        register("weathered_plaster",WEATHERED_PLASTER);
        register("cracked_red_brick",CRACKED_RED_BRICK);
        register("old_cinderblock",OLD_CINDERBLOCK);
        register("dark_concrete_panel",DARK_CONCRETE_PANEL);
        register("corrugated_slate",CORRUGATED_SLATE);
        register("metal_garage_door",METAL_GARAGE_DOOR);
        register("broken_window_glass",BROKEN_WINDOW_GLASS);
        register("old_window_frame",OLD_WINDOW_FRAME);
        register("rusted_vent_grille",RUSTED_VENT_GRILLE);
        register("transformer_cabinet",TRANSFORMER_CABINET);
        register("old_wall_tile",OLD_WALL_TILE);
        register("peeling_paint_wall",PEELING_PAINT_WALL);
        register("damaged_wood_panel",DAMAGED_WOOD_PANEL);
    }
    private static void register(String name,Block block) {
        var id=new Identifier("bobin_craft",name);
        Registry.register(Registries.BLOCK,id,block);
        Registry.register(Registries.ITEM,id,new BlockItem(block,new FabricItemSettings()));
    }
}
