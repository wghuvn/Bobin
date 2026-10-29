package com.bobincraft.armament.client.mixin;

import com.bobincraft.armament.client.presentation.fabric.FirstPersonDiagnostic;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only an explicitly enabled empty-hands diagnostic can cancel this one hand pass. */
@Mixin(HeldItemRenderer.class)
public abstract class FirstPersonHeldItemMixin {
    @Shadow private ItemStack mainHand;
    @Shadow private ItemStack offHand;
    @Inject(method="renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",at=@At("HEAD"),cancellable=true)
    private void bobin$firstPersonGlb(float delta,MatrixStack matrices,VertexConsumerProvider.Immediate consumers,ClientPlayerEntity player,int light,CallbackInfo ci){
        if(FirstPersonDiagnostic.render(delta,matrices,consumers,player,light,mainHand.isEmpty()&&offHand.isEmpty()))ci.cancel();
    }
}
