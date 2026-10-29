package com.bobincraft.vehicle;

import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.UUID;

/** Short-lived, swept-collision game projectile. Reloaded projectiles are inert. */
public final class ArmorShellEntity extends Entity {
    private static final TrackedData<org.joml.Vector3f> MOTION=DataTracker.registerData(ArmorShellEntity.class,TrackedDataHandlerRegistry.VECTOR3F);
    private UUID operator,vehicle;private boolean live;
    public ArmorShellEntity(EntityType<? extends ArmorShellEntity> type,World world){super(type,world);}
    public void launch(ArmoredVehicleEntity carrier,LivingEntity owner,Vec3d position,Vec3d velocity){vehicle=carrier.getUuid();operator=owner.getUuid();setPosition(position);setVelocity(velocity);syncMotion(velocity);orient(velocity);live=true;}
    @Override protected void initDataTracker() {dataTracker.startTracking(MOTION,new org.joml.Vector3f());}
    @Override public boolean canHit(){return false;}
    @Override public void tick(){super.tick();if(getWorld().isClient){Vec3d v=getVelocity().add(0,-ArmorControls.SHELL_GRAVITY,0);setPosition(getPos().add(v));setVelocity(v);orient(v);return;}
        if(!live||age>80||ArmorContent.inHub(getWorld())){discard();return;}
        var world=(ServerWorld)getWorld();Vec3d before=getPos(),velocity=getVelocity().add(0,-ArmorControls.SHELL_GRAVITY,0),after=before.add(velocity);
        Box sweep=getBoundingBox().stretch(velocity);
        for(int x=MathHelper.floor(sweep.minX)>>4;x<=MathHelper.floor(sweep.maxX)>>4;x++)for(int z=MathHelper.floor(sweep.minZ)>>4;z<=MathHelper.floor(sweep.maxZ)>>4;z++)if(!world.isChunkLoaded(x,z)){discard();return;}
        if(!world.getWorldBorder().contains(sweep)||after.y<world.getBottomY()){discard();return;}
        var block=world.raycast(new RaycastContext(before,after,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.ANY,this));
        double distance=block.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:before.squaredDistanceTo(block.getPos());Vec3d impact=block.getPos();Entity struck=null;
        for(var candidate:world.getOtherEntities(this,sweep.expand(.1),e->e.canHit()&&e.isAlive()&&!e.getUuid().equals(operator)&&!e.getUuid().equals(vehicle))){
            var hit=candidate.getBoundingBox().expand(.1).raycast(before,after);if(hit.isPresent()&&before.squaredDistanceTo(hit.get())<distance){distance=before.squaredDistanceTo(hit.get());impact=hit.get();struck=candidate;}
        }
        if(Double.isFinite(distance)){
            setPosition(impact);Entity owner=operator==null?null:world.getEntity(operator);
            if(struck!=null)struck.damage(world.getDamageSources().explosion(this,owner),48);
            // Bias the pressure origin out of the struck face; damage/cover is traced before the crater.
            Vec3d center=impact.subtract(velocity.normalize().multiply(.12));
            com.bobincraft.drone.DroneExplosion.explodeAt(world,this,owner instanceof net.minecraft.server.network.ServerPlayerEntity p?p:null,com.bobincraft.drone.DroneBlastType.ARMOR_HIGH_EXPLOSIVE,center);
            discard();return;
        }
        setPosition(after);setVelocity(velocity);syncMotion(velocity);orient(velocity);velocityDirty=true;world.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,getX(),getY(),getZ(),1,0,0,0,0);
    }
    private void syncMotion(Vec3d v){dataTracker.set(MOTION,new org.joml.Vector3f((float)v.x,(float)v.y,(float)v.z));}
    @Override public void onTrackedDataSet(TrackedData<?> data){super.onTrackedDataSet(data);if(data==MOTION&&getWorld().isClient){var v=dataTracker.get(MOTION);setVelocity(v.x,v.y,v.z);}}
    /** Vanilla velocity packets clamp each axis to 3.9; fast shells carry their authoritative vector separately. */
    @Override public void setVelocityClient(double x,double y,double z){var v=dataTracker.get(MOTION);if(v.lengthSquared()>0)super.setVelocityClient(v.x,v.y,v.z);else super.setVelocityClient(x,y,z);}
    private void orient(Vec3d v){setYaw((float)Math.toDegrees(Math.atan2(v.x,v.z)));setPitch((float)-Math.toDegrees(Math.atan2(v.y,v.horizontalLength())));}
    @Override public void updateTrackedPositionAndAngles(double x,double y,double z,float yaw,float pitch,int ticks,boolean interpolate){setPosition(x,y,z);setYaw(yaw);setPitch(pitch);}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt){ }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt){live=false;}
}
