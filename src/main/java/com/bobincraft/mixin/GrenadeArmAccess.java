package com.bobincraft.mixin;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface GrenadeArmAccess {
    @Invoker("renderArmHoldingItem")
    void bobin_craft$grenadeArm(MatrixStack matrices,VertexConsumerProvider out,int light,float equip,float swing,Arm arm);
}
