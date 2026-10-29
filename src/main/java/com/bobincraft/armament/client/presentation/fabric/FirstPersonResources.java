package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.glb.ReloadableResourceCache;
import com.bobincraft.armament.client.presentation.FirstPersonAsset;
import com.bobincraft.armament.client.resource.fabric.GlbGpuAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;

/** One atomic profile/model/socket bundle. Failed reload retains the last good generation. */
public final class FirstPersonResources implements SimpleSynchronousResourceReloadListener,AutoCloseable {
    public static final WeaponId PROFILE=WeaponId.parse("bobin_craft:armament/debug/first_person_fixture.profile.json");
    public record Bundle(FirstPersonAsset cpu,GlbGpuAsset gpu) implements AutoCloseable{@Override public void close(){gpu.close();}}
    private final ReloadableResourceCache<WeaponId,Bundle> cache=new ReloadableResourceCache<>();
    private long uploads;private String error="not loaded";
    @Override public Identifier getFabricId(){return new Identifier("bobin_craft","armament_first_person_debug");}
    @Override public Collection<Identifier> getFabricDependencies(){return List.of(ResourceReloadListenerKeys.TEXTURES);}
    @Override public void reload(ResourceManager manager){
        RenderSystem.assertOnRenderThread();
        try{
            cache.reload(PROFILE,()->{
                FirstPersonAsset cpu=FirstPersonAsset.load(PROFILE,id->{
                    Identifier key=new Identifier(id.namespace(),id.path());
                    return manager.getResource(key).orElseThrow(()->new IOException("missing resource "+key)).getInputStream();
                });
                return new Bundle(cpu,GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(),cpu.model(),++uploads));
            });error="";
        }catch(Exception e){error=e.getMessage();LoggerFactory.getLogger("bobin_craft/armament_fp").warn("First-person reload failed; preserving last good bundle: {}",error);}
    }
    public Optional<Bundle> current(){return cache.get(PROFILE);}
    public String status(){return "fpGeneration="+cache.generation()+", fpAssets="+cache.size()+", liveTextures="+GlbGpuAsset.liveTextures()+(error.isEmpty()?"":", resourceError="+error);}
    @Override public void close(){try{cache.close();}catch(Exception e){LoggerFactory.getLogger("bobin_craft/armament_fp").warn("Closing first-person resources",e);}}
}
