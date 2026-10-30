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
import java.io.IOException;
import java.util.*;

/** Atomic gauge/anchor bundles. Hands come from Minecraft's live player model, never GLB. */
public final class FirstPersonResources implements SimpleSynchronousResourceReloadListener, AutoCloseable {
    public static final WeaponId PROFILE = VanillaArmAttachments.PROFILES.get("default");
    public record Bundle(FirstPersonAsset cpu, GlbGpuAsset gpu, VanillaArmAttachments arms) implements AutoCloseable {
        @Override public void close() { gpu.close(); }
    }
    private record Variants(Map<String, Bundle> values) implements AutoCloseable {
        private Variants { values = Map.copyOf(values); }
        @Override public void close() { for (Bundle bundle : values.values()) bundle.close(); }
    }
    private final ReloadableResourceCache<WeaponId, Variants> cache = new ReloadableResourceCache<>();
    private long uploads;
    private String error = "not loaded";
    @Override public Identifier getFabricId() { return new Identifier("bobin_craft", "armament_first_person_debug"); }
    @Override public Collection<Identifier> getFabricDependencies() { return List.of(ResourceReloadListenerKeys.TEXTURES); }
    @Override public void reload(ResourceManager manager) {
        RenderSystem.assertOnRenderThread();
        try {
            cache.reload(PROFILE, () -> {
                com.bobincraft.armament.client.model.WeaponModelLoader.ResourceSource source = id -> {
                    Identifier key = new Identifier(id.namespace(), id.path());
                    return manager.getResource(key).orElseThrow(() -> new IOException("missing resource " + key)).getInputStream();
                };
                Map<String, FirstPersonAsset> assets = new LinkedHashMap<>();
                Map<String, VanillaArmAttachments> anchors = new LinkedHashMap<>();
                for (String variant : List.of("default", "slim")) {
                    var cpu = FirstPersonAsset.load(VanillaArmAttachments.PROFILES.get(variant), source);
                    assets.put(variant, cpu);
                    anchors.put(variant, new VanillaArmAttachments(cpu, variant));
                }
                Map<String, Bundle> loaded = new LinkedHashMap<>();
                try {
                    for (var entry : assets.entrySet())
                        loaded.put(entry.getKey(), new Bundle(entry.getValue(),
                                GlbGpuAsset.upload(MinecraftClient.getInstance().getTextureManager(), entry.getValue().model(), ++uploads),
                                anchors.get(entry.getKey())));
                    return new Variants(loaded);
                } catch (Exception e) {
                    for (Bundle bundle : loaded.values()) bundle.close();
                    throw e;
                }
            });
            error = "";
        } catch (Exception e) {
            error = e.getMessage();
            LoggerFactory.getLogger("bobin_craft/armament_fp").warn("Vanilla-arm resource reload failed; preserving last good bundle: {}", error);
        }
    }
    public Optional<Bundle> current() { return current("default"); }
    public Optional<Bundle> current(String model) {
        return cache.get(PROFILE).map(variants -> variants.values().get("slim".equals(model) ? "slim" : "default"));
    }
    public String status() {
        return "fpHands=vanillaModelParts, fpGeneration=" + cache.generation() + ", fpVariants=" +
                cache.get(PROFILE).map(variants -> variants.values().size()).orElse(0) + ", liveTextures=" +
                GlbGpuAsset.liveTextures() + (error.isEmpty() ? "" : ", resourceError=" + error);
    }
    @Override public void close() {
        try { cache.close(); }
        catch (Exception e) { LoggerFactory.getLogger("bobin_craft/armament_fp").warn("Closing first-person resources", e); }
    }
}
