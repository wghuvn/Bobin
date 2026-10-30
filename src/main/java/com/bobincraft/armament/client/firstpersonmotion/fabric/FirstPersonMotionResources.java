package com.bobincraft.armament.client.firstpersonmotion.fabric;

import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionAssets;
import com.bobincraft.armament.client.firstpersonmotion.FirstPersonMotionController;
import com.bobincraft.armament.client.model.glb.ReloadableResourceCache;
import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.presentation.fabric.FirstPersonResources.Bundle;
import com.bobincraft.armament.client.resource.fabric.GlbGpuAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import java.io.IOException;
import java.util.*;

/** Atomic optional extension. A failed load keeps the last good generation or the approved asset. */
public final class FirstPersonMotionResources implements SimpleSynchronousResourceReloadListener,AutoCloseable {
    private record Variants(Map<String,Bundle> values) implements AutoCloseable {
        private Variants {values=Map.copyOf(values);}
        @Override public void close(){values.values().forEach(Bundle::close);}
    }
    private static FirstPersonMotionResources active;
    private final ReloadableResourceCache<String,Variants> cache=new ReloadableResourceCache<>();
    private long uploads;
    public FirstPersonMotionResources(){active=this;}
    @Override public Identifier getFabricId(){return new Identifier("bobin_craft","armament_first_person_motion");}
    @Override public Collection<Identifier> getFabricDependencies(){return List.of(ResourceReloadListenerKeys.TEXTURES);}
    @Override public void reload(ResourceManager manager){
        RenderSystem.assertOnRenderThread();
        try{
            cache.reload("motion",()->{
                com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource source=id->manager.getResource(new Identifier(id.namespace(),id.path()))
                    .orElseThrow(()->new IOException("missing "+id)).getInputStream();
                Map<String,FirstPersonAsset> assets=new LinkedHashMap<>();
                for(String variant:List.of("default","slim")){
                    var cpu=FirstPersonAsset.load(FirstPersonMotionAssets.PROFILES.get(variant),source);
                    if(!FirstPersonMotionController.supported(cpu.model().rig()))throw new IOException("missing FPS aim/sprint clips: "+variant);
                    assets.put(variant,cpu);
                }
                Map<String,Bundle> loaded=new LinkedHashMap<>();
                try{
                    for(var entry:assets.entrySet()){
                        var arms=new VanillaArmAttachments(entry.getValue(),entry.getKey());
                        loaded.put(entry.getKey(),new Bundle(entry.getValue(),GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(),entry.getValue().model(),++uploads),arms));
                    }
                    return new Variants(loaded);
                }catch(Exception e){loaded.values().forEach(Bundle::close);throw e;}
            });
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("bobin_craft/armament_fp_motion").warn("Keeping last good FPS motion assets; approved FPS remains available",e);}
    }
    public static Optional<Bundle> current(String variant){
        return active==null?Optional.empty():active.cache.get("motion").map(v->v.values().get("slim".equals(variant)?"slim":"default"));
    }
    @Override public void close(){
        if(active==this)active=null;
        try{cache.close();}catch(Exception e){throw new IllegalStateException(e);}
    }
}
