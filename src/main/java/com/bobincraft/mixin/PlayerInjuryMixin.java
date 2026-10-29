package com.bobincraft.mixin;

import com.bobincraft.combat.InjuryCarrier;
import com.bobincraft.combat.InjuryPersistence;
import com.bobincraft.combat.InjuryState;
import com.bobincraft.combat.InjurySystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerInjuryMixin implements InjuryCarrier, com.bobincraft.combat.PlayerInjurySync {
    @Unique private final InjuryState bobin$injuries = new InjuryState();

    @Unique private static final net.minecraft.entity.data.TrackedData<Integer> bobin$limbs = net.minecraft.entity.data.DataTracker.registerData(PlayerEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);
    @Inject(method="initDataTracker", at=@At("TAIL"))
    private void bobin$trackLimbs(CallbackInfo ci){((PlayerEntity)(Object)this).getDataTracker().startTracking(bobin$limbs,0);}
    @Override public InjuryState bobinInjuries() {
        PlayerEntity p=(PlayerEntity)(Object)this;
        if(p.getWorld().isClient){int mask=p.getDataTracker().get(bobin$limbs);if(bobin$injuries.missingLimbs()!=mask){var old=bobin$injuries.snapshot();bobin$injuries.restore(new InjuryState.Snapshot(old.severity(),old.remainingTicks(),old.faceSeverity(),mask));}}
        return bobin$injuries;
    }
    @Override public void bobinSyncLimbs(){((PlayerEntity)(Object)this).getDataTracker().set(bobin$limbs,bobin$injuries.missingLimbs());}
    @Inject(method="updatePose",at=@At("HEAD"),cancellable=true)
    private void bobin$crawlPose(CallbackInfo ci){PlayerEntity p=(PlayerEntity)(Object)this;if(com.bobincraft.combat.PlayerInjuries.prone(p)&&!p.hasVehicle()&&!p.isSleeping()&&!p.isFallFlying()){p.setPose(net.minecraft.entity.EntityPose.SWIMMING);ci.cancel();}}
    @Inject(method="jump",at=@At("HEAD"),cancellable=true)
    private void bobin$noLegJump(CallbackInfo ci){if(com.bobincraft.combat.PlayerInjuries.prone((PlayerEntity)(Object)this))ci.cancel();}


    @Inject(method = "tick", at = @At("TAIL"))
    private void bobin$tickInjuries(CallbackInfo ci) { InjurySystem.tick((PlayerEntity) (Object) this); }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void bobin$writeInjuries(NbtCompound nbt, CallbackInfo ci) { InjuryPersistence.write(bobin$injuries, nbt); }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void bobin$readInjuries(NbtCompound nbt, CallbackInfo ci) { InjuryPersistence.read(bobin$injuries, nbt); }
}
