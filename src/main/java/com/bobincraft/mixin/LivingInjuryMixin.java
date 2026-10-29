package com.bobincraft.mixin;
import com.bobincraft.combat.PlayerInjuries;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class LivingInjuryMixin {
    @Inject(method="getLeaningPitch",at=@At("HEAD"),cancellable=true)
    private void bobin$crawling(float delta,CallbackInfoReturnable<Float> cir){var e=(LivingEntity)(Object)this;if(e instanceof PlayerEntity&&PlayerInjuries.prone(e)&&!e.hasVehicle())cir.setReturnValue(1f);}
}
