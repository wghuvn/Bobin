package com.bobincraft.armament.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read vanilla's interpolated FOV. The first-person adapter passes changingFov=false for hands. */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccess {
    @Invoker("getFov") double bobin$armamentFov(Camera camera,float tickDelta,boolean changingFov);
}
