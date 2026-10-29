package com.bobincraft.mixin;
import com.bobincraft.combat.PlayerInjuries;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemRenderer.class)
public abstract class InjuredFirstPersonMixin {
    @Inject(method="renderFirstPersonItem",at=@At("HEAD"),cancellable=true)
    private void bobin$missingHand(AbstractClientPlayerEntity p,float delta,float pitch,Hand hand,float swing,ItemStack stack,float equip,MatrixStack m,VertexConsumerProvider out,int light,CallbackInfo ci){if(!PlayerInjuries.usableHand(p,hand))ci.cancel();}
}
