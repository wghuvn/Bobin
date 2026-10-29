package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/** Invisible, tick-free chunk anchor for smooth cable meshes. */
public final class CableSpanBlock extends BlockWithEntity {
    public CableSpanBlock(Settings settings){super(settings);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CableSpanBlockEntity(pos,state);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.empty();}
}
