package com.bobincraft.warzone;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import java.util.List;

public final class CableSpanBlockEntity extends BlockEntity {
    private float[] segments=new float[0];
    public CableSpanBlockEntity(BlockPos pos,BlockState state){super(CombatZoneBlocks.CABLE_SPAN_ENTITY,pos,state);}
    public float[] segments(){return segments;}
    public void setSegments(List<CableGeometry.Segment> source){
        float[] values=new float[source.size()*6];int i=0;
        for(var segment:source)for(var v:List.of(segment.a(),segment.b())){values[i++]=(float)(v.x()-pos.getX());values[i++]=(float)(v.y()-pos.getY());values[i++]=(float)(v.z()-pos.getZ());}
        segments=values;markDirty();
    }
    @Override protected void writeNbt(NbtCompound nbt){super.writeNbt(nbt);int[] data=new int[segments.length];for(int i=0;i<data.length;i++)data[i]=Float.floatToIntBits(segments[i]);nbt.putIntArray("Segments",data);}
    @Override public void readNbt(NbtCompound nbt){super.readNbt(nbt);int[] data=nbt.getIntArray("Segments");
        // A chunk anchor only covers one chunk and cannot hold an unbounded mesh.
        int count=Math.min(24000,data.length/6*6);segments=new float[count];for(int i=0;i<count;i++)segments[i]=Float.intBitsToFloat(data[i]);}
    @Override public NbtCompound toInitialChunkDataNbt(){return createNbt();}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
