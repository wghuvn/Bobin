package com.bobincraft.warzone.client;

import com.bobincraft.warzone.CompositeSlabBlockEntity;
import net.minecraft.block.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;

public final class CompositeSlabRenderer implements BlockEntityRenderer<CompositeSlabBlockEntity> {
    private final BlockRenderManager renderer;
    public CompositeSlabRenderer(BlockEntityRendererFactory.Context context){renderer=context.getRenderManager();}
    @Override public void render(CompositeSlabBlockEntity be,float delta,MatrixStack m,VertexConsumerProvider vertices,int light,int overlay){
        renderHalf(be.lower(),false,m,vertices,light,overlay);renderHalf(be.upper(),true,m,vertices,light,overlay);
    }
    private void renderHalf(BlockState state,boolean upper,MatrixStack m,VertexConsumerProvider vertices,int light,int overlay){
        m.push();if(!(state.getBlock() instanceof SlabBlock)){if(upper)m.translate(0,.5,0);m.scale(1,.5f,1);}
        renderer.renderBlockAsEntity(state,m,vertices,light,overlay);m.pop();
    }
}
