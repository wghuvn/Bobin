package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** A round, flush cover that slides onto the adjoining ground. No raised collar or lever. */
public final class ManholeBlock extends BlockWithEntity {
    public static final BooleanProperty OPEN=Properties.OPEN;
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    private static VoxelShape corners(){
        VoxelShape result=VoxelShapes.empty();for(int z=0;z<16;z++){
            double half=Math.sqrt(Math.max(0,7.8*7.8-(z+.5-8)*(z+.5-8)));
            if(8-half>0){result=VoxelShapes.union(result,Block.createCuboidShape(0,0,z,8-half,16,z+1));result=VoxelShapes.union(result,Block.createCuboidShape(8+half,0,z,16,16,z+1));}
        }
        // Keep all four outer faces closed: sampled circular rows must not punch a slit through the road edge.
        for(VoxelShape side:new VoxelShape[]{Block.createCuboidShape(0,0,0,16,16,.25),Block.createCuboidShape(0,0,15.75,16,16,16),Block.createCuboidShape(0,0,0,.25,16,16),Block.createCuboidShape(15.75,0,0,16,16,16)})result=VoxelShapes.union(result,side);
        return result;
    }
    private static final VoxelShape CORNERS=corners();
    private static final VoxelShape LID=Block.createCuboidShape(0,14.5,0,16,16,16);
    public ManholeBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(OPEN,false).with(FACING,Direction.EAST));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(OPEN,FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().rotateYClockwise());}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.MODEL;}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ManholeBlockEntity(pos,state);}
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return LID;}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        if(w!=null&&w.getBlockEntity(p) instanceof ManholeBlockEntity be)return ManholeMotion.passable(be.progress(0))?CORNERS:VoxelShapes.union(CORNERS,LID);
        return s.get(OPEN)?CORNERS:VoxelShapes.union(CORNERS,LID);
    }
    @Override public VoxelShape getCullingShape(BlockState s,BlockView w,BlockPos p){return VoxelShapes.empty();}
    @Override public ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){
        if(w.isClient)return ActionResult.SUCCESS;
        boolean open=!s.get(OPEN);BlockPos side=p.offset(s.get(FACING)).up();
        if(open&&!w.getBlockState(side).getCollisionShape(w,side).isEmpty())return ActionResult.FAIL;
        // Wait until the aperture is vacant before closing; the moving lid cannot crush a climber.
        if(!open&&!w.getOtherEntities(null,new Box(p).expand(0,.2,0)).isEmpty())return ActionResult.FAIL;
        w.setBlockState(p,s.with(OPEN,open),Block.NOTIFY_ALL);
        if(w!=null&&w.getBlockEntity(p) instanceof ManholeBlockEntity be)be.animate(open);
        w.playSound(null,p,open?SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN:SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,SoundCategory.BLOCKS,.7f,.65f);
        return ActionResult.CONSUME;
    }
}
