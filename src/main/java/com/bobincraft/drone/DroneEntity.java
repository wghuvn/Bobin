package com.bobincraft.drone;

import com.bobincraft.raid.HubSafety;
import com.bobincraft.vehicle.ArmorGeometry;
import com.bobincraft.vehicle.ArmoredVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/** Server-simulated multirotor and fixed-wing aircraft. The client supplies stick inputs only. */
public final class DroneEntity extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger("bobin_craft.drone");
    private static final TrackedData<Optional<UUID>> OWNER = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<Float> BATTERY = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SIGNAL = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> BANK = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> THROTTLE = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> PAYLOADS = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ARMED = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> ANGLE = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> DOWN_CAMERA = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> LOOK_PITCH = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> HOLDING = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> LINKED = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> AUTO_LANDING = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(DroneEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private DroneFlight.Input input = DroneFlight.Input.IDLE;
    private long lastInputTick;
    private boolean controlled;
    private boolean detonating;
    private float batteryTicks;
    private float health = 8;
    private int dropCooldown;
    private int idleTicks;
    private int interpolationTicks;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private Vec3d hoverPosition;
    private boolean holdingHorizontal, holdingVertical;
    private boolean flightContactReady;
    private int landingTicks;
    private int signalLossTicks;

    public DroneEntity(EntityType<? extends DroneEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override protected void initDataTracker() {
        dataTracker.startTracking(OWNER, Optional.empty());
        dataTracker.startTracking(BATTERY, 1f); dataTracker.startTracking(SIGNAL, 1f);
        dataTracker.startTracking(BANK, 0f); dataTracker.startTracking(THROTTLE, 0f);
        dataTracker.startTracking(PAYLOADS, 0); dataTracker.startTracking(ARMED, false);
        dataTracker.startTracking(ANGLE, true); dataTracker.startTracking(DOWN_CAMERA, false);
        dataTracker.startTracking(LOOK_PITCH, 0f);
        dataTracker.startTracking(HOLDING, false);
        dataTracker.startTracking(LINKED, true);
        dataTracker.startTracking(AUTO_LANDING, false);
        dataTracker.startTracking(VARIANT, 0);
    }

    public DroneType getDroneType() { return getType() == DroneContent.FIXED_WING_DRONE ? DroneType.FIXED_WING : getType() == DroneContent.HEAVY_DRONE ? DroneType.HEAVY : getType() == DroneContent.BOMBER_DRONE ? DroneType.BOMBER : DroneType.FPV; }
    public DroneVariant getVariant() { return DroneVariant.tracked(dataTracker.get(VARIANT), getDroneType()); }
    public void setVariant(DroneVariant variant) { dataTracker.set(VARIANT, DroneVariant.saved(variant.id, getDroneType()).ordinal()); }
    public float getBatteryFraction() { return dataTracker.get(BATTERY); }
    public float getSignalStrength() { return dataTracker.get(SIGNAL); }
    public int getPayloadCount() { return dataTracker.get(PAYLOADS); }
    public UUID getOwnerUuid() { return dataTracker.get(OWNER).orElse(null); }
    public boolean isArmed() { return dataTracker.get(ARMED); }
    public boolean isAngleMode() { return dataTracker.get(ANGLE); }
    public boolean isDownCamera() { return dataTracker.get(DOWN_CAMERA); }
    public float getBankAngle() { return dataTracker.get(BANK); }
    public float getThrottle() { return dataTracker.get(THROTTLE); }
    public float getLookPitch() { return dataTracker.get(LOOK_PITCH); }
    public float getCameraTilt() {
        if (getDroneType().isCarrier() && isDownCamera()) return 90f;
        return isAngleMode() ? dataTracker.get(LOOK_PITCH) - getPitch() : -18f;
    }
    public boolean isHoverHolding() { return isArmed() && isAngleMode() && dataTracker.get(HOLDING); }
    public boolean isLinked() { return dataTracker.get(LINKED); }
    public boolean isAutoLanding() { return dataTracker.get(AUTO_LANDING); }

    public static float maxHealthFor(DroneType type) {
        return switch (type) {
            case FPV -> 6.0f;
            case HEAVY -> 18.0f;
            case FIXED_WING -> 28.0f;
            default -> 10.0f;
        };
    }

    public void configure(ServerPlayerEntity owner, NbtCompound itemData) {
        dataTracker.set(OWNER, Optional.of(owner.getUuid()));
        float fraction = itemData != null && itemData.contains("DroneBattery") ? itemData.getFloat("DroneBattery") : 1;
        if (!Float.isFinite(fraction)) fraction = 0;
        batteryTicks = MathHelper.clamp(fraction, 0, 1) * getDroneType().batteryTicks;
        dataTracker.set(BATTERY, batteryTicks / getDroneType().batteryTicks);
        int payload = itemData != null && itemData.contains("DronePayloads") ? itemData.getInt("DronePayloads") : getVariant().payloadCapacity();
        dataTracker.set(PAYLOADS, MathHelper.clamp(payload, 0, getVariant().payloadCapacity()));
        health = maxHealthFor(getDroneType());
        lastInputTick = getWorld().getTime();
    }

    public void startControl() {
        signalLossTicks = 0;
        controlled = true; idleTicks = 0;
        lastInputTick = getWorld().getTime();
        input = DroneFlight.Input.IDLE;
        captureHover();
    }

    public void releaseControl() {
        controlled = false;
        flightContactReady = false;
        dataTracker.set(ARMED, false);
        dataTracker.set(THROTTLE, 0f);
        dataTracker.set(HOLDING, false);
        input = DroneFlight.Input.IDLE;
    }

    public void unlinkAndLand() {
        releaseControl();
        dataTracker.set(LINKED, false);
        dataTracker.set(AUTO_LANDING, true);
        landingTicks = 0;
        idleTicks = 0;
        captureHover();
    }

    public void acceptInput(DroneFlight.Input input, int flags, float lookPitch) {
        if (!input.valid() || !Float.isFinite(lookPitch) || !controlled || getBatteryFraction() <= 0) return;
        if (getSignalStrength() <= .005f) {
            lastInputTick = getWorld().getTime();
            this.input = new DroneFlight.Input(0, 0, 0, .5f);
            return;
        }
        this.input = input;
        dataTracker.set(LOOK_PITCH, MathHelper.clamp(lookPitch, -80, 80));
        lastInputTick = getWorld().getTime();
        if ((flags & DroneNetworking.ARM) != 0) {
            flightContactReady = false;
            if (isArmed()) dataTracker.set(ARMED, false);
            else if (isAngleMode() || input.throttle() <= 0.08f && getThrottle() <= 0.08f) {
                dataTracker.set(ARMED, true);
                if (getDroneType().isFixedWing() && getVelocity().horizontalLengthSquared() < 0.04) {
                    Vec3d launchBoost = Vec3d.fromPolar(0, getYaw()).multiply(DroneFlight.FIXED_WING_MIN_CRUISE).add(0, 0.12, 0);
                    setVelocity(launchBoost);
                    dataTracker.set(THROTTLE, 0.62f);
                }
                captureHover();
            }
        }
        if ((flags & DroneNetworking.LEVEL) != 0 && getDroneType().isStrikeImpact()) {
            dataTracker.set(ANGLE, !isAngleMode());
            captureHover();
        }
        if ((flags & DroneNetworking.CAMERA) != 0 && getDroneType().isCarrier()) dataTracker.set(DOWN_CAMERA, !isDownCamera());
        if ((flags & DroneNetworking.DROP) != 0) dropPayload();
    }

    private ServerPlayerEntity owner() {
        return getOwnerUuid() == null || getWorld().getServer() == null ? null
                : getWorld().getServer().getPlayerManager().getPlayer(getOwnerUuid());
    }

    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) { interpolate(); return; }
        ServerWorld world = (ServerWorld) getWorld();
        ServerPlayerEntity owner = owner();
        // Never leave an armed vehicle behind after death, logout, dimension change or world reload.
        if (owner == null || !owner.isAlive() || owner.getWorld() != world || HubSafety.isHub(world)) {
            LOGGER.info("Drone removed: drone={}, type={}, reason=owner_unavailable_or_safe_world", getId(), getDroneType());
            releaseControl(); discard(); return;
        }
        if (dropCooldown > 0) dropCooldown--;
        if (age % 5 == 0) updateSignal(owner, world);
        if (controlled) {
            signalLossTicks = DroneSignal.advanceLoss(signalLossTicks, getSignalStrength());
            if (signalLossTicks > 0) input = new DroneFlight.Input(0, 0, 0, .5f);
            DroneNetworking.EndReason reason = DroneNetworking.controlEndReason(DroneNetworking.controls(owner, this),
                    DroneNetworking.validOperator(owner, this), owner.hurtTime > 0,
                    DroneSignal.sessionSignal(getSignalStrength(), signalLossTicks), getBatteryFraction(), world.getTime(), lastInputTick);
            if (reason != null) {
                DroneNetworking.end(owner, this, reason);
                releaseControl();
            }
        }
        if (isArmed() || isAutoLanding()) {
            batteryTicks = Math.max(0, batteryTicks - (1.0f + getThrottle() * 0.35f));
            dataTracker.set(BATTERY, batteryTicks / getDroneType().batteryTicks);
        }
        if (isAutoLanding() && batteryTicks <= 0) {
            dataTracker.set(AUTO_LANDING, false);
            releaseControl();
        }
        Vec3d before = getVelocity();
        DroneFlight.State initial = new DroneFlight.State(before.x, before.y, before.z, getYaw(), getPitch(), getBankAngle(), getThrottle());
        DroneFlight.State state;
        if (isAutoLanding()) {
            if (hoverPosition == null) captureHover();
            Vec3d error = hoverPosition.subtract(getPos());
            state = DroneFlight.assistedStep(getDroneType(), initial, new DroneFlight.Input(0, 0, 0, 0), 0, error.x, 0, error.z);
            if (++landingTicks > 6000) dataTracker.set(AUTO_LANDING, false);
        } else if (getDroneType().isFixedWing()) {
            state = DroneFlight.fixedWingStep(initial, input, dataTracker.get(LOOK_PITCH), isArmed(), isAngleMode());
        } else if (isArmed() && isAngleMode()) {
            updateHoverTarget();
            Vec3d error = hoverPosition.subtract(getPos());
            state = DroneFlight.assistedStep(getDroneType(), initial, input, dataTracker.get(LOOK_PITCH), error.x, error.y, error.z);
        } else state = DroneFlight.step(getDroneType(), initial, input, isArmed(), false);
        setYaw(state.yaw()); setPitch(state.pitch());
        dataTracker.set(BANK, state.bank()); dataTracker.set(THROTTLE, state.throttle());
        Vec3d velocity = new Vec3d(state.x(), state.y(), state.z());
        Vec3d next = getPos().add(velocity);

        // Chunk tickets are managed by DroneChunkStreamer on END_SERVER_TICK, including when the drone
        // cannot currently tick in an unloaded chunk. Never generate terrain synchronously from entity tick.
        // If the next chunk is still preparing, hold at its boundary instead of ending the session.
        if (!world.getWorldBorder().contains(getBoundingBox().offset(velocity))
                || next.y >= world.getTopY() - 2) {
            DroneNetworking.end(owner, this, DroneNetworking.EndReason.WORLD_BOUNDARY);
            releaseControl();
            velocity = new Vec3d(0, Math.min(0, velocity.y), 0);
        } else if (!world.isChunkLoaded(MathHelper.floor(next.x) >> 4, MathHelper.floor(next.z) >> 4)) {
            velocity = Vec3d.ZERO;
        }
        setVelocity(velocity);
        Vec3d oldPos = getPos();
        Box oldBody = getBoundingBox();
        move(MovementType.SELF, velocity);
        velocityDirty = true;
        Vec3d travelled = getPos().subtract(oldPos);
        double impact = DroneFlight.impactSpeed(velocity.x, velocity.y, velocity.z, travelled.x, travelled.y, travelled.z);
        if (isAutoLanding() && isOnGround()) {
            dataTracker.set(AUTO_LANDING, false);
            dataTracker.set(THROTTLE, 0f);
            setVelocity(Vec3d.ZERO);
            idleTicks = 0;
        }
        // Vanilla may clear onGround for a zero-Y assisted move. Test the floor too, so taxiing cannot arm the flight fuse.
        boolean floorSupported = world.getBlockCollisions(this, getBoundingBox().offset(0, -0.02, 0)).iterator().hasNext();
        boolean previouslyAirborne = flightContactReady;
        flightContactReady = DroneCollision.flightFuseAfterUpdate(flightContactReady, isArmed(), isOnGround() || floorSupported, travelled);
        if (isArmed()) for (var pos : net.minecraft.util.math.BlockPos.iterate(
                BlockPosHelper.min(oldBody.stretch(velocity)), BlockPosHelper.max(oldBody.stretch(velocity)))) {
            if (world.getBlockState(pos).isOf(com.bobincraft.warzone.CombatZoneBlocks.ANTI_DRONE_NET) &&
                    DroneCollision.solidContact(oldBody, velocity, true, java.util.List.of(world.getBlockState(pos).getCollisionShape(world, pos).offset(pos.getX(), pos.getY(), pos.getZ())))) {
                detonate("interception_net_contact");
                return;
            }
        }
        boolean physicalContact = isArmed() && getDroneType().isStrikeImpact()
                && DroneCollision.solidContact(oldBody, velocity, previouslyAirborne,
                        world.getBlockCollisions(this, oldBody.stretch(velocity).expand(0.001)));
        if (isArmed() && DroneCollision.powerLineContact(world, oldBody, velocity)) {
            detonate("utility_line_contact");
            return;
        }
        // Exact 3D local-space swept collision against ArmoredVehicleEntity (hull, turret, open hatch, hatch rim, interior, and crew)
        if (isArmed() && getDroneType().isStrikeImpact()) {
            ArmorGeometry.SweptTankHit earliestTankHit = null;
            for (Entity candidate : world.getOtherEntities(this, oldBody.stretch(velocity).expand(1.5),
                    e -> e instanceof ArmoredVehicleEntity && e.isAlive())) {
                var hitOpt = ArmorGeometry.sweepFpvDrone((ArmoredVehicleEntity) candidate, this, owner,
                        oldBody, travelled, previouslyAirborne, flightContactReady);
                if (hitOpt.isPresent()) {
                    var hit = hitOpt.get();
                    if (earliestTankHit == null || hit.t() < earliestTankHit.t()) {
                        earliestTankHit = hit;
                    }
                }
            }
            if (earliestTankHit != null) {
                setPosition(earliestTankHit.contactPos());
                detonateAt(earliestTankHit.interior() ? "tank_interior_contact:" + earliestTankHit.group()
                        : "tank_armor_contact:" + earliestTankHit.group(), earliestTankHit.blastCenter());
                return;
            }
        }
        if (isArmed() && getDroneType().isStrikeImpact() && (physicalContact
                || DroneCollision.blockContact(flightContactReady, velocity, horizontalCollision, verticalCollision))) {
            detonate("block_contact");
            return;
        }
        if (isArmed() && getDroneType().isCarrier() && (horizontalCollision || verticalCollision) && impact > 0.24) {
            crash("block_collision impact=" + impact);
            return;
        }
        // Swept entity collision against non-tank entities (ignoring outer tank AABB and armor-shielded crew).
        if (isArmed() && getDroneType().isStrikeImpact()) {
            boolean hit = world.getOtherEntities(this, getBoundingBox().stretch(oldPos.subtract(getPos())).expand(0.08),
                    entity -> !(entity instanceof ArmoredVehicleEntity)
                            && (flightContactReady || entity != owner) && entity.canHit() && entity.isAlive()).stream()
                    .anyMatch(entity -> DroneCollision.entityContact(oldBody, travelled, entity.getBoundingBox())
                            && !ArmorGeometry.blocksCrewHit(entity, oldBody.getCenter(), entity.getBoundingBox().getCenter()));
            if (hit) { detonate("entity_contact"); return; }
        }
        if (isTouchingWater() || isInLava()) { crash("water_or_lava"); return; }
        if (getY() < world.getBottomY() - 8) { discard(); return; }
        if (!controlled && !isAutoLanding() && ++idleTicks > 1200) discard();
    }

    private static final class BlockPosHelper {
        static net.minecraft.util.math.BlockPos min(Box b){return net.minecraft.util.math.BlockPos.ofFloored(b.minX-.001,b.minY-.001,b.minZ-.001);}
        static net.minecraft.util.math.BlockPos max(Box b){return net.minecraft.util.math.BlockPos.ofFloored(b.maxX+.001,b.maxY+.001,b.maxZ+.001);}
    }

    private void updateSignal(ServerPlayerEntity owner, ServerWorld world) {
        double range = getDroneType().range;
        double obs = DroneSignal.obstruction(world, owner.getEyePos(), getPos());
        if (getDroneType().isFixedWing()) {
            // Long-range fixed-wing directional link resists terrain attenuation
            obs *= 0.35;
        }
        dataTracker.set(SIGNAL, DroneSignal.strength(distanceTo(owner), range, obs));
    }

    private void captureHover() {
        hoverPosition = getPos();
        holdingHorizontal = holdingVertical = false;
        dataTracker.set(HOLDING, false);
    }

    private void updateHoverTarget() {
        if (hoverPosition == null) captureHover();
        boolean horizontal = DroneFlight.neutralHorizontal(input);
        boolean vertical = DroneFlight.neutralVertical(getDroneType(), input, dataTracker.get(LOOK_PITCH));
        if (!horizontal || !holdingHorizontal) hoverPosition = new Vec3d(
                getX() + (horizontal ? DroneFlight.stoppingOffset(getDroneType(), getVelocity().x) : 0), hoverPosition.y,
                getZ() + (horizontal ? DroneFlight.stoppingOffset(getDroneType(), getVelocity().z) : 0));
        if (!vertical || !holdingVertical) hoverPosition = new Vec3d(hoverPosition.x,
                getY() + (vertical ? DroneFlight.stoppingOffset(getDroneType(), getVelocity().y) : 0), hoverPosition.z);
        holdingHorizontal = horizontal;
        holdingVertical = vertical;
        dataTracker.set(HOLDING, horizontal && vertical);
    }

    private void dropPayload() {
        if (!getDroneType().isCarrier()) return;
        if (!isArmed()) { dropFeedback("motors_off", "gui.bobin_craft.drone.status.motors_first"); return; }
        if (dropCooldown > 0) { dropFeedback("cooldown", "drone.bobin_craft.drop_cooldown"); return; }
        if (getPayloadCount() <= 0) { dropFeedback("empty", "gui.bobin_craft.drone.status.no_payload"); return; }
        ServerWorld world = (ServerWorld) getWorld();
        if (HubSafety.isHub(world)) { dropFeedback("safe_world", "drone.bobin_craft.launch_blocked"); return; }
        DronePayloadEntity payload = DroneContent.DRONE_PAYLOAD.create(world);
        if (payload == null) { dropFeedback("creation_failed", "drone.bobin_craft.drop_blocked"); return; }
        DronePayloadProfile profile = DronePayloadProfile.of(getVariant().blast);
        Vec3d release = profile.releasePosition(getBoundingBox(), payload.getHeight(), getYaw(),
                getVariant().payloadCapacity() - getPayloadCount());
        payload.configure(getOwnerUuid(), getUuid(), release, getVelocity(), getVariant().blast);
        payload.setYaw(getYaw());
        if (getBoundingBox().intersects(payload.getBoundingBox()) || !world.isSpaceEmpty(payload)) {
            dropFeedback("bay_obstructed", "drone.bobin_craft.drop_blocked");
            return;
        }
        if (world.spawnEntity(payload)) {
            dataTracker.set(PAYLOADS, getPayloadCount() - 1);
            dropCooldown = 16;
            dropFeedback("released payload=" + payload.getId(), "drone.bobin_craft.drop_released", getPayloadCount());
        } else dropFeedback("spawn_rejected", "drone.bobin_craft.drop_blocked");
    }

    private void dropFeedback(String reason, String key, Object... arguments) {
        LOGGER.info("Drone payload action: drone={}, reason={}, remaining={}", getId(), reason, getPayloadCount());
    }

    private void crash(String reason) {
        LOGGER.info("Drone crash: drone={}, type={}, reason={}, position={}, velocity={}", getId(), getDroneType(), reason, getPos(), getVelocity());
        releaseControl();
        if (getWorld() instanceof ServerWorld world) world.spawnParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 14, 0.25, 0.15, 0.25, 0.04);
        discard();
    }

    private void detonate(String reason) {
        detonateAt(reason, getPos().add(0, 0.10, 0));
    }

    private void detonateAt(String reason, Vec3d center) {
        if (detonating || !(getWorld() instanceof ServerWorld world)) return;
        detonating = true;
        boolean armed = isArmed();
        LOGGER.info("Drone detonation: drone={}, reason={}, armed={}, controlled={}, position={}, center={}, velocity={}",
                getId(), reason, armed, controlled, getPos(), center, getVelocity());
        releaseControl();
        if (armed && !HubSafety.isHub(world)) {
            DroneExplosion.explodeAt(world, this, owner(), getVariant().blast, center);
        }
        discard();
    }

    @Override public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || isInvulnerableTo(source) || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.getSource() instanceof DronePayloadEntity payload && payload.protectsCarrier(getUuid())) return false;
        health -= amount;
        if (health <= 0) {
            if (isArmed() && getDroneType().isStrikeImpact()) detonate("damage:" + source.getName()); else crash("damage:" + source.getName());
        }
        return true;
    }

    @Override public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!player.getUuid().equals(getOwnerUuid())) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (!isArmed() && isOnGround() && getVelocity().lengthSquared() < 0.02) {
            ItemStack recovered = new ItemStack(DroneContent.itemFor(getVariant()));
            recovered.getOrCreateNbt().putFloat("DroneBattery", getBatteryFraction());
            recovered.getOrCreateNbt().putInt("DronePayloads", getPayloadCount());
            if (!player.getAbilities().creativeMode) player.getInventory().offerOrDrop(recovered);
            player.sendMessage(Text.translatable("drone.bobin_craft.recovered"), true);
            discard();
        } else DroneNetworking.begin((ServerPlayerEntity) player, this);
        return ActionResult.SUCCESS;
    }

    @Override public void remove(RemovalReason reason) {
        if (!getWorld().isClient) DroneNetworking.removed(this);
        super.remove(reason);
    }

    @Override public boolean canHit() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override protected float getEyeHeight(net.minecraft.entity.EntityPose pose, net.minecraft.entity.EntityDimensions dimensions) {
        return getDroneType() == DroneType.FIXED_WING ? 0.32f : 0.12f;
    }

    @Override public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int ticks, boolean interpolate) {
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

    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (getOwnerUuid() != null) nbt.putUuid("Owner", getOwnerUuid());
        nbt.putFloat("Battery", batteryTicks); nbt.putInt("Payloads", getPayloadCount());
        nbt.putFloat("Health", health);
        nbt.putString("Variant", getVariant().id);
        nbt.putBoolean("Linked", isLinked());
        nbt.putBoolean("AutoLanding", isAutoLanding());
        nbt.putInt("LandingTicks", landingTicks);
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        setVariant(DroneVariant.saved(nbt.getString("Variant"), getDroneType()));
        if (nbt.containsUuid("Owner")) dataTracker.set(OWNER, Optional.of(nbt.getUuid("Owner")));
        float savedBattery = nbt.getFloat("Battery");
        batteryTicks = Float.isFinite(savedBattery) ? MathHelper.clamp(savedBattery, 0, getDroneType().batteryTicks) : 0;
        dataTracker.set(BATTERY, batteryTicks / getDroneType().batteryTicks);
        dataTracker.set(PAYLOADS, MathHelper.clamp(nbt.getInt("Payloads"), 0, getVariant().payloadCapacity()));
        float savedHealth = nbt.getFloat("Health");
        health = Float.isFinite(savedHealth) ? MathHelper.clamp(savedHealth, 1, maxHealthFor(getDroneType())) : 1;
        dataTracker.set(LINKED, !nbt.contains("Linked") || nbt.getBoolean("Linked"));
        dataTracker.set(AUTO_LANDING, !isLinked() && nbt.getBoolean("AutoLanding"));
        landingTicks = MathHelper.clamp(nbt.getInt("LandingTicks"), 0, 6000);
        captureHover();
        releaseControl();
    }
}
