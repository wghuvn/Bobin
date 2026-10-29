package com.bobincraft.mixin;

import com.bobincraft.raid.HubAudio;
import com.bobincraft.raid.HubNpcInteraction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native quick trades bypass the entity's silent flag; supply the camp's own issue confirmation. */
@Mixin(MerchantEntity.class)
public abstract class HubMerchantSoundMixin {
    @Inject(method = "getYesSound", at = @At("HEAD"), cancellable = true)
    private void bobin$serviceConfirmation(CallbackInfoReturnable<SoundEvent> cir) {
        HubNpcInteraction.Role role = HubNpcInteraction.roleOf((Entity) (Object) this);
        if (role != null && role.service()) {
            cir.setReturnValue(HubNpcInteraction.reserveVoice((Entity) (Object) this)
                    ? HubAudio.soundForRole(role, "accept") : SoundEvents.INTENTIONALLY_EMPTY);
        }
    }
}
