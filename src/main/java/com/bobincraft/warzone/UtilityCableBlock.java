package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Thin, non-colliding cables with connections baked across chunk boundaries. */
public class UtilityCableBlock extends Block {
    public static final BooleanProperty[] CONNECTIONS={Properties.NORTH,Properties.EAST,Properties.SOUTH,Properties.WEST,Properties.UP,Properties.DOWN};
    public UtilityCableBlock(Settings settings){super(settings);BlockState state=getDefaultState();for(var property:CONNECTIONS)state=state.with(property,false);setDefaultState(state);}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(CONNECTIONS);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        VoxelShape shape=Block.createCuboidShape(7,7,7,9,9,9);int[][] boxes={{7,7,0,9,9,8},{8,7,7,16,9,9},{7,7,8,9,9,16},{0,7,7,8,9,9},{7,8,7,9,16,9},{7,0,7,9,8,9}};
        for(int i=0;i<6;i++)if(state.get(CONNECTIONS[i])){int[] b=boxes[i];shape=VoxelShapes.union(shape,Block.createCuboidShape(b[0],b[1],b[2],b[3],b[4],b[5]));}return shape;
    }
}
