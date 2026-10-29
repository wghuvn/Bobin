package com.bobincraft.warzone;
import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
/** Thin sheet using the zone's existing rusted metal / olive tarp / blue tarp textures. */
public final class CheckpointRoofBlock extends ConnectedSurfaceBlock {
    public static final IntProperty VARIANT=IntProperty.of("variant",0,2);
    private static final VoxelShape SHAPE=Block.createCuboidShape(0,0,0,16,2,16);
    public CheckpointRoofBlock(Settings settings){super(settings);setDefaultState(stateManager.getDefaultState().with(VARIANT,0));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(VARIANT);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPE;}
}
