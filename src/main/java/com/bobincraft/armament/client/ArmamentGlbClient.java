package com.bobincraft.armament.client;

import com.bobincraft.armament.client.debug.GlbDebugPreview;
import com.bobincraft.armament.client.render.fabric.FabricGlbRenderer;
import com.bobincraft.armament.client.resource.fabric.GlbDebugResources;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/** Diagnostic is disabled by default. Enable with -Dbobin.armament.glbDebug=true. */
public final class ArmamentGlbClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        if(!Boolean.getBoolean("bobin.armament.glbDebug"))return;
        var resources=new GlbDebugResources();var renderer=new FabricGlbRenderer();
        CoreShaderRegistrationCallback.EVENT.register(context->{
            renderer.shader(null);
            try{context.register(new Identifier("bobin_craft","armament_static_glb"),VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,renderer::shader);}
            catch(IOException e){LoggerFactory.getLogger("bobin_craft/armament_glb").warn("GLB preview shader unavailable",e);}
        });
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(resources);
        new GlbDebugPreview(resources,renderer).register();
    }
}
