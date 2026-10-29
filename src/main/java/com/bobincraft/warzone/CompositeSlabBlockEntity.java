package com.bobincraft.warzone;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

public final class CompositeSlabBlockEntity extends BlockEntity {
    private BlockState lower=Blocks.STONE_SLAB.getDefaultState(),upper=Blocks.SPRUCE_SLAB.getDefaultState().with(SlabBlock.TYPE,net.minecraft.block.enums.SlabType.TOP);
    public CompositeSlabBlockEntity(BlockPos pos,BlockState state){super(CombatZoneBlocks.COMPOSITE_SLAB_ENTITY,pos,state);}
    public BlockState lower(){return lower;}
    public BlockState upper(){return upper;}
    public void setMaterials(BlockState bottom,BlockState top){lower=bottom;upper=top;markDirty();}
    @Override protected void writeNbt(NbtCompound n){super.writeNbt(n);n.put("Lower",NbtHelper.fromBlockState(lower));n.put("Upper",NbtHelper.fromBlockState(upper));}
    @Override public void readNbt(NbtCompound n){super.readNbt(n);if(n.contains("Lower"))lower=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),n.getCompound("Lower"));if(n.contains("Upper"))upper=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),n.getCompound("Upper"));}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
