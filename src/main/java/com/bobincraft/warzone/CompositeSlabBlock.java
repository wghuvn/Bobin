package com.bobincraft.warzone;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.*;
import net.minecraft.loot.context.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import java.util.List;

/** Two independently saved materials in a single full collision cube. */
public final class CompositeSlabBlock extends BlockWithEntity {
    public CompositeSlabBlock(Settings settings){super(settings);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CompositeSlabBlockEntity(pos,state);}
    @Override public List<ItemStack> getDroppedStacks(BlockState s,LootContextParameterSet.Builder context){
        if(context.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof CompositeSlabBlockEntity be)return List.of(new ItemStack(be.lower().getBlock()),new ItemStack(be.upper().getBlock()));
        return List.of();
    }
    public static boolean fillsMissingHalf(BlockState slab,Direction side,double hitY){
        if(!(slab.getBlock() instanceof SlabBlock)||slab.get(SlabBlock.TYPE)==SlabType.DOUBLE)return false;
        return slab.get(SlabBlock.TYPE)==SlabType.BOTTOM?side==Direction.UP||side.getAxis().isHorizontal()&&hitY>=.5:
            side==Direction.DOWN||side.getAxis().isHorizontal()&&hitY<=.5;
    }
    public static void registerInteraction(){UseBlockCallback.EVENT.register((player,world,hand,hit)->{
        var stack=player.getStackInHand(hand);if(player.isSpectator()||player.isSneaking()||!(stack.getItem() instanceof BlockItem item))return ActionResult.PASS;
        var pos=hit.getBlockPos();var state=world.getBlockState(pos);var material=item.getBlock().getDefaultState();
        if(material.getBlock()==state.getBlock()||!fillsMissingHalf(state,hit.getSide(),hit.getPos().y-pos.getY())||material.hasBlockEntity()||
                !(material.getBlock() instanceof SlabBlock)&&!material.isFullCube(world,pos))return ActionResult.PASS;
        if(!world.canPlayerModifyAt(player,pos)||!player.canModifyBlocks())return ActionResult.FAIL;
        if(world.isClient)return ActionResult.SUCCESS;
        boolean bottom=state.get(SlabBlock.TYPE)==SlabType.BOTTOM;
        BlockState lower=bottom?state:material,upper=bottom?material:state;
        if(lower.getBlock() instanceof SlabBlock)lower=lower.with(SlabBlock.TYPE,SlabType.BOTTOM).with(SlabBlock.WATERLOGGED,false);
        if(upper.getBlock() instanceof SlabBlock)upper=upper.with(SlabBlock.TYPE,SlabType.TOP).with(SlabBlock.WATERLOGGED,false);
        world.setBlockState(pos,CombatZoneBlocks.COMPOSITE_SLAB.getDefaultState(),Block.NOTIFY_ALL);
        if(world.getBlockEntity(pos) instanceof CompositeSlabBlockEntity be){be.setMaterials(lower,upper);world.updateListeners(pos,be.getCachedState(),be.getCachedState(),Block.NOTIFY_ALL);}
        if(!player.getAbilities().creativeMode)stack.decrement(1);
        world.playSound(null,pos,material.getSoundGroup().getPlaceSound(),net.minecraft.sound.SoundCategory.BLOCKS,1,1);
        return ActionResult.CONSUME;
    });}
}
