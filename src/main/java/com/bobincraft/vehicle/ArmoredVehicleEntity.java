package com.bobincraft.vehicle;

import com.bobincraft.raid.HubSafety;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Persistent, server-driven armor: the same entity is a mobile tank or a dug-in weapon emplacement. */
public final class ArmoredVehicleEntity extends Entity {
    public static final int CLIMB_OUT_TICKS=24;
    public static final class ClimbOutState { final Vec3d startLocal; int ticks; ClimbOutState(Vec3d startLocal) { this.startLocal = startLocal; } }
    private final java.util.Map<java.util.UUID,ClimbOutState> climbingOut=new java.util.HashMap<>();
    private static final TrackedData<Boolean> HATCH=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private float hatchAngle=100;
    public float previousHatch=100;
    private record WalkingPose(long x,long y,long z,int yaw,int turret,int pitch,int bank,int hatch,boolean fast) { }
    private record DeckPose(String group,Vec3d local) { }
    private WalkingPose walkingPose;
    private java.util.List<Box> walkingBoxes=java.util.List.of();
    private java.util.List<String> walkingGroups=java.util.List.of();
    private java.util.List<VoxelShape> walkingShapes=java.util.List.of();
    public boolean hatchOpen(){return dataTracker.get(HATCH);}
    public float hatchAngle(){return hatchAngle;}
    public double forwardSpeed(){
        if(!getWorld().isClient)return speed;
        double a=Math.toRadians(getYaw());
        return (getZ()-prevZ)*Math.cos(a)-(getX()-prevX)*Math.sin(a);
    }
    public java.util.List<Box> walkingBoxes(){
        var pose=new WalkingPose(Math.round(getX()*200),Math.round(getY()*200),Math.round(getZ()*200),
            Math.round(getYaw()*4),Math.round(turretYaw()*4),Math.round(suspension()*4),Math.round(bank()*4),Math.round(hatchAngle),forwardSpeed()>.06);
        if(!pose.equals(walkingPose)){
            var colliders=ArmorGeometry.worldColliders(this);
            var boxes=new java.util.ArrayList<Box>(colliders.size());
            var groups=new java.util.ArrayList<String>(colliders.size());
            var shapes=new java.util.ArrayList<VoxelShape>(colliders.size());
            for(var c:colliders){boxes.add(c.box());groups.add(c.group());shapes.add(VoxelShapes.cuboid(c.box()));}
            walkingBoxes=java.util.List.copyOf(boxes);
            walkingGroups=java.util.List.copyOf(groups);
            walkingShapes=java.util.List.copyOf(shapes);
            walkingPose=pose;
        }
        return walkingBoxes;
    }
    public java.util.List<String> walkingGroups(){walkingBoxes();return walkingGroups;}
    public java.util.List<VoxelShape> walkingShapes(){walkingBoxes();return walkingShapes;}
    public boolean isInCabin(Entity entity){
        if(entity==null||entity.getWorld()!=getWorld())return false;
        Vec3d feet=ArmorGeometry.toLocal(this,"turret",entity.getPos());
        Vec3d waist=ArmorGeometry.toLocal(this,"turret",entity.getPos().add(0,.35,0));
        Vec3d center=ArmorGeometry.toLocal(this,"turret",entity.getBoundingBox().getCenter());
        return ArmorGeometry.CABIN.contains(feet)&&feet.y<=.85
            ||ArmorGeometry.CABIN.contains(waist)&&waist.y<=.95
            ||ArmorGeometry.CABIN.contains(center)&&center.y<=1.02;
    }
    public boolean canBoard(Entity entity){
        if(isInCabin(entity))return true;
        if(entity==null||entity.getWorld()!=getWorld()||!hatchOpen())return false;
        Vec3d feet=ArmorGeometry.toLocal(this,"turret",entity.getPos());
        return Math.hypot(feet.x-.68,feet.z-.16)<=.64&&feet.y>=-1.12&&feet.y<=1.38;
    }
    public boolean toggleHatch(PlayerEntity player){
        if(getWorld().isClient||ArmorContent.inHub(getWorld())||!player.isAlive()||armorHealth()<=0)return false;
        Vec3d hatch=ArmorGeometry.nearestHatchCenter(this,player.getEyePos());
        if(!hasPassenger(player)&&!isInCabin(player)){
            if(player.getEyePos().squaredDistanceTo(hatch)>9||player.getY()<getY()+1.25)return false;
            if(getWorld().raycast(new RaycastContext(player.getEyePos(),hatch,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()!=HitResult.Type.MISS)return false;
        }
        dataTracker.set(HATCH,!hatchOpen());
        getWorld().playSound(null,getBlockPos(),hatchOpen()?SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN:SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,SoundCategory.BLOCKS,.8f,.8f);
        return true;
    }
    private static float smoothstep(float t){
        t=MathHelper.clamp(t,0f,1f);
        return t*t*(3f-2f*t);
    }
    public static Vec3d climbOutLocalPos(Vec3d startLocal,float progress){
        float p=MathHelper.clamp(progress,0f,1f);
        Vec3d hatchBase=new Vec3d(.68,-.82,.16);
        Vec3d hatchRim=new Vec3d(.68,.92,.04);
        Vec3d roofStand=new Vec3d(.68,1.25,-.48);
        if(p<=.28f){
            float t=smoothstep(p/.28f);
            return new Vec3d(MathHelper.lerp(t,startLocal.x,hatchBase.x),MathHelper.lerp(t,startLocal.y,hatchBase.y),MathHelper.lerp(t,startLocal.z,hatchBase.z));
        }
        if(p<=.72f){
            float t=smoothstep((p-.28f)/.44f);
            return new Vec3d(MathHelper.lerp(t,hatchBase.x,hatchRim.x),MathHelper.lerp(t,hatchBase.y,hatchRim.y),MathHelper.lerp(t,hatchBase.z,hatchRim.z));
        }
        float t=smoothstep((p-.72f)/.28f);
        return new Vec3d(MathHelper.lerp(t,hatchRim.x,roofStand.x),MathHelper.lerp(t,hatchRim.y,roofStand.y),MathHelper.lerp(t,hatchRim.z,roofStand.z));
    }
    public boolean startClimbOut(PlayerEntity player){
        if(player==null||!player.isAlive()||!isAlive()||armorHealth()<=0||ArmorContent.inHub(getWorld()))return false;
        if(!hasPassenger(player)&&!isInCabin(player))return false;
        if(!hatchOpen()){
            dataTracker.set(HATCH,true);
            hatchAngle=Math.max(hatchAngle,85f);
            getWorld().playSound(null,getBlockPos(),SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN,SoundCategory.BLOCKS,.8f,.9f);
        }else if(hatchAngle<85f){
            hatchAngle=85f;
        }
        if(hasPassenger(player))player.stopRiding();
        Vec3d rawLocal=ArmorGeometry.toLocal(this,"turret",player.getPos());
        Vec3d startLocal=new Vec3d(MathHelper.clamp(rawLocal.x,-.95,.95),MathHelper.clamp(rawLocal.y,-1.04,-.72),MathHelper.clamp(rawLocal.z,-.65,.65));
        climbingOut.put(player.getUuid(),new ClimbOutState(startLocal));
        Vec3d firstWorld=ArmorGeometry.toWorld(this,"turret",climbOutLocalPos(startLocal,1f/CLIMB_OUT_TICKS));
        player.setPosition(firstWorld.x,firstWorld.y,firstWorld.z);
        player.setVelocity(Vec3d.ZERO);player.fallDistance=0;
        if(getWorld() instanceof ServerWorld sw){
            for(ServerPlayerEntity viewer:sw.getPlayers(p->p.squaredDistanceTo(this)<=64*64)){
                var buf=PacketByteBufs.create();
                buf.writeInt(getId());buf.writeUuid(player.getUuid());
                buf.writeFloat((float)startLocal.x);buf.writeFloat((float)startLocal.y);buf.writeFloat((float)startLocal.z);
                ServerPlayNetworking.send(viewer,ArmorContent.CLIMB_OUT,buf);
            }
        }
        return true;
    }
    public void startClimbOutClient(java.util.UUID playerId,Vec3d startLocal){
        if(!hatchOpen())dataTracker.set(HATCH,true);
        if(hatchAngle<85f)hatchAngle=85f;
        climbingOut.put(playerId,new ClimbOutState(startLocal));
    }
    public float climbOutProgress(PlayerEntity player,float tickDelta){
        if(player==null)return -1f;
        ClimbOutState state=climbingOut.get(player.getUuid());
        if(state==null)return -1f;
        float elapsed=state.ticks+tickDelta;
        if(elapsed<0f||elapsed>CLIMB_OUT_TICKS+1f)return -1f;
        return MathHelper.clamp(elapsed/CLIMB_OUT_TICKS,0f,1f);
    }
    public static float activeClimbOutProgress(PlayerEntity player,float tickDelta){
        if(player==null||player.getWorld()==null)return -1f;
        for(var e:player.getWorld().getOtherEntities(player,player.getBoundingBox().expand(4.5),e->e instanceof ArmoredVehicleEntity&&e.isAlive())){
            float p=((ArmoredVehicleEntity)e).climbOutProgress(player,tickDelta);
            if(p>=0f)return p;
        }
        return -1f;
    }
    private PlayerEntity findClimbOutPlayer(java.util.UUID id){
        PlayerEntity p=getWorld().getPlayerByUuid(id);
        if(p!=null)return p;
        for(var e:getWorld().getOtherEntities(this,getBoundingBox().expand(6.0),e->e instanceof PlayerEntity&&e.getUuid().equals(id)))
            return (PlayerEntity)e;
        return null;
    }
    private void tickClimbOuts(){
        if(climbingOut.isEmpty())return;
        var it=climbingOut.entrySet().iterator();
        while(it.hasNext()){
            var entry=it.next();
            PlayerEntity player=findClimbOutPlayer(entry.getKey());
            if(player==null||!player.isAlive()||player.hasVehicle()){it.remove();continue;}
            ClimbOutState state=entry.getValue();
            int step=++state.ticks;
            float progress=MathHelper.clamp((float)step/CLIMB_OUT_TICKS,0f,1f);
            Vec3d local=climbOutLocalPos(state.startLocal,progress);
            Vec3d worldPos=ArmorGeometry.toWorld(this,"turret",local);
            player.setPosition(worldPos.x,worldPos.y,worldPos.z);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance=0f;
            player.setOnGround(progress>=.95f);
            player.setYaw(turretYaw());
            player.setBodyYaw(turretYaw());
            if(step>=CLIMB_OUT_TICKS)it.remove();
        }
    }
    private void animateHatch(){previousHatch=hatchAngle;hatchAngle+=MathHelper.clamp((hatchOpen()?100:0)-hatchAngle,-10,10);}
    private java.util.Map<Entity,DeckPose> deckRiders(){
        var result=new java.util.HashMap<Entity,DeckPose>();
        var others=getWorld().getOtherEntities(this,getBoundingBox().expand(.5,2,.5),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()&&!e.hasVehicle());
        if(others.isEmpty())return result;
        var boxes=walkingBoxes();var groups=walkingGroups();
        for(var e:others){
            if(climbingOut.containsKey(e.getUuid()))continue;
            if(!e.isOnGround())continue;Box feet=e.getBoundingBox().offset(0,-.05,0);
            String supportGroup=null;double bestDy=.10;
            for(int i=0,n=boxes.size();i<n;i++){
                Box b=boxes.get(i);double dy=Math.abs(b.maxY-e.getY());
                if(b.intersects(feet)&&dy<bestDy){bestDy=dy;supportGroup=groups.get(i);}
            }
            if(supportGroup!=null){
                String group=isInCabin(e)||!"hull".equals(supportGroup)?"turret":"hull";
                result.put(e,new DeckPose(group,ArmorGeometry.toLocal(this,group,e.getPos())));
            }
        }return result;
    }
    private void carryDeckRiders(java.util.Map<Entity,DeckPose> riders){
        for(var entry:riders.entrySet()){
            Entity rider=entry.getKey();if(climbingOut.containsKey(rider.getUuid()))continue;
            DeckPose pose=entry.getValue();
            Vec3d shift=ArmorGeometry.toWorld(this,pose.group(),pose.local()).subtract(rider.getPos());
            if(shift.lengthSquared()>1||shift.lengthSquared()<1e-12)continue;
            Box destination=rider.getBoundingBox().offset(shift);
            if(!getWorld().getBlockCollisions(rider,destination.contract(.001)).iterator().hasNext()){
                rider.setPosition(rider.getPos().add(shift));rider.fallDistance=0;
            }
        }
        if(hatchOpen()){
            boolean tilted=Math.abs(suspension())>.5f||Math.abs(bank())>.5f;
            for(var e:getWorld().getOtherEntities(this,getBoundingBox().expand(.2,1,.2),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()&&!e.hasVehicle())){
                if(climbingOut.containsKey(e.getUuid()))continue;
                Vec3d local=ArmorGeometry.toLocal(this,"turret",e.getPos());
                var openHatch=ArmorGeometry.HATCHES.get(0);
                double dHatch=Math.hypot(local.x-openHatch.center().x,local.z-openHatch.center().z);
                if(tilted&&e.getVelocity().y<=.02&&!e.isSneaking()&&dHatch<.46&&local.y>-.92&&local.y<=1.36){
                    double nextY=Math.max(-1.02,local.y-.16);
                    Vec3d guided=ArmorGeometry.toWorld(this,"turret",new Vec3d(openHatch.center().x,nextY,openHatch.center().z));
                    e.setPosition(guided.x,guided.y,guided.z);
                    e.fallDistance=0;
                    continue;
                }
                if(!isInCabin(e)||e.getVelocity().y<=.12||local.y<-.85)continue;
                if(dHatch<.38){
                    Vec3d roofPoint=ArmorGeometry.toWorld(this,"turret",new Vec3d(openHatch.center().x,1.25,openHatch.center().z));
                    e.setPosition(roofPoint.x,roofPoint.y,roofPoint.z);
                    Vec3d v=e.getVelocity();e.setVelocity(v.x,.18,v.z);e.fallDistance=0;
                }
            }
        }
    }
    private static final TrackedData<Boolean> FIXED=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> TURRET=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> ELEVATION=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEALTH=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> AMMO=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LOADING=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FACTION=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> CHAMBER=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> SUSPENSION=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BANK=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> SEAT_MODE=DataTracker.registerData(ArmoredVehicleEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private ArmorControls.Input input=ArmorControls.Input.STOP,secondaryInput=ArmorControls.Input.STOP;
    private long lastInput=-100,lastSecondaryInput=-100;
    private int cooldown,site=-1,lerpTicks;
    private double speed,targetX,targetY,targetZ;
    private float targetYaw;
    private float cachedTurret,cachedElevation,cachedSuspension,cachedBank,cachedHealth=100f;
    private java.util.UUID crewReservation;
    private long crewReservationUntil;
    public double treadDistance,previousTread;
    public float previousTurret,previousElevation,previousSuspension,previousBank;
    public ArmoredVehicleEntity(EntityType<? extends ArmoredVehicleEntity> type,World world){super(type,world);setStepHeight(0);}
    @Override protected void initDataTracker(){dataTracker.startTracking(HATCH,true);dataTracker.startTracking(BANK,0f);dataTracker.startTracking(CHAMBER,true);dataTracker.startTracking(SUSPENSION,0f);dataTracker.startTracking(LOADING,0);dataTracker.startTracking(FIXED,false);dataTracker.startTracking(TURRET,0f);dataTracker.startTracking(ELEVATION,0f);dataTracker.startTracking(HEALTH,100f);dataTracker.startTracking(AMMO,ArmorControls.MAGAZINE);dataTracker.startTracking(FACTION,-1);dataTracker.startTracking(SEAT_MODE,0);}
    @Override public void onTrackedDataSet(TrackedData<?> data){
        super.onTrackedDataSet(data);
        if(TURRET.equals(data))cachedTurret=dataTracker.get(TURRET);
        else if(ELEVATION.equals(data))cachedElevation=dataTracker.get(ELEVATION);
        else if(SUSPENSION.equals(data))cachedSuspension=dataTracker.get(SUSPENSION);
        else if(BANK.equals(data))cachedBank=dataTracker.get(BANK);
        else if(HEALTH.equals(data))cachedHealth=dataTracker.get(HEALTH);
    }
    private LivingEntity secondRider;
    public LivingEntity secondPassenger(){
        if(!hasPassengers())return null;
        Entity first=getFirstPassenger();
        if(secondRider!=null&&secondRider!=first&&hasPassenger(secondRider))return secondRider;
        for(var e:getWorld().getOtherEntities(this,getBoundingBox().expand(2),e->e instanceof LivingEntity&&hasPassenger(e)))
            if(e!=first){secondRider=(LivingEntity)e;return secondRider;}
        secondRider=null;return null;
    }
    public boolean hasFreeSeat(){return !hasPassengers()||secondPassenger()==null;}
    public int seatFor(Entity passenger){
        int base=dataTracker.get(SEAT_MODE)&1;
        return (passenger==null||passenger==getFirstPassenger())?base:1-base;
    }
    public void switchSeat(PlayerEntity player){
        if(!hasPassenger(player))return;
        dataTracker.set(SEAT_MODE,1-(dataTracker.get(SEAT_MODE)&1));
    }
    public boolean chambered(){return dataTracker.get(CHAMBER);}
    public float bank(){return cachedBank;}
    public float suspension(){return cachedSuspension;}
    @Override protected MoveEffect getMoveEffect(){return MoveEffect.NONE;}
    public boolean engineRunning(){return !fixed()&&hasPassengers();}
    public double drivingSpeed(){return getWorld().isClient?getPos().distanceTo(new Vec3d(prevX,prevY,prevZ)):Math.abs(speed);}
    public boolean fixed(){return dataTracker.get(FIXED);}
    public float turretYaw(){return cachedTurret;}
    public float elevation(){return cachedElevation;}
    public int ammo(){return dataTracker.get(AMMO);}
    public float armorHealth(){return cachedHealth;}
    public int factionId(){return dataTracker.get(FACTION);}
    public int siteId(){return site;}
    public int reloadTicks(){return !getWorld().isClient?cooldown:dataTracker.get(LOADING);}
    public void configure(boolean fixed,int faction,int site,float yaw){dataTracker.set(FIXED,fixed);dataTracker.set(FACTION,faction);this.site=site;setYaw(yaw);cachedTurret=yaw;dataTracker.set(TURRET,yaw);previousTurret=yaw;}
    public void acceptInput(PlayerEntity player,ArmorControls.Input controls){
        if(getWorld().isClient||!hasPassenger(player)||!player.isAlive()||!controls.valid()||ArmorContent.inHub(getWorld())||!com.bobincraft.combat.GuardInjuryPose.canUseHands(com.bobincraft.combat.PlayerInjuries.missing(player)))return;
        if(controls.yaw()>18000f)switchSeat(player);
        var clean=new ArmorControls.Input(controls.forward(),controls.steer(),MathHelper.wrapDegrees(controls.yaw()),MathHelper.clamp(controls.pitch(),-28,fixed()?6:12),controls.fire());
        if(player==getFirstPassenger()){input=clean;lastInput=getWorld().getTime();}
        else{secondaryInput=clean;lastSecondaryInput=getWorld().getTime();}
    }
    public boolean canOperate(WarzoneGuardEntity guard){return !hasPassengers()&&armorHealth()>0&&guard.isAlive()&&!guard.isHubGuard()&&guard.canUseHands()&&!guard.bobinInjuries().isBleeding()&&!guard.isInjuryProne()&&guard.faction().ordinal()==factionId()
        &&(crewReservation==null||crewReservationUntil<getWorld().getTime()||crewReservation.equals(guard.getUuid()));}
    public boolean reserveCrew(WarzoneGuardEntity guard){if(!canOperate(guard))return false;crewReservation=guard.getUuid();crewReservationUntil=getWorld().getTime()+100;return true;}
    public void releaseCrew(WarzoneGuardEntity guard){if(guard.getUuid().equals(crewReservation)){crewReservation=null;crewReservationUntil=0;}}
    /** Ground-level boarding stations lie outside the rotated broad-phase box, including dug-in hulls. */
    public java.util.List<Vec3d> boardingPoints(LivingEntity rider){
        var box=getBoundingBox();var result=new java.util.ArrayList<Vec3d>();
        for(double[] xz:new double[][]{{box.minX-1,getZ()},{box.maxX+1,getZ()},{getX(),box.minZ-1},{getX(),box.maxZ+1}})
            for(int y=MathHelper.floor(getY())+5;y>=MathHelper.floor(getY())-2;y--){BlockPos feet=BlockPos.ofFloored(xz[0],y,xz[1]);
                if(!getWorld().isChunkLoaded(feet)||!getWorld().getFluidState(feet).isEmpty()||getWorld().getBlockState(feet.down()).getCollisionShape(getWorld(),feet.down()).isEmpty())continue;
                Vec3d p=Vec3d.ofBottomCenter(feet);if(getWorld().isSpaceEmpty(rider,rider.getDimensions(EntityPose.STANDING).getBoxAt(p))){result.add(p);break;}}
        result.sort(java.util.Comparator.comparingDouble(rider::squaredDistanceTo));return result;
    }
    public Vec3d boardingPoint(){return getPos().add(getBoundingBox().maxX-getX()+1,fixed()?ArmorControls.BURIAL_DEPTH:0,0);}
    @Override public boolean canHit(){return isAlive();}
    @Override public boolean isPushable(){return false;}
    @Override public boolean collidesWith(Entity other){return !isConnectedThroughVehicle(other)&&other.isCollidable();}
    @Override public boolean isCollidable(){return true;}
    @Override protected boolean canAddPassenger(Entity passenger){return passenger instanceof LivingEntity&&hasFreeSeat();}
    // Let the server own physics. Exposing a controlling player would let vanilla send vehicle-move coordinates.
    @Override public LivingEntity getControllingPassenger(){return null;}
    @Override public double getMountedHeightOffset(){return 1.15;}
    @Override protected void updatePassengerPosition(Entity passenger,PositionUpdater updater){
        if(!hasPassenger(passenger))return;
        Vec3d seatPos=seatFor(passenger)==0?ArmorGeometry.OPERATOR_SEAT:ArmorGeometry.GUNNER_SEAT;
        Vec3d seat=ArmorGeometry.toWorld(this,"turret",seatPos).add(0,Math.max(-.32,passenger.getHeightOffset()),0);
        updater.accept(passenger,seat.x,seat.y,seat.z);
        if(passenger instanceof LivingEntity living)living.bodyYaw=turretYaw();
        if(passenger instanceof WarzoneGuardEntity){passenger.setYaw(turretYaw());passenger.setHeadYaw(turretYaw());}
    }
    @Override protected Box calculateBoundingBox(){
        double a=Math.toRadians(getYaw()),x=Math.abs(Math.cos(a))*ArmorGeometry.HALF_WIDTH+Math.abs(Math.sin(a))*ArmorGeometry.HALF_LENGTH,z=Math.abs(Math.cos(a))*ArmorGeometry.HALF_LENGTH+Math.abs(Math.sin(a))*ArmorGeometry.HALF_WIDTH;
        return new Box(getX()-x,getY(),getZ()-z,getX()+x,getY()+4.5,getZ()+z);
    }
    @Override public ActionResult interact(PlayerEntity player,Hand hand){
        if(ArmorContent.inHub(getWorld()))return ActionResult.FAIL;
        if(player.getStackInHand(hand).getItem() instanceof HandGrenadeItem)return ActionResult.PASS;
        if(player.getStackInHand(hand).isOf(ArmorContent.SHELL)&&ammo()<ArmorControls.MAGAZINE){
            if(!getWorld().isClient){dataTracker.set(AMMO,Math.min(ArmorControls.MAGAZINE,ammo()+1));if(!player.getAbilities().creativeMode)player.getStackInHand(hand).decrement(1);}
            return ActionResult.SUCCESS;
        }
        if(player.isSneaking()){
            Vec3d hatch=ArmorGeometry.nearestHatchCenter(this,player.getEyePos());
            if(player.getStackInHand(hand).isEmpty()&&(isInCabin(player)||player.getY()>=getY()+1.25&&player.getEyePos().squaredDistanceTo(hatch)<=9)){
                if(!getWorld().isClient)toggleHatch(player);return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }
        if(hasFreeSeat()&&canBoard(player)){
            if(!getWorld().isClient){
                if(!hasPassengers()){
                    Vec3d aim=ArmorGeometry.toLocal(this,"turret",player.getEyePos().add(player.getRotationVec(1f).multiply(.85)));
                    Vec3d feet=ArmorGeometry.toLocal(this,"turret",player.getPos());
                    dataTracker.set(SEAT_MODE,(aim.x*.65+feet.x*.35)<0?1:0);
                }
                if(player.startRiding(this))player.sendMessage(Text.translatable(fixed()?"vehicle.bobin_craft.fixed_controls":"vehicle.bobin_craft.controls"),true);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    @Override public Vec3d updatePassengerForDismount(LivingEntity rider){
        int seat=seatFor(rider);
        double sx=seat==0?.58:-.58,sz=seat==0?.18:-.10;
        for(double[] local:new double[][]{{sx,-1.02,sz},{0,-1.02,.04},{-sx,-1.02,-sz}}){
            Vec3d point=ArmorGeometry.toWorld(this,"turret",new Vec3d(local[0],local[1],local[2]));
            if(!getWorld().getBlockCollisions(rider,rider.getDimensions(EntityPose.STANDING).getBoxAt(point).contract(.04)).iterator().hasNext())return point;
        }
        return ArmorGeometry.toWorld(this,"turret",new Vec3d(sx,-1.02,sz));
    }
    private void knockDownObstacles(ServerWorld sw,Vec3d centerPos,float yaw){
        if(ArmorContent.inHub(sw)||Math.abs(speed)<.012)return;
        double a=Math.toRadians(yaw);
        Vec3d travelDir=new Vec3d(-Math.sin(a)*(speed>=0?1:-1),0,Math.cos(a)*(speed>=0?1:-1));
        int baseY=MathHelper.floor(centerPos.y);
        for(int i=-4;i<=4;i++)for(double side:new double[]{-1.85,-.95,0,.95,1.85}){
            double f=i*.82;
            double wx=centerPos.x-Math.sin(a)*f+Math.cos(a)*side;
            double wz=centerPos.z+Math.cos(a)*f+Math.sin(a)*side;
            for(int dy=0;dy<=2;dy++){
                BlockPos pos=BlockPos.ofFloored(wx,baseY+dy,wz);
                if(!sw.isChunkLoaded(pos))continue;
                BlockState state=sw.getBlockState(pos);
                if(state.isAir()||state.hasBlockEntity())continue;
                float hardness=state.getHardness(sw,pos);
                if(hardness<0f||hardness>3.0f)continue;
                Block b=state.getBlock();
                boolean smallObstacle=b instanceof FenceBlock||b instanceof FenceGateBlock||b instanceof WallBlock
                    ||b instanceof PaneBlock||b instanceof DoorBlock||b instanceof TrapdoorBlock
                    ||(!state.isOpaqueFullCube(sw,pos)&&hardness<=1.5f);
                if(!smallObstacle)continue;
                Vec3d outward=Vec3d.ofBottomCenter(pos).subtract(centerPos);
                Vec3d push=new Vec3d(travelDir.x*.75+outward.x*.12,0,travelDir.z*.75+outward.z*.12);
                if(push.lengthSquared()<1e-4)push=travelDir;else push=push.normalize();
                sw.breakBlock(pos,false,this);
                com.bobincraft.combat.ImpactDebris.send(sw,state,Vec3d.ofBottomCenter(pos),push,-1.0f,null);
                BlockPos up=pos.up();BlockState upState=sw.getBlockState(up);
                if(!upState.isAir()&&!upState.hasBlockEntity()&&upState.getBlock()==b){
                    sw.breakBlock(up,false,this);
                    com.bobincraft.combat.ImpactDebris.send(sw,upState,Vec3d.ofBottomCenter(up),push,-1.0f,null);
                }
            }
        }
    }
    private void crushFrontEntities(LivingEntity operator){
        double fwd=forwardSpeed();
        boolean movingForward=fwd>.06;
        boolean movingReverse=fwd<-.024;
        if((!movingForward&&!movingReverse)||ArmorContent.inHub(getWorld()))return;
        double a=Math.toRadians(getYaw());
        double dirSign=movingForward?1.0:-1.0;
        Vec3d travelDir=new Vec3d(-Math.sin(a)*dirSign,0,Math.cos(a)*dirSign);
        // Reverse max speed is 0.12 vs forward 0.30 (2.5x ratio), so scale reverse speed for equivalent crush energy
        double rawSpeed=Math.abs(fwd);
        double effSpeed=movingForward?rawSpeed:rawSpeed*2.5;
        boolean isServer=getWorld() instanceof ServerWorld;
        for(var e:getWorld().getOtherEntities(this,getBoundingBox().expand(.8,.3,.8),e->e instanceof LivingEntity&&e.isAlive()&&!e.isSpectator()&&!hasPassenger(e))){
            LivingEntity target=(LivingEntity)e;
            if(target instanceof WarzoneGuardEntity g&&g.isHubGuard())continue;
            Vec3d local=ArmorGeometry.toLocal(this,"hull",target.getPos());
            if(Math.abs(local.x)>2.32||local.y>1.85||local.y<-.6)continue;
            boolean inFrontZone=movingForward&&local.z>=-4.45&&local.z<-2.05;
            boolean inRearZone=movingReverse&&local.z>2.05&&local.z<=4.45;
            boolean underHullZone=isServer&&local.z>=-2.05&&local.z<=2.05&&Math.abs(local.x)<=2.25&&local.y<=1.50;
            if(!inFrontZone&&!inRearZone&&!underHullZone)continue;
            double sideSign=local.x>=0?1:-1;
            Vec3d sideDir=new Vec3d(Math.cos(a)*sideSign,0,Math.sin(a)*sideSign);
            double pushSpeed=rawSpeed+.22;
            target.setVelocity(travelDir.x*pushSpeed+sideDir.x*.08,Math.min(target.getVelocity().y,-.08),travelDir.z*pushSpeed+sideDir.z*.08);
            target.velocityModified=true;
            if(getWorld() instanceof ServerWorld sw&&age%4==0){
                target.timeUntilRegen=0;
                float dmg=(float)MathHelper.clamp((effSpeed-.05)*85.0,6.0,22.0);
                DamageSource src=operator instanceof PlayerEntity p?sw.getDamageSources().playerAttack(p):operator!=null?sw.getDamageSources().mobAttack(operator):sw.getDamageSources().explosion(this,this);
                if(target.damage(src,dmg)&&effSpeed>.10){
                    int severed=com.bobincraft.combat.HitRegion.LEFT_LEG.limbMask|com.bobincraft.combat.HitRegion.RIGHT_LEG.limbMask;
                    if(underHullZone&&effSpeed>.16&&target instanceof WarzoneGuardEntity g&&(g.missingLimbs()&severed)==severed){
                        severed|=com.bobincraft.combat.HitRegion.HEAD_SEVERED;
                    }
                    com.bobincraft.combat.InjurySystem.wound(target,com.bobincraft.combat.InjurySeverity.SEVERE,false,severed);
                }
            }
        }
    }
    @Override public void tick(){
        var riders=deckRiders();animateHatch();super.tick();previousTurret=turretYaw();previousElevation=elevation();previousSuspension=suspension();previousBank=bank();
        if(getWorld().isClient){previousTread=treadDistance;Vec3d before=getPos();if(lerpTicks>0){setPosition(getX()+(targetX-getX())/lerpTicks,getY()+(targetY-getY())/lerpTicks,getZ()+(targetZ-getZ())/lerpTicks);setYaw(getYaw()+MathHelper.wrapDegrees(targetYaw-getYaw())/lerpTicks);lerpTicks--;}carryDeckRiders(riders);tickClimbOuts();crushFrontEntities(null);treadDistance+=getPos().subtract(before).horizontalLength()*(Math.cos(Math.toRadians(getYaw()))*(getZ()-before.z)-Math.sin(Math.toRadians(getYaw()))*(getX()-before.x)<0?-1:1);return;}
        if(armorHealth()<=0){discard();return;}
        if(cooldown>0&&--cooldown==0)dataTracker.set(CHAMBER,true);
        Entity fp=getFirstPassenger();LivingEntity sp=secondPassenger();
        if(fp!=null&&(!fp.isAlive()||ArmorContent.inHub(getWorld())))fp.stopRiding();
        if(sp!=null&&(!sp.isAlive()||ArmorContent.inHub(getWorld())))sp.stopRiding();
        LivingEntity operator=getFirstPassenger() instanceof LivingEntity living?living:null;
        LivingEntity second=secondPassenger();
        LivingEntity driver=null,gunner=null;
        // War Thunder single-player control: a solo player simultaneously drives and aims/fires without switching seats.
        if(second==null&&operator!=null){
            driver=operator;gunner=operator;
        }else{
            if(operator!=null){if(seatFor(operator)==0)driver=operator;else gunner=operator;}
            if(second!=null){if(seatFor(second)==0)driver=second;else gunner=second;}
        }
        ArmorControls.Input dCtrl=driver==operator?(getWorld().getTime()-lastInput<=ArmorControls.INPUT_TIMEOUT&&driver instanceof PlayerEntity?input:ArmorControls.Input.STOP):(getWorld().getTime()-lastSecondaryInput<=ArmorControls.INPUT_TIMEOUT&&driver instanceof PlayerEntity?secondaryInput:ArmorControls.Input.STOP);
        ArmorControls.Input gCtrl=gunner==operator?(getWorld().getTime()-lastInput<=ArmorControls.INPUT_TIMEOUT&&gunner instanceof PlayerEntity?input:new ArmorControls.Input(0,0,turretYaw(),elevation(),false)):(gunner!=null&&getWorld().getTime()-lastSecondaryInput<=ArmorControls.INPUT_TIMEOUT&&gunner instanceof PlayerEntity?secondaryInput:new ArmorControls.Input(0,0,turretYaw(),elevation(),false));
        if(driver instanceof PlayerEntity p&&!com.bobincraft.combat.GuardInjuryPose.canUseHands(com.bobincraft.combat.PlayerInjuries.missing(p)))dCtrl=ArmorControls.Input.STOP;
        if(gunner instanceof PlayerEntity p&&!com.bobincraft.combat.GuardInjuryPose.canUseHands(com.bobincraft.combat.PlayerInjuries.missing(p)))gCtrl=new ArmorControls.Input(0,0,turretYaw(),elevation(),false);
        Entity threat=null;
        if(operator instanceof WarzoneGuardEntity guard){
            if(!guard.canUseHands()||guard.isInjuryProne()){guard.stopRiding();operator=null;driver=null;gunner=null;}
            else {if(!chambered()&&cooldown==0&&ammo()>0)beginLoading();threat=guard.vehicleThreat();if(threat!=null){Vec3d aim=threat.getBoundingBox().getCenter().subtract(muzzleBase());double horizontal=Math.hypot(aim.x,aim.z);
                double flight=horizontal/ArmorControls.SHELL_SPEED;float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y+ArmorControls.SHELL_GRAVITY*.5*flight*flight,horizontal));
                gCtrl=new ArmorControls.Input(0,0,yaw,MathHelper.clamp(pitch,-28,12),true);dCtrl=ArmorControls.Input.STOP;gunner=guard;}
                else if(!fixed()&&guard.vehiclePatrolPoint()!=null){Vec3d destination=guard.vehiclePatrolPoint().subtract(getPos());float yaw=(float)Math.toDegrees(Math.atan2(-destination.x,destination.z));float difference=MathHelper.wrapDegrees(yaw-getYaw());
                    dCtrl=new ArmorControls.Input(destination.horizontalLengthSquared()>36&&Math.abs(difference)<65?.45f:0,MathHelper.clamp(difference/25,-1,1),getYaw(),0,false);driver=guard;gunner=guard;gCtrl=new ArmorControls.Input(0,0,getYaw(),0,false);}
            }
        }
        if(gunner!=null){cachedTurret=ArmorControls.slew(turretYaw(),gCtrl.yaw(),3f);dataTracker.set(TURRET,cachedTurret);cachedElevation=ArmorControls.slew(elevation(),MathHelper.clamp(gCtrl.pitch(),-28,fixed()?6:12),2f);dataTracker.set(ELEVATION,cachedElevation);}
        speed=ArmorControls.speed(speed,driver==null?0:dCtrl.forward(),fixed());
        if(!fixed()){
            float turn=driver==null?0:dCtrl.steer()*1.5f;
            if(turn!=0&&getWorld() instanceof ServerWorld sw)knockDownObstacles(sw,getPos(),getYaw()+turn);
            if(turn!=0){
                float oldTurretOffset=MathHelper.wrapDegrees(turretYaw()-getYaw());
                setYaw(getYaw()+turn);setBoundingBox(calculateBoundingBox());
                if(!ArmorTerrainMotion.clear(getWorld(),this,getPos(),getYaw(),suspension(),bank())&&!ArmorTerrainMotion.clear(getWorld(),this,getPos().add(0,.05,0),getYaw(),suspension(),bank())){
                    setYaw(getYaw()-turn);setBoundingBox(calculateBoundingBox());
                }else if(gunner==null){
                    cachedTurret=MathHelper.wrapDegrees(getYaw()+oldTurretOffset);
                    dataTracker.set(TURRET,cachedTurret);
                }
            }
            double angle=Math.toRadians(getYaw());Vec3d horizontal=new Vec3d(-Math.sin(angle)*speed,0,Math.cos(angle)*speed);
            Box envelope=getBoundingBox().stretch(horizontal).expand(0,1.1,0);
            if(!getWorld().getWorldBorder().contains(envelope)||!loadedEnvelope(envelope))horizontal=Vec3d.ZERO;
            Vec3d before=getPos();
            if(getWorld() instanceof ServerWorld sw&&horizontal.lengthSquared()>1e-6)knockDownObstacles(sw,before.add(horizontal),getYaw());
            var step=ArmorTerrainMotion.advance(getWorld(),this,before,horizontal,getYaw(),suspension(),bank(),getVelocity().y);
            setPosition(step.position());setVelocity(step.position().subtract(before));setOnGround(step.grounded());
            cachedSuspension=step.pitch();cachedBank=step.roll();
            dataTracker.set(SUSPENSION,cachedSuspension);dataTracker.set(BANK,cachedBank);
            if(step.blocked()&&step.position().y<=before.y&&step.pitch()==previousSuspension)speed*=.65;
            if(isTouchingWater())speed*=.9;
            crushFrontEntities(driver!=null?driver:operator);
        }else setVelocity(Vec3d.ZERO);
        if(gunner!=null&&chambered()&&ArmorControls.fireReady(!ArmorContent.inHub(getWorld()),ammo(),cooldown,MathHelper.wrapDegrees(gCtrl.yaw()-turretYaw()),gCtrl.pitch()-elevation())&&gCtrl.fire()){
            if(threat==null||safeNpcShot((WarzoneGuardEntity)gunner,threat))fire(gunner);
        }
        carryDeckRiders(riders);tickClimbOuts();dataTracker.set(LOADING,cooldown);velocityDirty=true;if(getY()<getWorld().getBottomY()-8)discard();
    }
    public void requestReload(PlayerEntity player){
        if(getWorld().isClient||!hasPassenger(player)||ArmorContent.inHub(getWorld())||!com.bobincraft.combat.GuardInjuryPose.canUseHands(com.bobincraft.combat.PlayerInjuries.missing(player)))return;
        if(ammo()<ArmorControls.MAGAZINE){for(int i=0;i<player.getInventory().size();i++){var stack=player.getInventory().getStack(i);if(stack.isOf(ArmorContent.SHELL)){dataTracker.set(AMMO,ammo()+1);if(!player.isCreative())stack.decrement(1);break;}}}
        if(!chambered()&&cooldown==0&&ammo()>0)beginLoading();
    }
    private void beginLoading(){cooldown=ArmorControls.RELOAD_TICKS;getWorld().playSound(null,getBlockPos(),ArmorContent.BREECH,SoundCategory.HOSTILE,.8f,1f);}
    private boolean loadedEnvelope(Box box){for(int x=MathHelper.floor(box.minX)>>4;x<=MathHelper.floor(box.maxX)>>4;x++)for(int z=MathHelper.floor(box.minZ)>>4;z<=MathHelper.floor(box.maxZ)>>4;z++)if(!getWorld().isChunkLoaded(x,z))return false;return true;}
    public Vec3d muzzleBase(){return ArmorGeometry.toWorld(this,"turret",ArmorGeometry.GUN_PIVOT);}
    public Vec3d muzzle(){return muzzleBase().add(Vec3d.fromPolar(elevation(),turretYaw()).multiply(ArmorGeometry.BARREL_LENGTH));}
    private boolean safeNpcShot(WarzoneGuardEntity guard,Entity target){
        Vec3d start=muzzle(),end=target.getBoundingBox().getCenter();if(start.squaredDistanceTo(end)<64)return false;
        if(getWorld().raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this)).getType()!=HitResult.Type.MISS)return false;
        for(var ally:getWorld().getOtherEntities(this,new Box(start,end).expand(2),e->e!=guard&&e instanceof LivingEntity&&e.isAlive()))
            if(!(ally instanceof LivingEntity living&&guard.isHostileTarget(living))&&(ally.getBoundingBox().expand(1).raycast(start,end).isPresent()||ally.squaredDistanceTo(target)<25))return false;
        return true;
    }
    private void fire(LivingEntity operator){
        Vec3d start=muzzleBase(),tip=muzzle();
        var hit=getWorld().raycast(new RaycastContext(start,tip,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,this));
        if(hit.getType()!=HitResult.Type.MISS)return;
        var shell=ArmorContent.PROJECTILE.create(getWorld());if(shell==null)return;
        shell.launch(this,operator,tip,Vec3d.fromPolar(elevation(),turretYaw()).multiply(ArmorControls.SHELL_SPEED));
        if(getWorld().spawnEntity(shell)){dataTracker.set(AMMO,ammo()-1);dataTracker.set(CHAMBER,false);
            // Heavy 120mm MG253 recoil: hull suspension pitch/roll jolt and backward recoil impulse
            double relRad=Math.toRadians(turretYaw()-getYaw());
            if(!fixed())speed=MathHelper.clamp(speed-.045*Math.cos(relRad),-.12,.30);
            cachedSuspension=MathHelper.clamp(cachedSuspension-(float)(Math.cos(relRad)*3.2),-22f,22f);
            cachedBank=MathHelper.clamp(cachedBank+(float)(Math.sin(relRad)*2.2),-18f,18f);
            dataTracker.set(SUSPENSION,cachedSuspension);
            dataTracker.set(BANK,cachedBank);
            getWorld().playSound(null,getBlockPos(),ArmorContent.CANNON,SoundCategory.HOSTILE,5,.95f+random.nextFloat()*.08f);
            if(getWorld() instanceof ServerWorld world){
                Vec3d dir=Vec3d.fromPolar(elevation(),turretYaw());
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH,tip.x,tip.y,tip.z,2,0,0,0,0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,tip.x+dir.x*.35,tip.y+dir.y*.35,tip.z+dir.z*.35,1,0,0,0,0);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,tip.x,tip.y,tip.z,18,.22,.22,.22,.12);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,tip.x,tip.y,tip.z,22,.28,.25,.28,.09);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,tip.x+dir.x*.6,tip.y+dir.y*.6,tip.z+dir.z*.6,8,.35,.25,.35,.02);
            }
        }
    }
    @Override public boolean damage(DamageSource source,float amount){
        if(getWorld().isClient||isInvulnerableTo(source)||!Float.isFinite(amount)||amount<=0||ArmorContent.inHub(getWorld()))return false;
        cachedHealth=Math.max(0,armorHealth()-amount);
        dataTracker.set(HEALTH,cachedHealth);
        if(armorHealth()<=0){removeAllPassengers();if(site>=0&&getWorld() instanceof ServerWorld world)ArmorRoster.get(world).destroyed(site,getUuid());
            ((ServerWorld)getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,getX(),getY()+2,getZ(),32,1,1,1,.02);discard();}
        return true;
    }
    @Override public void updateTrackedPositionAndAngles(double x,double y,double z,float yaw,float pitch,int ticks,boolean interpolate){targetX=x;targetY=y;targetZ=z;targetYaw=yaw;lerpTicks=MathHelper.clamp(ticks,1,3);}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt){nbt.putInt("ArmorVersion",4);nbt.putBoolean("HatchOpen",hatchOpen());nbt.putBoolean("Chamber",chambered());nbt.putBoolean("Fixed",fixed());nbt.putFloat("Turret",turretYaw());nbt.putFloat("Elevation",elevation());nbt.putFloat("Armor",armorHealth());nbt.putInt("Ammo",ammo());nbt.putInt("Cooldown",cooldown);nbt.putInt("Faction",factionId());nbt.putInt("Site",site);}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt){
        dataTracker.set(HATCH,!nbt.contains("HatchOpen")||nbt.getBoolean("HatchOpen"));hatchAngle=hatchOpen()?100:0;previousHatch=hatchAngle;
        configure(nbt.getBoolean("Fixed"),MathHelper.clamp(nbt.getInt("Faction"),-1,com.bobincraft.warzone.Faction.values().length-1),nbt.contains("Site")?nbt.getInt("Site"):-1,getYaw());
        cachedTurret=finite(nbt.getFloat("Turret"),getYaw())%360;dataTracker.set(TURRET,cachedTurret);
        cachedElevation=MathHelper.clamp(finite(nbt.getFloat("Elevation"),0),-28,12);dataTracker.set(ELEVATION,cachedElevation);
        cachedHealth=MathHelper.clamp(finite(nbt.getFloat("Armor"),100),0,100);dataTracker.set(HEALTH,cachedHealth);
        dataTracker.set(AMMO,MathHelper.clamp(nbt.getInt("Ammo"),0,ArmorControls.MAGAZINE));cooldown=MathHelper.clamp(nbt.getInt("Cooldown"),0,ArmorControls.RELOAD_TICKS);
        if(fixed()&&nbt.getInt("ArmorVersion")<2)setPosition(getPos().add(0,1.90-ArmorControls.BURIAL_DEPTH,0));
        dataTracker.set(CHAMBER,nbt.contains("Chamber")?nbt.getBoolean("Chamber"):cooldown==0);
        dataTracker.set(LOADING,cooldown);previousTurret=turretYaw();previousElevation=elevation();previousSuspension=suspension();previousBank=bank();input=ArmorControls.Input.STOP;lastInput=-100;
    }
    private static float finite(float value,float fallback){return Float.isFinite(value)?value:fallback;}
}
