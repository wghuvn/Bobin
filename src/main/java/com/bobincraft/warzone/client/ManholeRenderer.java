package com.bobincraft.warzone.client;

import com.bobincraft.warzone.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public final class ManholeRenderer implements BlockEntityRenderer<ManholeBlockEntity> {
    private static final Identifier TEXTURE=new Identifier("bobin_craft","textures/block/manhole_iron.png");
    public ManholeRenderer(BlockEntityRendererFactory.Context ignored){ }
    @Override public void render(ManholeBlockEntity be,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay){
        var facing=be.getCachedState().get(ManholeBlock.FACING);double slide=ManholeMotion.slide(be.progress(delta));
        matrices.push();matrices.translate(facing.getOffsetX()*slide,ManholeMotion.lift(be.progress(delta)),facing.getOffsetZ()*slide);
        ZoneMesh.lid(buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),matrices.peek(),light);matrices.pop();
    }
    @Override public boolean rendersOutsideBoundingBox(ManholeBlockEntity be){return true;}
}
