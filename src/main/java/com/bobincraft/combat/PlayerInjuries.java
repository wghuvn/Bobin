package com.bobincraft.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.*;
import net.minecraft.util.*;
import net.fabricmc.fabric.api.event.player.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerInjuries {
    private static final UUID MOVEMENT=UUID.fromString("df3f18cf-a2ae-4225-8033-38b21dff92e3");
    private static final Map<UUID,Integer> DEATH_POSE=new ConcurrentHashMap<>();
    private static final Map<UUID,Long> PRONE_SINCE=new ConcurrentHashMap<>();
    private PlayerInjuries() { }
    public static void setDeathPose(UUID id,int pose){if(pose<=0)DEATH_POSE.remove(id);else DEATH_POSE.put(id,pose&3);}
    public static boolean wasCrawlingBeforeDeath(net.minecraft.entity.LivingEntity entity){
        Long since=PRONE_SINCE.get(entity.getUuid());
        return since!=null&&entity.getWorld()!=null&&entity.getWorld().getTime()-since>1;
    }
    public static int deathPose(net.minecraft.entity.LivingEntity entity){
        int raw=entity instanceof InjuryCarrier carrier?carrier.bobinInjuries().missingLimbs()&HitRegion.ALL_SEVERABLE:0;
        if(entity.isAlive()){
            DEATH_POSE.remove(entity.getUuid());
            if((raw&HitRegion.LEGS)!=0&&entity.getWorld()!=null)PRONE_SINCE.putIfAbsent(entity.getUuid(),entity.getWorld().getTime());
            else PRONE_SINCE.remove(entity.getUuid());
            return 0;
        }
        Integer cached=DEATH_POSE.get(entity.getUuid());
        if(cached!=null&&cached>0)return cached;
        boolean wasCrawling=wasCrawlingBeforeDeath(entity);
        boolean blast=((Entity)entity).getVelocity().lengthSquared()>.12;
        int pose=wasCrawling?GuardInjuryPose.POSE_CRAWL:(blast?GuardInjuryPose.POSE_BLAST:((raw&HitRegion.LEGS)!=0?GuardInjuryPose.POSE_CRAWL:GuardInjuryPose.POSE_FALLEN));
        DEATH_POSE.put(entity.getUuid(),pose);
        return pose;
    }
    public static int missing(net.minecraft.entity.LivingEntity entity){
        int raw=entity instanceof InjuryCarrier carrier?carrier.bobinInjuries().missingLimbs()&HitRegion.ALL_SEVERABLE:0;
        return entity.isAlive()?raw:GuardInjuryPose.pack(raw,deathPose(entity));
    }
    public static boolean prone(net.minecraft.entity.LivingEntity entity){return GuardInjuryPose.prone(missing(entity));}
    public static boolean armMissing(net.minecraft.entity.LivingEntity entity,Arm arm){return (missing(entity)&(arm==Arm.LEFT?1:2))!=0;}
    public static boolean usableHand(PlayerEntity player,Hand hand){return !armMissing(player,hand==Hand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite());}
    public static void mobility(PlayerEntity player){
        deathPose(player);
        var attr=player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);if(attr==null)return;
        double penalty=GuardInjuryPose.speedPenalty(missing(player));var old=attr.getModifier(MOVEMENT);
        if(old!=null&&old.getValue()==penalty)return;
        if(old!=null)attr.removeModifier(MOVEMENT);
        if(penalty!=0)attr.addTemporaryModifier(new EntityAttributeModifier(MOVEMENT,"Limb injury",penalty,EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        if(prone(player)&&!player.hasVehicle()){player.setSprinting(false);player.setPose(net.minecraft.entity.EntityPose.SWIMMING);}
    }
    public static void register(){
        UseItemCallback.EVENT.register((p,w,h)->usableHand(p,h)?TypedActionResult.pass(p.getStackInHand(h)):TypedActionResult.fail(p.getStackInHand(h)));
        AttackEntityCallback.EVENT.register((p,w,h,e,hit)->usableHand(p,h)?ActionResult.PASS:ActionResult.FAIL);
        UseBlockCallback.EVENT.register((p,w,h,hit)->usableHand(p,h)?ActionResult.PASS:ActionResult.FAIL);
        AttackBlockCallback.EVENT.register((p,w,h,pos,side)->usableHand(p,h)?ActionResult.PASS:ActionResult.FAIL);
    }
}
