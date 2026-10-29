package com.bobincraft.combat;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Server chooses the exposure; clients receive only the bounded sensory effect. */
public final class Concussion {
    public static final Identifier PACKET=new Identifier("bobin_craft","concussion");
    private Concussion() { }
    public static float intensity(double distance,double exposure){
        if(!Double.isFinite(distance)||!Double.isFinite(exposure)||distance<0)return 0;
        return (float)Math.max(0,Math.min(1,(1-distance/18)*Math.max(0,Math.min(1,exposure))));
    }
    public static void apply(Entity target,double distance,double exposure){
        if(!(target instanceof LivingEntity living)||!InjurySystem.vulnerable(living))return;
        float intensity=intensity(distance,exposure);if(intensity<.06)return;
        if(intensity>.15)living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,(int)(15+intensity*45),intensity>.6?1:0,false,false));
        if(living instanceof ServerPlayerEntity p&&ServerPlayNetworking.canSend(p,PACKET)){
            var b=PacketByteBufs.create();b.writeFloat(intensity);b.writeInt((int)(40+intensity*120));ServerPlayNetworking.send(p,PACKET,b);
        }
    }
}
