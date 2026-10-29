package com.bobincraft.warzone.client;

import com.bobincraft.warzone.CableSpanBlockEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public final class CableSpanRenderer implements BlockEntityRenderer<CableSpanBlockEntity> {
    private static final Identifier TEXTURE=new Identifier("bobin_craft","textures/block/cable_rubber.png");
    public CableSpanRenderer(BlockEntityRendererFactory.Context ignored){ }
    @Override public void render(CableSpanBlockEntity be,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay){
        float[] data=be.segments();var out=buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));var entry=matrices.peek();
        for(int i=0;i+5<data.length;i+=6)ZoneMesh.tube(out,entry,new Vector3f(data[i],data[i+1],data[i+2]),new Vector3f(data[i+3],data[i+4],data[i+5]),.026f,light);
    }
    @Override public boolean rendersOutsideBoundingBox(CableSpanBlockEntity be){return true;}
    @Override public int getRenderDistance(){return 160;}
}
