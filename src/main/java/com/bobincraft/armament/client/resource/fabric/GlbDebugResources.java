package com.bobincraft.armament.client.resource.fabric;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.glb.*;
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

/** Small opt-in diagnostic asset only. Reload errors do not abort Minecraft resource reload. */
public final class GlbDebugResources implements SimpleSynchronousResourceReloadListener,AutoCloseable {
    public static final WeaponId MODEL=WeaponId.parse("bobin_craft:armament/debug/technical_fixture.glb");
    private static final Logger LOGGER=LoggerFactory.getLogger("bobin_craft/armament_glb");
    private final ReloadableResourceCache<WeaponId,GlbGpuAsset> cache=new ReloadableResourceCache<>();
    private long uploadSerial;
    private String lastError="not loaded";
    @Override public Identifier getFabricId(){return new Identifier("bobin_craft","armament_glb_debug");}
    @Override public Collection<Identifier> getFabricDependencies(){return List.of(ResourceReloadListenerKeys.TEXTURES);}
    @Override public void reload(ResourceManager manager){
        RenderSystem.assertOnRenderThread();
        try{
            cache.reload(MODEL,()->{
                GlbModelAsset asset=new JgltfModelLoader().load(MODEL,id->{
                    Identifier resource=new Identifier(id.namespace(),id.path());
                    return manager.getResource(resource).orElseThrow(()->new IOException("missing resource "+resource)).getInputStream();
                });
                return GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(),asset,++uploadSerial);
            });
            lastError="";
            LOGGER.info("GLB resource generation {} loaded: {} triangles; live textures {}",cache.generation(),
                    current().orElseThrow().asset().triangles(),GlbGpuAsset.liveTextures());
        }catch(Exception e){lastError=e.getMessage();LOGGER.warn("GLB diagnostic reload failed; retaining last good generation: {}",lastError);}
    }
    public Optional<GlbGpuAsset> current(){return cache.get(MODEL);}
    public String status(){return "generation="+cache.generation()+", assets="+cache.size()+", liveTextures="+GlbGpuAsset.liveTextures()+
            (lastError.isEmpty()?"":", error="+lastError)+current().map(v->", triangles="+v.asset().triangles()+", bounds="+v.asset().bounds()).orElse("");}
    @Override public void close(){try{cache.close();}catch(Exception e){LOGGER.warn("Closing GLB diagnostic resources failed",e);}}
}
