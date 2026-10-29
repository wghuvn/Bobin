package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.Direction;

public final class FactionMarkerBlock extends HorizontalFacingBlock {
    public static final IntProperty VARIANT=IntProperty.of("variant",0,2);
    public FactionMarkerBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH).with(VARIANT,0));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING,VARIANT);}
}
