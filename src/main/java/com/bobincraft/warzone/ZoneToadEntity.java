package com.bobincraft.warzone;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Small amphibious Exclusion-Zone toad that sits on damp riverbanks and pond edges.
 * When a player gets within 6.5 blocks, it turns toward the nearest water block, leaps head-first
 * in an arc into the water, splashes, and vanishes beneath the murky surface.
 */
public final class ZoneToadEntity extends Entity {
    private static final TrackedData<Boolean> LEAPING = DataTracker.registerData(
            ZoneToadEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(
            ZoneToadEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private double targetWaterX;
    private double targetWaterZ;
    private boolean hasWaterTarget;
    private int fleeTicks;
    private int idleHopCooldown = 50;

    private int clientLerpSteps;
    private double clientX;
    private double clientY;
    private double clientZ;
    private float clientYaw;

    public ZoneToadEntity(EntityType<? extends ZoneToadEntity> type, World world) {
        super(type, world);
        this.noClip = false;
    }

    public void configureBankSpawn(double waterX, double waterZ, int variant) {
        this.targetWaterX = waterX;
        this.targetWaterZ = waterZ;
        this.hasWaterTarget = true;
        this.dataTracker.set(VARIANT, MathHelper.clamp(variant, 0, 2));
        double dx = waterX - getX();
        double dz = waterZ - getZ();
        float faceYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        this.setYaw(faceYaw);
        this.prevYaw = faceYaw;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LEAPING, false);
        this.dataTracker.startTracking(VARIANT, 0);
    }

    public boolean isLeaping() {
        return this.dataTracker.get(LEAPING);
    }

    public int variant() {
        return this.dataTracker.get(VARIANT);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                               int interpolationSteps, boolean interpolate) {
        this.clientX = x;
        this.clientY = y;
        this.clientZ = z;
        this.clientYaw = yaw;
        this.clientLerpSteps = Math.max(1, interpolationSteps);
    }

    @Override
    public void tick() {
        super.tick();

        if (getWorld().isClient) {
            if (this.clientLerpSteps > 0) {
                double nx = getX() + (this.clientX - getX()) / this.clientLerpSteps;
                double ny = getY() + (this.clientY - getY()) / this.clientLerpSteps;
                double nz = getZ() + (this.clientZ - getZ()) / this.clientLerpSteps;
                float nyaw = getYaw() + MathHelper.wrapDegrees(this.clientYaw - getYaw()) / this.clientLerpSteps;
                this.clientLerpSteps--;
                setPosition(nx, ny, nz);
                setYaw(nyaw);
            }
            if (isLeaping() && this.age % 2 == 0) {
                getWorld().addParticle(ParticleTypes.SPLASH,
                        getX(), getY() + 0.1, getZ(), 0.0, 0.02, 0.0);
            }
            return;
        }

        if (this.age > 1200 || getWorld().getClosestPlayer(this, 68.0) == null) {
            discard();
            return;
        }

        BlockPos pos = getBlockPos();
        boolean inWater = getWorld().getBlockState(pos).isOf(Blocks.WATER)
                || getWorld().getBlockState(pos.down()).isOf(Blocks.WATER);

        if (!isLeaping()) {
            PlayerEntity nearby = getWorld().getClosestPlayer(this, 6.8);
            if (nearby != null && !nearby.isSpectator()) {
                triggerWaterEscape(nearby);
            } else if (--idleHopCooldown <= 0 && isOnGround()) {
                idleHopCooldown = 65 + getWorld().random.nextInt(70);
                float angle = getYaw() + (getWorld().random.nextFloat() - 0.5f) * 35.0f;
                this.prevYaw = getYaw();
                setYaw(angle);
                double rad = Math.toRadians(angle + 90.0f);
                setVelocity(Math.cos(rad) * 0.09, 0.18, Math.sin(rad) * 0.09);
            }
        } else {
            fleeTicks++;
            Vec3d v = getVelocity();
            if (v.x * v.x + v.z * v.z > 1.0e-4) {
                float moveYaw = (float) (MathHelper.atan2(v.z, v.x) * (180.0 / Math.PI)) - 90.0f;
                this.prevYaw = getYaw();
                this.setYaw(moveYaw);
            }
            if (inWater || fleeTicks > 26 || (fleeTicks > 8 && isOnGround())) {
                if (getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.spawnParticles(ParticleTypes.SPLASH,
                            getX(), getY() + 0.15, getZ(), 10, 0.18, 0.08, 0.18, 0.08);
                    serverWorld.spawnParticles(ParticleTypes.BUBBLE_POP,
                            getX(), getY() + 0.10, getZ(), 6, 0.15, 0.04, 0.15, 0.03);
                    serverWorld.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.ENTITY_FROG_TONGUE, SoundCategory.AMBIENT, 0.35f, 1.45f);
                    serverWorld.playSound(null, getX(), getY(), getZ(),
                            SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.AMBIENT, 0.28f, 1.65f);
                }
                discard();
                return;
            }
        }

        Vec3d vel = getVelocity();
        if (!isOnGround()) {
            vel = vel.add(0.0, -0.055, 0.0);
        } else if (!isLeaping()) {
            vel = new Vec3d(vel.x * 0.55, vel.y, vel.z * 0.55);
        }
        setVelocity(vel);
        move(MovementType.SELF, getVelocity());
    }

    private void triggerWaterEscape(PlayerEntity player) {
        this.dataTracker.set(LEAPING, true);
        this.fleeTicks = 0;

        double dirX;
        double dirZ;
        if (hasWaterTarget) {
            dirX = targetWaterX - getX();
            dirZ = targetWaterZ - getZ();
        } else {
            dirX = getX() - player.getX();
            dirZ = getZ() - player.getZ();
        }
        double len = Math.hypot(dirX, dirZ);
        if (len < 1.0e-3) {
            dirX = 1.0;
            dirZ = 0.0;
            len = 1.0;
        }
        dirX /= len;
        dirZ /= len;

        float jumpYaw = (float) (MathHelper.atan2(dirZ, dirX) * (180.0 / Math.PI)) - 90.0f;
        this.prevYaw = jumpYaw;
        this.setYaw(jumpYaw);
        this.setVelocity(dirX * 0.36, 0.34, dirZ * 0.36);
        this.velocityDirty = true;
        if (getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENTITY_FROG_LONG_JUMP, SoundCategory.AMBIENT, 0.45f, 1.35f);
        }
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.targetWaterX = nbt.getDouble("WaterX");
        this.targetWaterZ = nbt.getDouble("WaterZ");
        this.hasWaterTarget = nbt.getBoolean("HasWater");
        this.dataTracker.set(VARIANT, nbt.getInt("Variant"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putDouble("WaterX", this.targetWaterX);
        nbt.putDouble("WaterZ", this.targetWaterZ);
        nbt.putBoolean("HasWater", this.hasWaterTarget);
        nbt.putInt("Variant", variant());
    }
}
