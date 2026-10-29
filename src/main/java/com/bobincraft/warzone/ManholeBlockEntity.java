package com.bobincraft.warzone;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

public final class ManholeBlockEntity extends BlockEntity {
    private long started=Long.MIN_VALUE;private float from=0;private boolean open;
    public ManholeBlockEntity(BlockPos pos,BlockState state){super(CombatZoneBlocks.MANHOLE_ENTITY,pos,state);open=state.get(ManholeBlock.OPEN);from=open?1:0;}
    public float progress(float tickDelta){return started==Long.MIN_VALUE?from:ManholeMotion.progress(from,open,(world==null?started:world.getTime())-started+tickDelta);}
    public void animate(boolean value){from=progress(0);open=value;started=world==null?0:world.getTime();markDirty();
        if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),3);}
    @Override protected void writeNbt(NbtCompound nbt){super.writeNbt(nbt);nbt.putBoolean("Open",open);nbt.putFloat("From",from);nbt.putLong("Started",started);}
    @Override public void readNbt(NbtCompound nbt){super.readNbt(nbt);open=nbt.getBoolean("Open");from=nbt.contains("From")?nbt.getFloat("From"):(open?1:0);started=nbt.contains("Started")?nbt.getLong("Started"):Long.MIN_VALUE;}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
