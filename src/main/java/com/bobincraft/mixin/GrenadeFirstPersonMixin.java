package com.bobincraft.mixin;

import com.bobincraft.vehicle.client.GrenadeClient;
import com.bobincraft.weapon.grom7.client.Grom7Client;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class GrenadeFirstPersonMixin {
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void bobin_craft$grenadeSequence(AbstractClientPlayerEntity player,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack matrices,VertexConsumerProvider out,int light,CallbackInfo ci){
        HeldItemRenderer self = (HeldItemRenderer)(Object)this;
        if(GrenadeClient.render(self,player,delta,hand,matrices,out,light))ci.cancel();
        else if(Grom7Client.renderFirstPerson(self,player,delta,hand,matrices,out,light))ci.cancel();
    }
}
