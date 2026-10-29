package com.bobincraft.drone;

import com.bobincraft.raid.HubSafety;
import com.bobincraft.vehicle.ArmorGeometry;
import com.bobincraft.vehicle.ArmoredVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.UUID;

/** Physical charges use family-specific, server-owned ballistics and fuzes. Reloaded charges remain inert. */
public final class DronePayloadEntity extends Entity {
    private static final TrackedData<Boolean> HAND_THROWN = DataTracker.registerData(DronePayloadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> FUSE = DataTracker.registerData(DronePayloadEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> RESTING = DataTracker.registerData(DronePayloadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> BLAST = DataTracker.registerData(DronePayloadEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private UUID ownerUuid;
    private UUID carrierUuid;
    private boolean live;
    private boolean carrierSeparated;
    private boolean detonating;
    private int liveTicks;
    private int interpolationTicks;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;

    public DronePayloadEntity(EntityType<? extends DronePayloadEntity> type, World world) { super(type, world); }

    public void configure(UUID owner, UUID carrier, Vec3d position, Vec3d velocity) {
        configure(owner, carrier, position, velocity, DroneBlastType.DROPPED_GRENADE);
    }

    public void configure(UUID owner, UUID carrier, Vec3d position, Vec3d velocity, DroneBlastType type) {
        dataTracker.set(HAND_THROWN, false);
        dataTracker.set(BLAST, type.kamikaze ? 0 : type.ordinal());
        ownerUuid = owner;
        carrierUuid = carrier;
        setPosition(position);
        setVelocity(DronePayloadProfile.of(getBlastType()).initialVelocity(velocity));
        live = true;
        carrierSeparated = false;
        dataTracker.set(FUSE, DronePayloadProfile.of(getBlastType()).fuseTicks);
        liveTicks = 0;
        dataTracker.set(RESTING, false);
    }

    @Override protected void initDataTracker() {
        dataTracker.startTracking(HAND_THROWN, false);
        dataTracker.startTracking(FUSE, DronePayloadSafety.FUSE_TICKS);
        dataTracker.startTracking(RESTING, false);
        dataTracker.startTracking(BLAST, 0);
    }

    public void configureHandGrenade(UUID owner, Vec3d position, Vec3d velocity) {
        configureHandGrenade(owner, position, velocity, com.bobincraft.vehicle.GrenadeTiming.FUSE_TICKS);
    }

    public void configureHandGrenade(UUID owner, Vec3d position, Vec3d velocity, int remainingFuseTicks) {
        configure(owner, null, position, Vec3d.ZERO, DroneBlastType.DROPPED_GRENADE);
        dataTracker.set(HAND_THROWN, true);
        setVelocity(velocity);
        dataTracker.set(FUSE, Math.max(1, remainingFuseTicks));
    }

    public boolean handThrown() { return dataTracker.get(HAND_THROWN); }
    @Override public net.minecraft.entity.EntityDimensions getDimensions(net.minecraft.entity.EntityPose pose) {
        return handThrown() ? net.minecraft.entity.EntityDimensions.fixed(.18f, .22f) : super.getDimensions(pose);
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data.equals(HAND_THROWN)) calculateDimensions();
    }
    public int getFuseTicks() { return dataTracker.get(FUSE); }
    public boolean isResting() { return dataTracker.get(RESTING); }
    public DroneBlastType getBlastType() {
        int index = dataTracker.get(BLAST);
        return index >= 0 && index < DroneBlastType.values().length && !DroneBlastType.values()[index].kamikaze
                ? DroneBlastType.values()[index] : DroneBlastType.DROPPED_GRENADE;
    }

    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) { interpolate(); return; }
        ServerWorld world = (ServerWorld) getWorld();
        ServerPlayerEntity owner = ownerUuid == null ? null : world.getServer().getPlayerManager().getPlayer(ownerUuid);
        boolean invalidOwner = owner == null || !owner.isAlive() || owner.getWorld() != world;
        if (!live || !handThrown() && invalidOwner || HubSafety.isHub(world)
                || getY() < world.getBottomY() - 8) { discard(); return; }
        DronePayloadProfile profile = DronePayloadProfile.of(getBlastType());
        liveTicks++;
        double clearDistance = getBlastType().pressureRadius() + .5;
        Entity carrier = carrierUuid == null ? null : world.getEntity(carrierUuid);
        if (carrier != null) carrierSeparated = DronePayloadSafety.carrierSeparated(carrierSeparated, squaredDistanceTo(carrier), clearDistance);
        Vec3d next = profile.fallingVelocity(getVelocity());
        Vec3d destination = getPos().add(next);
        if (!world.isChunkLoaded(MathHelper.floor(destination.x) >> 4, MathHelper.floor(destination.z) >> 4)
                || !world.getWorldBorder().contains(getBoundingBox().offset(next))) { discard(); return; }
        setVelocity(next);
        var contact = DronePayloadMotion.move(this, next);
        boolean blockedX = contact.x(), blockedZ = contact.z(), supported = contact.supported();
        Vec3d bounced = handThrown()
                ? DronePayloadPhysics.afterHandGrenadeCollision(next, blockedX, verticalCollision, blockedZ, supported)
                : DronePayloadPhysics.afterCollision(next, blockedX, verticalCollision, blockedZ, supported, profile.bounce);
        if (handThrown() && (blockedX || blockedZ || (verticalCollision && Math.abs(next.y) > 0.10))) {
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_METAL_HIT, SoundCategory.BLOCKS, 0.35f, 1.45f);
        }
        setVelocity(bounced);
        boolean resting = supported && bounced.lengthSquared() < 1e-6;
        dataTracker.set(RESTING, resting);
        if (!resting) {
            setYaw(MathHelper.wrapDegrees(getYaw() + profile.spin));
            if (handThrown()) {
                float rollAdvance = supported
                        ? (float) (bounced.horizontalLength() * 310.0)
                        : (float) (bounced.length() * 140.0);
                setPitch(MathHelper.wrapDegrees(getPitch() + rollAdvance));
            }
        }
        velocityDirty = true;
        if (carrier != null) carrierSeparated = DronePayloadSafety.carrierSeparated(carrierSeparated, squaredDistanceTo(carrier), clearDistance);
        dataTracker.set(FUSE, DronePayloadSafety.tickFuse(getFuseTicks()));
        double ground = Double.POSITIVE_INFINITY;
        if (profile == DronePayloadProfile.FRAGMENTATION && liveTicks >= profile.armingTicks) {
            boolean enteringOpenHatch = false;
            for (Entity e : world.getOtherEntities(this, getBoundingBox().expand(2.5),
                    cand -> cand instanceof ArmoredVehicleEntity && cand.isAlive())) {
                if (ArmorGeometry.insideOrEnteringOpenHatch((ArmoredVehicleEntity) e, getPos().add(0, 0.10, 0))) {
                    enteringOpenHatch = true;
                    break;
                }
            }
            if (!enteringOpenHatch) {
                Vec3d end = getPos().add(0, -1.8, 0);
                if (world.isChunkLoaded(MathHelper.floor(end.x) >> 4, MathHelper.floor(end.z) >> 4)) {
                    var hit = world.raycast(new RaycastContext(getPos(), end, RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.ANY, this));
                    if (hit.getType() != HitResult.Type.MISS) ground = getY() - hit.getPos().y;
                }
            }
        }
        if (live && !detonating && profile.detonates(liveTicks, getFuseTicks(), horizontalCollision || verticalCollision || supported, ground)) {
            detonating = true;
            DroneExplosion.explode(world, this, owner, getBlastType());
            discard();
        }
    }

