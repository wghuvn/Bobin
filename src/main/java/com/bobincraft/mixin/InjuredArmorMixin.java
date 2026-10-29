package com.bobincraft.mixin;
import com.bobincraft.combat.PlayerInjuries;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ArmorFeatureRenderer.class)
public abstract class InjuredArmorMixin {
    @org.spongepowered.asm.mixin.Unique private LivingEntity bobin$wearer;
    @Inject(method="renderArmor",at=@At("HEAD"))
    private void bobin$wearer(net.minecraft.client.util.math.MatrixStack m,net.minecraft.client.render.VertexConsumerProvider out,LivingEntity e,EquipmentSlot slot,int light,BipedEntityModel<?> model,CallbackInfo ci){bobin$wearer=e;}
    @Inject(method="setVisible",at=@At("TAIL"))
    private void bobin$hideArmor(BipedEntityModel<?> model,EquipmentSlot slot,CallbackInfo ci){if(bobin$wearer==null)return;int mask=PlayerInjuries.missing(bobin$wearer);model.leftArm.visible&=(mask&1)==0;model.rightArm.visible&=(mask&2)==0;model.leftLeg.visible&=(mask&4)==0;model.rightLeg.visible&=(mask&8)==0;}
}
