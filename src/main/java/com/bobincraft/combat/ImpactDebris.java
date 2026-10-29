package com.bobincraft.combat;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Cosmetic, bounded material fragments. A packet never authorizes damage or terrain edits. */
public final class ImpactDebris {
    public static final Identifier PACKET=new Identifier("bobin_craft","impact_debris");
    private ImpactDebris() { }
    public static void shot(ServerWorld w,Entity shooter,Vec3d reported,double range){
        if(!Double.isFinite(reported.x)||!Double.isFinite(reported.y)||!Double.isFinite(reported.z)||shooter.squaredDistanceTo(reported)>(range+16)*(range+16))return;
        Vec3d start=shooter.getEyePos(),direction=reported.subtract(start).normalize();
        var hit=w.raycast(new RaycastContext(start,reported.add(direction.multiply(.4)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,shooter));
        if(hit.getType()==HitResult.Type.BLOCK&&w.isChunkLoaded(hit.getBlockPos()))send(w,w.getBlockState(hit.getBlockPos()),hit.getPos(),Vec3d.of(hit.getSide().getVector()),.35f,shooter instanceof ServerPlayerEntity p?p:null);
    }
    public static void send(ServerWorld w,BlockState state,Vec3d position,Vec3d normal,float power,ServerPlayerEntity localViewer){
        if(state==null||state.isAir())return;long seed=w.random.nextLong();
        for(var p:w.getPlayers(p->p!=localViewer&&(p.squaredDistanceTo(position)<96*96||com.bobincraft.drone.DroneNetworking.viewOrigin(p).squaredDistanceTo(position)<96*96))){
            if(!ServerPlayNetworking.canSend(p,PACKET))continue;
            var b=PacketByteBufs.create();b.writeInt(Block.getRawIdFromState(state));b.writeDouble(position.x);b.writeDouble(position.y);b.writeDouble(position.z);
            b.writeFloat((float)normal.x);b.writeFloat((float)normal.y);b.writeFloat((float)normal.z);b.writeFloat(power);b.writeLong(seed);ServerPlayNetworking.send(p,PACKET,b);
        }
    }
}
