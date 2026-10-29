package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;

/** Connections are resolved from the render region, including old saves and generated chunks. */
public class ConnectedSurfaceBlock extends Block {
    public static final BooleanProperty NORTH=BooleanProperty.of("north"),SOUTH=BooleanProperty.of("south"),
        EAST=BooleanProperty.of("east"),WEST=BooleanProperty.of("west"),UP=BooleanProperty.of("up"),DOWN=BooleanProperty.of("down");
    public ConnectedSurfaceBlock(Settings settings){super(settings);}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(NORTH,SOUTH,EAST,WEST,UP,DOWN);}
    public static BooleanProperty property(Direction d){return switch(d){case NORTH->NORTH;case SOUTH->SOUTH;case EAST->EAST;case WEST->WEST;case UP->UP;case DOWN->DOWN;};}
    public static BlockState connectedState(BlockState state,BlockView world,BlockPos pos){
        if(!(state.getBlock() instanceof ConnectedSurfaceBlock))return state;
        for(Direction d:Direction.values()){
            BlockState other=world.getBlockState(pos.offset(d));
            boolean match=state.isOf(other.getBlock())&&(!(state.getBlock() instanceof CheckpointRoofBlock)||state.get(CheckpointRoofBlock.VARIANT).equals(other.get(CheckpointRoofBlock.VARIANT)));
            state=state.with(property(d),match);
        }
        return state;
    }
}
