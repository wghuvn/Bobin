package com.bobincraft.armament.client.render.fabric;

import com.bobincraft.armament.client.model.WeaponModelAsset;
import com.bobincraft.armament.client.model.glb.GlbModelAsset;
import com.bobincraft.armament.client.animation.gltf.RigFrame;
import com.bobincraft.armament.client.render.WeaponRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.*;
import org.lwjgl.opengl.*;
import java.util.*;

/** Triangles from GLB accessors or a matching per-instance CPU-skinned frame. */
public final class FabricGlbRenderer implements WeaponRenderer<RigFrame,GlbRenderContext> {
    private ShaderProgram program; // GameRenderer owns shader lifetime during resource reload.
    public void shader(ShaderProgram program){this.program=program;}
    public boolean hasShader(){return program!=null;}
    @Override public void render(WeaponModelAsset source,RigFrame pose,GlbRenderContext context){
        RenderSystem.assertOnRenderThread();if(program==null)return;
        if(!(source instanceof GlbModelAsset asset))throw new IllegalArgumentException("expected static GLB asset");
        if(pose!=null&&(asset.rig()==null||pose.model()!=asset.rig()))throw new IllegalArgumentException("pose belongs to a different GLB asset");
        BufferBuilder buffer=Tessellator.getInstance().getBuffer();
        if(buffer.isBuilding())return; // Never interrupt another mod's unfinished batch.
        State before=new State();
        try{
            RenderSystem.setShader(()->program);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();
            // Explicit event projection * view * root placement; never multiply the global
            // RenderSystem model-view again. Static model normals are already in world axes.
            program.getUniformOrDefault("GlbViewProjection").set(context.viewProjection());
            program.getUniformOrDefault("GlbNormalMatrix").set(context.normalTransform());
            List<GlbModelAsset.Primitive> order=new ArrayList<>(asset.primitives());
            order.sort((a,b)->{
                boolean ta=asset.materials().get(a.material()).alpha()==GlbModelAsset.Alpha.BLEND;
                boolean tb=asset.materials().get(b.material()).alpha()==GlbModelAsset.Alpha.BLEND;
                if(ta!=tb)return ta?1:-1;
                return ta?Double.compare(distance(b,asset,pose,context),distance(a,asset,pose,context)):0;
            });
            for(var primitive:order){
                if(context.bindings()!=null&&!context.bindings().visible(primitive.material()))continue;
                var material=asset.materials().get(primitive.material());
                RenderSystem.depthMask(material.alpha()!=GlbModelAsset.Alpha.BLEND);
                if(material.doubleSided())RenderSystem.disableCull();else RenderSystem.enableCull();
                var texture=context.gpu().texture(material.texture());
                RenderSystem.setShaderTexture(0,context.bindings()==null?texture:context.bindings().texture(primitive.material(),texture));
                var color=material.baseColor();
                program.getUniformOrDefault("BaseColorFactor").set(color.r(),color.g(),color.b(),color.a());
                program.getUniformOrDefault("AlphaMode").set(material.alpha().ordinal());
                program.getUniformOrDefault("AlphaCutoff").set(material.cutoff());
                buffer.begin(VertexFormat.DrawMode.TRIANGLES,VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
                if(pose==null){
                    for(int index:primitive.indices()){
                        var v=primitive.vertices().get(index);
                        buffer.vertex(v.x(),v.y(),v.z()).color(255,255,255,255).texture(v.u(),v.v())
                                .light(context.light()).normal(v.nx(),v.ny(),v.nz()).next();
                    }
                }else{
                    int meshIndex=primitiveIndex(asset,primitive);var mesh=pose.model().meshes().get(meshIndex);
                    for(int i=0;i<mesh.indexCount();i++){
                        int corner=i%3;int slot=pose.reflected(meshIndex)&&corner>0?i+(corner==1?1:-1):i;
                        int v=mesh.index(slot);
                        buffer.vertex(pose.vertex(meshIndex,v,0),pose.vertex(meshIndex,v,1),pose.vertex(meshIndex,v,2))
                                .color(255,255,255,255).texture((float)pose.vertex(meshIndex,v,6),(float)pose.vertex(meshIndex,v,7))
                                .light(context.light()).normal((float)pose.vertex(meshIndex,v,3),(float)pose.vertex(meshIndex,v,4),(float)pose.vertex(meshIndex,v,5)).next();
                    }
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        }finally{
            if(buffer.isBuilding()){buffer.end().release();buffer.clear();}
            BufferRenderer.reset();before.restore();
        }
    }
    private static double distance(GlbModelAsset.Primitive primitive,GlbModelAsset asset,RigFrame pose,GlbRenderContext context){
        if(pose!=null){int mesh=primitiveIndex(asset,primitive),n=pose.model().meshes().get(mesh).vertexCount();double x=0,y=0,z=0;
            for(int v=0;v<n;v++){x+=pose.vertex(mesh,v,0);y+=pose.vertex(mesh,v,1);z+=pose.vertex(mesh,v,2);}return context.cameraInModel().squaredDistanceTo(x/n,y/n,z/n);}
        double x=0,y=0,z=0;for(var v:primitive.vertices()){x+=v.x();y+=v.y();z+=v.z();}
        int n=primitive.vertices().size();return context.cameraInModel().squaredDistanceTo(x/n,y/n,z/n);
    }
    private static int primitiveIndex(GlbModelAsset asset,GlbModelAsset.Primitive primitive){
        // Equal bind-pose geometry may carry different skin weights: use stable identity.
        for(int i=0;i<asset.primitives().size();i++)if(asset.primitives().get(i)==primitive)return i;
        throw new IllegalArgumentException("primitive is not part of this GLB asset");
    }
    /** Restore state that this backend changes, including exceptional draw paths. */
    private static final class State {
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int equation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        final int alphaEquation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int texture0=RenderSystem.getShaderTexture(0),texture2=RenderSystem.getShaderTexture(2);
        final float[] color=RenderSystem.getShaderColor().clone();
        final ShaderProgram shader=RenderSystem.getShader();
        final int activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int bound0,bound1,bound2;
        State(){
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);bound0=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            // ShaderProgram assigns declared samplers consecutively: Sampler2 may use unit 1.
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);bound1=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(GL13.GL_TEXTURE2);bound2=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(activeTexture);
        }
        void restore(){
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.depthMask(mask);RenderSystem.depthFunc(depthFunc);
            RenderSystem.blendEquation(equation);RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(equation!=alphaEquation)GL20.glBlendEquationSeparate(equation,alphaEquation);
            RenderSystem.setShaderTexture(0,texture0);RenderSystem.setShaderTexture(2,texture2);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(bound0);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(bound1);
            RenderSystem.activeTexture(GL13.GL_TEXTURE2);RenderSystem.bindTexture(bound2);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);RenderSystem.setShader(()->shader);
        }
    }
}
