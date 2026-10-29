package com.bobincraft.weapon.grom7;

import com.bobincraft.destruction.StructureDamageManager;
import com.bobincraft.destruction.StructureDamageSource;
import com.bobincraft.destruction.StructureNetworkSync;
import com.bobincraft.drone.BlastTerrain;
import com.bobincraft.drone.DroneBlastType;
import com.bobincraft.drone.DroneExplosionEffects;
import com.bobincraft.drone.DronePayloadPhysics;
import com.bobincraft.raid.HubSafety;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Single optimized entity representing a placed or thrown GROM-7 remote demolition charge (Section 10.4–10.10).
 *
 * <p>Supports persistent unique {@code chargeId} binding to a GROM-7 remote (up to 3 charges per remote),
 * visual LED confirmation blinking upon binding, and distance/obstacle-delayed radio detonation.
 */
public final class Grom7ChargeEntity extends Entity {
    private static final TrackedData<String> STATE = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> CHARGE_NUMBER = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ATTACH_FACE = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> ATTACH_ROLL = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<BlockPos> ATTACH_BLOCK = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Integer> BIND_BLINK_TICKS = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BOUND_TO_REMOTE = DataTracker.registerData(Grom7ChargeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private UUID ownerUuid;
    private UUID chargeId = UUID.randomUUID();
    private int activationDelayTicks = -1;
    private boolean detonating = false;
    private int interpolationTicks;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;

    public Grom7ChargeEntity(EntityType<? extends Grom7ChargeEntity> type, World world) {
        super(type, world);
        this.intersectionChecked = true;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STATE, Grom7State.RESTING.name());
        this.dataTracker.startTracking(CHARGE_NUMBER, 1);
        this.dataTracker.startTracking(ATTACH_FACE, Direction.UP.ordinal());
        this.dataTracker.startTracking(ATTACH_ROLL, 0.0f);
        this.dataTracker.startTracking(ATTACH_BLOCK, BlockPos.ORIGIN);
        this.dataTracker.startTracking(BIND_BLINK_TICKS, 0);
        this.dataTracker.startTracking(BOUND_TO_REMOTE, false);
    }

    public void configureThrown(UUID owner, int chargeNumber, Vec3d startPos, Vec3d velocity, float yaw, float pitch) {
        this.ownerUuid = owner;
        this.setChargeNumber(chargeNumber);
        this.setChargeState(Grom7State.THROWN);
        this.setAttachmentFace(Direction.UP);
        this.setAttachmentRoll(0.0f);
        this.setPosition(startPos);
        this.setVelocity(velocity);
        this.setYaw(yaw);
        this.setPitch(pitch);
        this.prevYaw = yaw;
        this.prevPitch = pitch;
    }

    public void configureAttached(UUID owner, int chargeNumber, Grom7Placement.PlacementResult placement) {
        this.ownerUuid = owner;
        this.setChargeNumber(chargeNumber);
        this.setChargeState(Grom7State.ATTACHED);
        this.setAttachmentBlockPos(placement.attachmentBlockPos());
        this.setAttachmentFace(placement.attachmentFace());
        this.setAttachmentRoll(placement.roll());
        this.setPosition(placement.attachmentPoint());
        this.setVelocity(Vec3d.ZERO);
        this.setYaw(placement.yaw());
        this.setPitch(placement.pitch());
        this.prevYaw = placement.yaw();
        this.prevPitch = placement.pitch();
    }

    public Grom7State getChargeState() {
        return Grom7State.fromName(this.dataTracker.get(STATE));
    }

    public void setChargeState(Grom7State state) {
        this.dataTracker.set(STATE, (state == null ? Grom7State.RESTING : state).name());
    }

    public int getChargeNumber() {
        return this.dataTracker.get(CHARGE_NUMBER);
    }

    public void setChargeNumber(int number) {
        this.dataTracker.set(CHARGE_NUMBER, Math.max(1, number));
    }

    public int getBindBlinkTicks() {
        return this.dataTracker.get(BIND_BLINK_TICKS);
    }