    public boolean protectsCarrier(UUID entityUuid) {
        return live && DronePayloadSafety.protectsCarrier(carrierUuid, entityUuid, carrierSeparated);
    }

    @Override public boolean canHit() { return false; }

    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                                       int ticks, boolean interpolate) {
        targetX = x; targetY = y; targetZ = z; targetYaw = yaw; targetPitch = pitch;
        interpolationTicks = Math.max(1, Math.min(ticks, 3));
    }

    private void interpolate() {
        if (interpolationTicks <= 0) return;
        setPosition(getX() + (targetX - getX()) / interpolationTicks, getY() + (targetY - getY()) / interpolationTicks,
                getZ() + (targetZ - getZ()) / interpolationTicks);
        setYaw(getYaw() + MathHelper.wrapDegrees(targetYaw - getYaw()) / interpolationTicks);
        setPitch(getPitch() + MathHelper.wrapDegrees(targetPitch - getPitch()) / interpolationTicks);
        interpolationTicks--;
    }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        DroneBlastType type = DroneBlastType.read(nbt.getString("BlastType"));
        dataTracker.set(BLAST, type.kamikaze ? 0 : type.ordinal());
        if (nbt.containsUuid("Owner")) ownerUuid = nbt.getUuid("Owner");
        dataTracker.set(HAND_THROWN, nbt.getBoolean("HandThrown"));
        live = false;
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("BlastType", getBlastType().name());
        nbt.putBoolean("HandThrown", handThrown());
        if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
    }
}
