package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Open-weave netting has a physical sheet collider, including its transparent openings. */
public final class DroneBarrierBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS=net.minecraft.state.property.Properties.AXIS;
    private static final VoxelShape X=Block.createCuboidShape(7,0,0,9,16,16),Y=Block.createCuboidShape(0,7,0,16,9,16),Z=Block.createCuboidShape(0,0,7,16,16,9);
    public DroneBarrierBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(AXIS,Direction.Axis.Y));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(AXIS);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(AXIS,c.getSide().getAxis());}
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return switch(s.get(AXIS)){case X->X;case Y->Y;case Z->Z;};}
}
