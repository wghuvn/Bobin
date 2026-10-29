package com.bobincraft.mixin;
import com.bobincraft.combat.PlayerInjuries;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemFeatureRenderer.class)
public abstract class InjuredHeldItemMixin {
    @Inject(method="renderItem",at=@At("HEAD"),cancellable=true)
    private void bobin$missingHand(LivingEntity e,ItemStack stack,ModelTransformationMode mode,Arm arm,MatrixStack m,VertexConsumerProvider out,int light,CallbackInfo ci){if(PlayerInjuries.armMissing(e,arm))ci.cancel();}
}
