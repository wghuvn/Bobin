package com.bobincraft.armament.client.resource.fabric;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.glb.*;
import com.bobincraft.armament.client.animation.gltf.*;
import com.bobincraft.armament.client.resource.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;

/** Small opt-in diagnostic asset only. Reload errors do not abort Minecraft resource reload. */
public final class GlbDebugResources implements SimpleSynchronousResourceReloadListener,AutoCloseable {
    public static final WeaponId MODEL=WeaponId.parse("bobin_craft:armament/debug/technical_fixture.glb");
    public static final WeaponId ANIMATION_MODEL=WeaponId.parse("bobin_craft:armament/debug/animation_fixture.glb");
    public record DebugAsset(GlbGpuAsset gpu,AnimationEventsDefinition events,RigSockets sockets) implements AutoCloseable {
        @Override public void close(){gpu.close();}
    }
    private static final Logger LOGGER=LoggerFactory.getLogger("bobin_craft/armament_glb");
    private final ReloadableResourceCache<WeaponId,DebugAsset> cache=new ReloadableResourceCache<>();
    private long uploadSerial;
    private final java.util.Map<WeaponId,String> errors=new java.util.LinkedHashMap<>();
    @Override public Identifier getFabricId(){return new Identifier("bobin_craft","armament_glb_debug");}
    @Override public Collection<Identifier> getFabricDependencies(){return List.of(ResourceReloadListenerKeys.TEXTURES);}
    @Override public void reload(ResourceManager manager){
        RenderSystem.assertOnRenderThread();
        for(WeaponId model:List.of(MODEL,ANIMATION_MODEL))try{
            cache.reload(model,()->{
                GlbModelAsset asset=new JgltfModelLoader().load(model,id->{
                    Identifier resource=new Identifier(id.namespace(),id.path());
                    return manager.getResource(resource).orElseThrow(()->new IOException("missing resource "+resource)).getInputStream();
                });
                AnimationEventsDefinition events=null;RigSockets sockets=null;
                if(model.equals(ANIMATION_MODEL)){
                    if(asset.rig()==null)throw new IOException("animation fixture contains no rig");
                    var rig=WeaponVisualJson.readRig(text(manager,"rig"));var socketDefinition=WeaponVisualJson.readSockets(text(manager,"sockets"));
                    if(!socketDefinition.rigId().equals(rig.id())||rig.nodes().size()!=asset.rig().nodes().size())throw new IOException("sidecar rig ID/node count mismatch");
                    for(var node:rig.nodes()){
                        if(node.nodeIndex()>=asset.rig().nodes().size())throw new IOException("sidecar node index outside GLB");
                        var actual=asset.rig().nodes().get(node.nodeIndex());
                        String parent=actual.parent()<0?null:asset.rig().nodes().get(actual.parent()).name();
                        if(!actual.name().equals(node.name())||!java.util.Objects.equals(parent,node.parent().orElse(null)))throw new IOException("fixture sidecar hierarchy differs from GLB: "+node.name());
                    }
                    sockets=new RigSockets(asset.rig(),rig,socketDefinition);events=WeaponVisualJson.readEvents(text(manager,"events"));
                    new GltfAnimationController(asset.rig()).markers(events); // Validate before any GPU allocation.
                }
                return new DebugAsset(GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(),asset,++uploadSerial),events,sockets);
            });
            errors.remove(model);
            LOGGER.info("GLB resource {} generation {} loaded; live textures {}",model,cache.generation(),GlbGpuAsset.liveTextures());
        }catch(Exception e){errors.put(model,e.getMessage());LOGGER.warn("GLB diagnostic reload failed for {}; retaining last good generation: {}",model,e.getMessage());}
    }
    private static String text(ResourceManager manager,String kind)throws IOException{
        Identifier id=new Identifier("bobin_craft","armament/debug/animation_fixture."+kind+".json");
        try(var stream=manager.getResource(id).orElseThrow(()->new IOException("missing sidecar "+id)).getInputStream()){
            byte[] bytes=stream.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("sidecar exceeds 1 MiB: "+id);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
    }
    public Optional<GlbGpuAsset> current(){return cache.get(MODEL).map(DebugAsset::gpu);}
    public Optional<DebugAsset> animation(){return cache.get(ANIMATION_MODEL);}
    public String status(){return "generation="+cache.generation()+", assets="+cache.size()+", liveTextures="+GlbGpuAsset.liveTextures()+
            (errors.isEmpty()?"":", errors="+errors)+current().map(v->", triangles="+v.asset().triangles()+", bounds="+v.asset().bounds()).orElse("");}
    @Override public void close(){try{cache.close();}catch(Exception e){LOGGER.warn("Closing GLB diagnostic resources failed",e);}}
}
