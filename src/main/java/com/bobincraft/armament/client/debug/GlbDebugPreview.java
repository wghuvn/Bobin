package com.bobincraft.armament.client.debug;

import com.bobincraft.armament.client.resource.fabric.GlbDebugResources;
import com.bobincraft.armament.client.render.fabric.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Opt-in client-only world preview: no item/block/entity registration or server edits. */
public final class GlbDebugPreview {
    private Vec3d anchor;
    private RegistryKey<World> dimension;
    private final GlbDebugResources resources;
    private final FabricGlbRenderer renderer;
    private final GlbAnimationPreview animations;
    public GlbDebugPreview(GlbDebugResources resources,FabricGlbRenderer renderer){
        this.resources=resources;this.renderer=renderer;animations=new GlbAnimationPreview(resources,renderer,()->anchor=null);
    }
    public void register(){
        animations.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(literal("bobin_glb")
            .then(literal("show").executes(ctx->{
                var client=MinecraftClient.getInstance();if(client.player==null||client.world==null)return 0;
                double yaw=Math.toRadians(client.player.getYaw());Vec3d horizontal=new Vec3d(-Math.sin(yaw),0,Math.cos(yaw));
                animations.hide();anchor=client.player.getPos().add(horizontal.multiply(3));dimension=client.world.getRegistryKey();
                if(resources.current().isEmpty())resources.reload(client.getResourceManager());
                ctx.getSource().sendFeedback(Text.literal("GLB preview: 1 metre = 1 block; forward indicator points north (-Z). "+resources.status()+", shader="+renderer.hasShader()));return 1;
            }))
            .then(literal("hide").executes(ctx->{anchor=null;animations.hide();return 1;}))
            .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(resources.status()+", shader="+renderer.hasShader()));return 1;}))
            .then(literal("reload").executes(ctx->{MinecraftClient.getInstance().reloadResources();return 1;}))));
        WorldRenderEvents.LAST.register(this::draw);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{anchor=null;dimension=null;});
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->resources.close());
    }
    private void draw(WorldRenderContext context){
        var client=MinecraftClient.getInstance();
        if(anchor==null||client.world==null||!client.world.getRegistryKey().equals(dimension))return;
        resources.current().ifPresent(gpu->{
            var matrices=context.matrixStack();Vec3d camera=context.camera().getPos();
            matrices.push();
            try{
                matrices.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);
                int light=WorldRenderer.getLightmapCoordinates(client.world,BlockPos.ofFloored(anchor.add(0,0.5,0)));
                Matrix4f viewProjection=new Matrix4f(context.projectionMatrix()).mul(matrices.peek().getPositionMatrix());
                renderer.render(gpu.asset(),null,new GlbRenderContext(viewProjection,light,camera.subtract(anchor),gpu));
            }finally{matrices.pop();}
        });
    }
}
