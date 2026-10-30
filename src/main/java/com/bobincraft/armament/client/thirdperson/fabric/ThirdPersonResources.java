package com.bobincraft.armament.client.thirdperson.fabric;

import com.bobincraft.armament.client.model.glb.ReloadableResourceCache;
import com.bobincraft.armament.client.model.WeaponModelLoader;
import com.bobincraft.armament.client.resource.fabric.GlbGpuAsset;
import com.bobincraft.armament.client.thirdperson.ThirdPersonAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.*;

/** Atomic reload of both Steve/Alex variants. Failed reload retains the last good generation. */
public final class ThirdPersonResources implements SimpleSynchronousResourceReloadListener, AutoCloseable {
    public record Bundle(ThirdPersonAsset cpu,GlbGpuAsset gpu) implements AutoCloseable {
        @Override public void close(){gpu.close();}
    }
    private record Variants(Map<String,Bundle> values) implements AutoCloseable {
        private Variants { values=Map.copyOf(values); }
        @Override public void close(){values.values().forEach(Bundle::close);}
    }
    private final ReloadableResourceCache<String,Variants> cache=new ReloadableResourceCache<>();
    private long uploads;
    private String error="not loaded";
    @Override public Identifier getFabricId(){return new Identifier("bobin_craft","armament_third_person_debug");}
    @Override public Collection<Identifier> getFabricDependencies(){return List.of(ResourceReloadListenerKeys.TEXTURES);}
    @Override public void reload(ResourceManager manager){
        RenderSystem.assertOnRenderThread();
        try {
            cache.reload("tp",()->{
                WeaponModelLoader.ResourceSource source=id->manager.getResource(new Identifier(id.namespace(),id.path()))
                        .orElseThrow(()->new IOException("missing "+id)).getInputStream();
                var normal=ThirdPersonAsset.load(source,false);var slim=ThirdPersonAsset.load(source,true);
                Map<String,Bundle> loaded=new LinkedHashMap<>();
                try {
                    for(var e:Map.of("default",normal,"slim",slim).entrySet())
                        loaded.put(e.getKey(),new Bundle(e.getValue(),GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(),e.getValue().model(),++uploads)));
                    return new Variants(loaded);
                } catch(Exception e){loaded.values().forEach(Bundle::close);throw e;}
            });
            error="";
        }catch(Exception e){error=e.getMessage();org.slf4j.LoggerFactory.getLogger("bobin_craft/armament_tp").warn("Keeping last good 3P resources",e);}
    }
    public Optional<Bundle> current(String variant){return cache.get("tp").map(v->v.values().get("slim".equals(variant)?"slim":"default"));}
    public String status(){return "tpGeneration="+cache.generation()+(error.isEmpty()?"":", resourceError="+error);}
    @Override public void close(){try{cache.close();}catch(Exception e){throw new IllegalStateException(e);}}
}