    public void triggerBindConfirmationBlink() {
        this.dataTracker.set(BIND_BLINK_TICKS, 18);
        this.dataTracker.set(BOUND_TO_REMOTE, true);
    }

    public boolean isBoundToRemote() {
        return this.dataTracker.get(BOUND_TO_REMOTE);
    }

    public void setBoundToRemote(boolean bound) {
        this.dataTracker.set(BOUND_TO_REMOTE, bound);
    }

    public Direction getAttachmentFace() {
        int ord = this.dataTracker.get(ATTACH_FACE);
        Direction[] dirs = Direction.values();
        return ord >= 0 && ord < dirs.length ? dirs[ord] : Direction.UP;
    }

    public void setAttachmentFace(Direction face) {
        this.dataTracker.set(ATTACH_FACE, (face == null ? Direction.UP : face).ordinal());
    }

    public float getAttachmentRoll() {
        return this.dataTracker.get(ATTACH_ROLL);
    }

    public void setAttachmentRoll(float roll) {
        this.dataTracker.set(ATTACH_ROLL, roll);
    }

    public BlockPos getAttachmentBlockPos() {
        return this.dataTracker.get(ATTACH_BLOCK);
    }

    public void setAttachmentBlockPos(BlockPos pos) {
        this.dataTracker.set(ATTACH_BLOCK, pos == null ? BlockPos.ORIGIN : pos.toImmutable());
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getChargeId() {
        return chargeId;
    }

    public int getActivationDelayTicks() {
        return activationDelayTicks;
    }

    /**
     * Triggers remote activation with the given signal-dependent delay in ticks (Section 10.7).
     */
    public boolean triggerRemoteActivation(int delayTicks) {
        Grom7State current = getChargeState();
        if (!current.canDetonate() || current == Grom7State.ACTIVATED) {
            return false;
        }
        this.setChargeState(Grom7State.ACTIVATED);
        this.activationDelayTicks = Math.max(1, delayTicks);
        return true;
    }

    /**
     * Validates whether the supporting block for an ATTACHED charge still exists; if broken, detaches and falls (Section 10.6).
     */
    public boolean verifyOrDetachSupport(BlockView worldView) {
        if (getChargeState() != Grom7State.ATTACHED) {
            return true;
        }
        boolean supported = Grom7Placement.hasValidSupport(
                worldView, getAttachmentBlockPos(), getAttachmentFace(), getPos());
        if (!supported) {
            setChargeState(Grom7State.FALLING);
            setAttachmentFace(Direction.UP);
            setAttachmentRoll(0.0f);
            setPitch(0.0f);
            setVelocity(new Vec3d(0.0, -0.04, 0.0));
            velocityDirty = true;
            return false;
        }
        return true;
    }

    /**
     * Pure-physics ballistic step usable both in live ServerWorld ticks and in headless unit tests.
     */
    public void stepBallistics(BlockView blockView) {
        Grom7State state = getChargeState();
        if (state == Grom7State.ATTACHED) {
            verifyOrDetachSupport(blockView);
            return;
        }
        if (state != Grom7State.THROWN && state != Grom7State.FALLING) {
            return;
        }

        Vec3d vel = getVelocity();
        Vec3d next = new Vec3d(vel.x * 0.96, (vel.y - 0.042) * 0.98, vel.z * 0.96);

        if (getWorld() != null && !getWorld().isClient) {
            this.move(net.minecraft.entity.MovementType.SELF, next);
            Vec3d actual = this.getVelocity();
            boolean blockedX = Math.abs(actual.x - next.x) > 1.0e-4;
            boolean blockedZ = Math.abs(actual.z - next.z) > 1.0e-4;
            boolean supported = this.isOnGround() || (this.verticalCollision && next.y < 0.0);
            Vec3d bounced = DronePayloadPhysics.afterCollision(next, blockedX, verticalCollision, blockedZ, supported, 0.22);
            if (supported) {
                bounced = new Vec3d(bounced.x * 0.52, bounced.y, bounced.z * 0.52);
            }
            setVelocity(bounced);
            if (supported && bounced.lengthSquared() < 0.0006) {
                setVelocity(Vec3d.ZERO);
                setChargeState(Grom7State.RESTING);
                setAttachmentFace(Direction.UP);
                setPitch(0.0f);
                setAttachmentRoll(0.0f);
            } else {
                setPitch(MathHelper.wrapDegrees(getPitch() + (float) (bounced.length() * 28.0)));
            }
            velocityDirty = true;
        } else if (blockView != null) {
            Vec3d currentPos = getPos();
            Vec3d candidatePos = currentPos.add(next);
            BlockPos belowPos = BlockPos.ofFloored(candidatePos.x, candidatePos.y - 0.02, candidatePos.z);
            BlockState stateBelow = blockView.getBlockState(belowPos);
            var shapeBelow = Grom7Placement.resolveAttachableShape(blockView, belowPos, stateBelow);
            double surfaceTop = belowPos.getY() + (shapeBelow.isEmpty() ? 0.0 : shapeBelow.getMax(Direction.Axis.Y));
            if (!shapeBelow.isEmpty() && candidatePos.y <= surfaceTop + Grom7Placement.SURFACE_EPSILON && next.y <= 0.0) {
                double bounceY = Math.abs(next.y) > 0.12 ? -next.y * 0.20 : 0.0;
                Vec3d bounced = new Vec3d(next.x * 0.48, bounceY, next.z * 0.48);
                setPosition(candidatePos.x, surfaceTop + Grom7Placement.SURFACE_EPSILON, candidatePos.z);
                if (bounced.lengthSquared() < 0.0008) {
                    setVelocity(Vec3d.ZERO);
                    setChargeState(Grom7State.RESTING);
                    setAttachmentFace(Direction.UP);
                    setPitch(0.0f);
                    setAttachmentRoll(0.0f);
                } else {
                    setVelocity(bounced);
                }
            } else {
                setPosition(candidatePos);
                setVelocity(next);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() == null) return;
        if (getWorld().isClient) {
            interpolate();
            return;
        }
        ServerWorld world = (ServerWorld) getWorld();
        int blink = getBindBlinkTicks();
        if (blink > 0) {
            this.dataTracker.set(BIND_BLINK_TICKS, blink - 1);
        }

        if (HubSafety.isHub(world) || getY() < world.getBottomY() - 12 || getChargeState() == Grom7State.DESTROYED) {
            Grom7Manager.unregisterCharge(this);
            discard();
            return;
        }

        Grom7Manager.registerOrUpdateCharge(this);

        Grom7State state = getChargeState();
        if (state == Grom7State.ATTACHED) {
            if ((this.age & 3) == 0) {
                verifyOrDetachSupport(world);
            }
        } else if (state == Grom7State.THROWN || state == Grom7State.FALLING) {
            stepBallistics(world);
        } else if (state == Grom7State.RESTING) {
            if ((this.age & 7) == 0) {
                BlockPos below = BlockPos.ofFloored(getX(), getY() - 0.08, getZ());
                if (world.getBlockState(below).isAir()) {
                    setChargeState(Grom7State.FALLING);
                }
            }
        } else if (state == Grom7State.ACTIVATED) {
            if (activationDelayTicks > 0) {
                activationDelayTicks--;
            }
            if (activationDelayTicks <= 0 && !detonating) {
                detonate(world);
            }
        }
    }

    public void detonate(ServerWorld world) {
        if (detonating || getChargeState() == Grom7State.DESTROYED) return;
        detonating = true;
        Grom7State priorState = getChargeState();
        setChargeState(Grom7State.DESTROYED);
        Grom7Manager.unregisterCharge(this);

        if (world == null || HubSafety.isHub(world)) {
            discard();
            return;
        }

        Vec3d center = getPos().add(0.0, 0.06, 0.0);
        Vec3d structuralOrigin = center;
        if (priorState == Grom7State.ATTACHED) {
            Vec3i normal = getAttachmentFace().getVector();
            structuralOrigin = getPos().subtract(normal.getX() * 0.18, normal.getY() * 0.18, normal.getZ() * 0.18);
        }

        ServerPlayerEntity owner = ownerUuid != null && world.getServer() != null
                ? world.getServer().getPlayerManager().getPlayer(ownerUuid)
                : null;

        StructureDamageSource structSource = new StructureDamageSource(
                this.chargeId,
                StructureDamageSource.Kind.DEMOLITION_CHARGE,
                ownerUuid,
                structuralOrigin,
                Grom7Content.blastRadius
        );
        StructureDamageManager.applyDamage(world, structuralOrigin, structSource, Grom7Content.structuralDamage);

        applyPureHighExplosivePressure(world, center, owner);

        boolean water = world.getFluidState(BlockPos.ofFloored(center)).isIn(FluidTags.WATER);
        var surface = world.raycast(new RaycastContext(center, center.add(0, -2.0, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        if (priorState == Grom7State.ATTACHED) {
            Vec3d faceNormal = Vec3d.of(getAttachmentFace().getVector());
            var attachedHit = world.raycast(new RaycastContext(center, center.subtract(faceNormal.multiply(1.0)),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
            if (attachedHit.getType() == HitResult.Type.BLOCK && attachedHit.getBlockPos().equals(getAttachmentBlockPos())) surface = attachedHit;
        }
        BlockState material = surface.getType() == HitResult.Type.BLOCK ? world.getBlockState(surface.getBlockPos()) : null;
        if (material == null && priorState == Grom7State.ATTACHED) {
            material = world.getBlockState(getAttachmentBlockPos());
        }
        var terrain = BlastTerrain.collect(world, this, owner, center, DroneBlastType.BOMB_HIGH_EXPLOSIVE, water);
        terrain.apply(world, this);
        DroneExplosionEffects.playDemolitionBlast(world, owner, this.chargeId, center, water, material, surface.getPos());

        List<Grom7ChargeEntity> nearbyCharges = world.getEntitiesByClass(
                Grom7ChargeEntity.class,
                new Box(center, center).expand(3.5),
                other -> other != this && other.isAlive() && other.getChargeState().canDetonate()
        );
        for (Grom7ChargeEntity other : nearbyCharges) {
            other.triggerRemoteActivation(2);
        }

        discard();
    }

    private void applyPureHighExplosivePressure(ServerWorld world, Vec3d center, ServerPlayerEntity owner) {
        double radius = Grom7Content.blastRadius;
        float maxDamage = Grom7Content.maxPressureDamage;
        Box area = new Box(center, center).expand(radius);
        DamageSource dmgSource = owner != null
                ? world.getDamageSources().explosion(this, owner)
                : world.getDamageSources().explosion(this, null);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            double dist = target.getPos().add(0, target.getHeight() * 0.5, 0).distanceTo(center);
            if (dist >= radius) continue;
            double falloff = 1.0 - (dist / radius);
            float dmg = (float) (maxDamage * (falloff * falloff + falloff) * 0.5);
            if (dmg > 0.25f) {
                target.damage(dmgSource, dmg);
            }
        }

        StructureNetworkSync.scheduleLayeredSound(world, center, Grom7Content.GROM7_DETONATE_CRACK_SOUND,
                SoundCategory.BLOCKS, 1.95f, 0.88f + world.random.nextFloat() * 0.08f, 0);
        StructureNetworkSync.scheduleLayeredSound(world, center, Grom7Content.GROM7_DETONATE_SUB_SOUND,
                SoundCategory.BLOCKS, 2.25f, 0.66f + world.random.nextFloat() * 0.06f, 0);
        StructureNetworkSync.scheduleLayeredSound(world, center, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 1.45f, 0.62f, 0);

        StructureNetworkSync.scheduleLayeredSound(world, center,
                Grom7Content.GROM7_DEBRIS_CRUMBLE_SOUND, SoundCategory.BLOCKS, 1.65f, 0.72f, 5);
        StructureNetworkSync.scheduleLayeredSound(world, center,
                SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.BLOCKS, 1.45f, 0.58f, 6);

        StructureNetworkSync.scheduleLayeredSound(world, center,
                Grom7Content.GROM7_DEBRIS_CRUMBLE_SOUND, SoundCategory.BLOCKS, 1.35f, 0.55f, 14);
        StructureNetworkSync.scheduleLayeredSound(world, center,
                SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.20f, 0.62f, 22);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isRemoved() || getWorld() == null || getWorld().isClient) return false;
        if (source != null && (source.isOf(net.minecraft.entity.damage.DamageTypes.EXPLOSION)
                || source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_EXPLOSION))) {
            if (getWorld() instanceof ServerWorld) {
                triggerRemoteActivation(2);
            }
            return true;
        }
        // If destroyed by non-explosive physical damage, clean up its remote binding without exploding
        if (amount >= 3.0f && getWorld() instanceof ServerWorld) {
            setChargeState(Grom7State.DESTROYED);
            Grom7Manager.unregisterCharge(this);
            getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_METAL_BREAK, SoundCategory.BLOCKS, 0.7f, 1.2f);
            discard();
            return true;
        }
        return false;
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        return EntityDimensions.fixed(0.24f, 0.16f);
    }

    @Override
    public boolean canHit() {
        return !isRemoved() && getChargeState() != Grom7State.DESTROYED;
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                               int ticks, boolean interpolate) {
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = yaw;
        targetPitch = pitch;
        interpolationTicks = Math.max(1, Math.min(ticks, 3));
    }

    private void interpolate() {
        if (interpolationTicks <= 0) return;
        setPosition(
                getX() + (targetX - getX()) / interpolationTicks,
                getY() + (targetY - getY()) / interpolationTicks,
                getZ() + (targetZ - getZ()) / interpolationTicks
        );
        setYaw(getYaw() + MathHelper.wrapDegrees(targetYaw - getYaw()) / interpolationTicks);
        setPitch(getPitch() + MathHelper.wrapDegrees(targetPitch - getPitch()) / interpolationTicks);
        interpolationTicks--;
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("ChargeId")) {
            this.chargeId = nbt.getUuid("ChargeId");
        }
        if (nbt.containsUuid("Owner")) {
            this.ownerUuid = nbt.getUuid("Owner");
        }
        setChargeState(Grom7State.fromName(nbt.getString("State")));
        setChargeNumber(nbt.getInt("ChargeNumber"));
        setBoundToRemote(nbt.getBoolean("BoundToRemote"));
        setAttachmentFace(Direction.byId(nbt.getInt("AttachFace")));
        setAttachmentRoll(nbt.getFloat("AttachRoll"));
        if (nbt.contains("AttachBlockX")) {
            setAttachmentBlockPos(new BlockPos(
                    nbt.getInt("AttachBlockX"),
                    nbt.getInt("AttachBlockY"),
                    nbt.getInt("AttachBlockZ")
            ));
        }
        if (nbt.contains("ExactX")) {
            setPosition(nbt.getDouble("ExactX"), nbt.getDouble("ExactY"), nbt.getDouble("ExactZ"));
        }
        if (nbt.contains("ExactYaw")) {
            setYaw(nbt.getFloat("ExactYaw"));
            setPitch(nbt.getFloat("ExactPitch"));
        }
        this.activationDelayTicks = nbt.getInt("ActivationDelay");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putUuid("ChargeId", this.chargeId);
        if (this.ownerUuid != null) {
            nbt.putUuid("Owner", this.ownerUuid);
        }
        nbt.putString("State", getChargeState().name());
        nbt.putInt("ChargeNumber", getChargeNumber());
        nbt.putBoolean("BoundToRemote", isBoundToRemote());
        nbt.putInt("AttachFace", getAttachmentFace().getId());
        nbt.putFloat("AttachRoll", getAttachmentRoll());
        BlockPos ap = getAttachmentBlockPos();
        nbt.putInt("AttachBlockX", ap.getX());
        nbt.putInt("AttachBlockY", ap.getY());
        nbt.putInt("AttachBlockZ", ap.getZ());
        nbt.putDouble("ExactX", getX());
        nbt.putDouble("ExactY", getY());
        nbt.putDouble("ExactZ", getZ());
        nbt.putFloat("ExactYaw", getYaw());
        nbt.putFloat("ExactPitch", getPitch());
        nbt.putInt("ActivationDelay", this.activationDelayTicks);
    }
}
