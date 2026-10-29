package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.api.WeaponId;
import com.bobincraft.armament.client.model.glb.ReloadableResourceCache;
import com.bobincraft.armament.client.presentation.*;
import com.bobincraft.armament.client.resource.fabric.GlbGpuAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One atomic profile/model/socket bundle. Failed reload retains the last good generation. */
public final class FirstPersonResources implements SimpleSynchronousResourceReloadListener,AutoCloseable {
    public static final WeaponId PROFILE=WeaponId.parse("bobin_craft:armament/debug/first_person_fixture.profile.json");
    private static final WeaponId APPEARANCE=WeaponId.parse("bobin_craft:armament/debug/first_person_hands.appearance.json");
    public record Bundle(FirstPersonAsset cpu,GlbGpuAsset gpu,PlayerSkinBinding skin) implements AutoCloseable{@Override public void close(){gpu.close();}}
    private record Variants(Map<String,Bundle> values) implements AutoCloseable {
        private Variants{values=Map.copyOf(values);}
        @Override public void close(){for(Bundle bundle:values.values())bundle.close();}
    }
    private final ReloadableResourceCache<WeaponId,Variants> cache=new ReloadableResourceCache<>();
    private long uploads;private String error="not loaded";
    @Override public Identifier getFabricId(){return new Identifier("bobin_craft","armament_first_person_debug");}
    @Override public Collection<Identifier> getFabricDependencies(){return List.of(ResourceReloadListenerKeys.TEXTURES);}
    @Override public void reload(ResourceManager manager){
        RenderSystem.assertOnRenderThread();
        try{
            cache.reload(APPEARANCE,()->{
                com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource source=id->{
                    Identifier key=new Identifier(id.namespace(),id.path());
                    return manager.getResource(key).orElseThrow(()->new IOException("missing resource "+key)).getInputStream();
                };
                PlayerHandAppearance appearance;
                try(InputStream input=source.open(APPEARANCE)){
                    byte[] bytes=input.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("hand appearance exceeds 1 MiB");
                    appearance=PlayerHandAppearance.read(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
                }
                Map<String,FirstPersonAsset> assets=new LinkedHashMap<>();Map<String,PlayerSkinBinding> bindings=new LinkedHashMap<>();
                for(String variant:List.of("default","slim")){
                    var cpu=FirstPersonAsset.load(appearance.profiles().get(variant),source);
                    assets.put(variant,cpu);bindings.put(variant,new PlayerSkinBinding(appearance.bind(cpu.model(),variant)));
                }
                // Both CPU variants must validate before any upload. A partial GPU
                // failure closes the new generation and preserves both previous variants.
                Map<String,Bundle> loaded=new LinkedHashMap<>();
                try{
                    for(var entry:assets.entrySet())loaded.put(entry.getKey(),new Bundle(entry.getValue(),GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(),entry.getValue().model(),++uploads),bindings.get(entry.getKey())));
                    return new Variants(loaded);
                }catch(Exception e){for(Bundle bundle:loaded.values())bundle.close();throw e;}
            });error="";
        }catch(Exception e){error=e.getMessage();LoggerFactory.getLogger("bobin_craft/armament_fp").warn("First-person reload failed; preserving last good bundle: {}",error);}
    }
    public Optional<Bundle> current(){return current("default");}
    public Optional<Bundle> current(String model){return cache.get(APPEARANCE).map(variants->variants.values().get("slim".equals(model)?"slim":"default"));}
    public String status(){return "fpGeneration="+cache.generation()+", fpVariants="+cache.get(APPEARANCE).map(variants->variants.values().size()).orElse(0)+", liveTextures="+GlbGpuAsset.liveTextures()+(error.isEmpty()?"":", resourceError="+error);}
    @Override public void close(){try{cache.close();}catch(Exception e){LoggerFactory.getLogger("bobin_craft/armament_fp").warn("Closing first-person resources",e);}}
}
