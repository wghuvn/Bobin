package com.bobincraft.armament.client.debug;

import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.resource.fabric.*;
import com.bobincraft.armament.client.render.fabric.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;
import java.util.function.Consumer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/** Isolated two-instance animation preview. No entity, held item or gameplay integration. */
final class GlbAnimationPreview {
    private final GlbDebugResources resources;
    private final FabricGlbRenderer renderer;
    private final Runnable hideStatic;
    private Vec3d anchor;
    private RegistryKey<World> dimension;
    private GlbDebugResources.DebugAsset generation;
    private GltfAnimationController first,second;
    private long markerCount;
    private String lastMarker="none",clip="bind_pose";
    private final double[] socket=new double[16];
    private final Consumer<GltfAnimationController.CosmeticEvent> markerSink=event->{
        markerCount++;lastMarker=event.instance()+"/"+event.clip()+"/"+event.marker()+"/loop="+event.loop();
    };
    GlbAnimationPreview(GlbDebugResources resources,FabricGlbRenderer renderer,Runnable hideStatic){this.resources=resources;this.renderer=renderer;this.hideStatic=hideStatic;}
    void register(){
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(literal("bobin_glb").then(literal("anim")
            .then(literal("list").executes(ctx->{
                var asset=loaded();if(asset==null){ctx.getSource().sendError(Text.literal(resources.status()));return 0;}
                for(var c:asset.gpu().asset().rig().clips())ctx.getSource().sendFeedback(Text.literal(c.name()+" ("+c.duration()+" s, "+c.channels().size()+" channels)"));return 1;
            }))
            .then(literal("play").then(argument("clip",StringArgumentType.string()).suggests((ctx,builder)->{
                resources.animation().ifPresent(asset->asset.gpu().asset().rig().clips().forEach(c->builder.suggest(c.name())));return builder.buildFuture();
            }).executes(ctx->{
                var client=MinecraftClient.getInstance();if(client.world==null||client.player==null)return 0;
                var asset=loaded();if(asset==null){ctx.getSource().sendError(Text.literal(resources.status()));return 0;}
                try{
                    String requested=StringArgumentType.getString(ctx,"clip");asset.gpu().asset().rig().clip(requested);
                    ensure(asset);double now=client.world.getTime()/20.0;
                    first.play(requested,GltfAnimationController.Mode.LOOP,now,0.25);
                    second.play(requested,GltfAnimationController.Mode.LOOP,now,0.25);second.seek(0,0.5,now);clip=requested;
                    if(anchor==null||!client.world.getRegistryKey().equals(dimension)){double yaw=Math.toRadians(client.player.getYaw());anchor=client.player.getPos().add(-3*Math.sin(yaw),0,3*Math.cos(yaw));}
                    dimension=client.world.getRegistryKey();hideStatic.run();
                    ctx.getSource().sendFeedback(Text.literal("GLB animation: "+requested+", two independent instances, second phase +0.5s, crossfade 0.25s. "+status()));return 1;
                }catch(IllegalArgumentException e){ctx.getSource().sendError(Text.literal(e.getMessage()));return 0;}
            })))
            .then(literal("stop").executes(ctx->{var client=MinecraftClient.getInstance();if(client.world!=null&&first!=null){double now=client.world.getTime()/20.0;first.stop(now,0.25);second.stop(now,0.25);clip="bind_pose";}return 1;}))
            .then(literal("status").executes(ctx->{ctx.getSource().sendFeedback(Text.literal(status()));return 1;})))));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(anchor==null||client.world==null||!client.world.getRegistryKey().equals(dimension))return;
            resources.animation().ifPresent(asset->{ensure(asset);long tick=client.world.getTime();first.advance(tick,markerSink);second.advance(tick,markerSink);});
        });
        WorldRenderEvents.LAST.register(this::draw);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->hide());
    }
    private GlbDebugResources.DebugAsset loaded(){
        if(resources.animation().isEmpty())resources.reload(MinecraftClient.getInstance().getResourceManager());
        return resources.animation().orElse(null);
    }
    private void ensure(GlbDebugResources.DebugAsset asset){
        if(generation==asset)return;generation=asset;
        first=new GltfAnimationController(asset.gpu().asset().rig());second=new GltfAnimationController(asset.gpu().asset().rig());
        first.markers(asset.events());second.markers(asset.events());clip="bind_pose";markerCount=0;lastMarker="none";
    }
    void hide(){anchor=null;dimension=null;generation=null;first=null;second=null;}
    String status(){
        var client=MinecraftClient.getInstance();if(first==null||client.world==null)return "animation preview inactive; "+resources.status();
        double now=client.world.getTime()/20.0;
        generation.sockets().sample("debug",first.sampleSeconds(now),socket);
        return "clip="+clip+", state="+first.state(now)+", instances=2, markers="+markerCount+", last="+lastMarker+
            ", socket=["+socket[12]+", "+socket[13]+", "+socket[14]+"], dropped="+(first.droppedEvents()+second.droppedEvents())+
            ", shader="+renderer.hasShader()+", "+resources.status();
    }
    private void draw(WorldRenderContext context){
        var client=MinecraftClient.getInstance();
        if(anchor==null||client.world==null||!client.world.getRegistryKey().equals(dimension)||!renderer.hasShader())return;
        resources.animation().ifPresent(asset->{
            ensure(asset);
            try{
                drawOne(context,asset.gpu(),first,anchor);
                drawOne(context,asset.gpu(),second,anchor.add(1.6,0,0));
            }catch(RuntimeException e){LoggerFactory.getLogger("bobin_craft/armament_glb").warn("GLB animation preview disabled after evaluation failure",e);hide();}
        });
    }
    private void drawOne(WorldRenderContext context,GlbGpuAsset gpu,GltfAnimationController controller,Vec3d position){
        var client=MinecraftClient.getInstance();var matrices=context.matrixStack();Vec3d camera=context.camera().getPos();
        matrices.push();
        try{
            matrices.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
            int light=WorldRenderer.getLightmapCoordinates(client.world,BlockPos.ofFloored(position.add(0,0.5,0)));
            Matrix4f vp=new Matrix4f(context.projectionMatrix()).mul(matrices.peek().getPositionMatrix());
            renderer.render(gpu.asset(),controller.sample(client.world.getTime(),context.tickDelta()),new GlbRenderContext(vp,light,camera.subtract(position),gpu));
        }finally{matrices.pop();}
    }
}
