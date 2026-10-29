package com.bobincraft.warzone;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * High-altitude Exclusion-Zone crow/raven that circles on thermals or glides across the sky.
 * Simulates smooth orbital/glide motion on both server and client ticks so rendering is 100% smooth
 * at any framerate (60/144/240 FPS) with zero network packet stutter.
 */
public final class SkyBirdEntity extends Entity {
    private static final TrackedData<Boolean> CIRCLING = DataTracker.registerData(
            SkyBirdEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> FLAP_OFFSET = DataTracker.registerData(
            SkyBirdEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private double centerX;
    private double centerY;
    private double centerZ;
    private float orbitRadius = 22.0f;
    private float orbitAngle;
    private float angularVelocity = 0.024f;
    private double glideVx = 0.24;
    private double glideVz = 0.14;
    private boolean initialized;

    private int clientLerpSteps;
    private double clientX;
    private double clientY;
    private double clientZ;
    private float clientYaw;

    public SkyBirdEntity(EntityType<? extends SkyBirdEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    public void configureFlight(double cx, double cy, double cz, boolean circling,
                                float radius, float startAngle, float flapOffset) {
        this.centerX = cx;
        this.centerY = cy;
        this.centerZ = cz;
        this.orbitRadius = MathHelper.clamp(radius, 14.0f, 38.0f);
        this.orbitAngle = startAngle;
        this.angularVelocity = 0.019f + (Math.abs(flapOffset) % 10.0f) * 0.0011f;
        this.glideVx = Math.cos(startAngle) * 0.34;
        this.glideVz = Math.sin(startAngle) * 0.34;
        this.initialized = true;
        this.dataTracker.set(CIRCLING, circling);
        this.dataTracker.set(FLAP_OFFSET, flapOffset);

        double tangentX = circling ? -Math.sin(startAngle) : glideVx;
        double tangentZ = circling ? Math.cos(startAngle) : glideVz;
        float initialYaw = (float) (MathHelper.atan2(tangentZ, tangentX) * (180.0 / Math.PI)) - 90.0f;
        this.setYaw(initialYaw);
        this.prevYaw = initialYaw;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(CIRCLING, true);
        this.dataTracker.startTracking(FLAP_OFFSET, 0.0f);
    }

    public boolean isCircling() {
        return this.dataTracker.get(CIRCLING);
    }

    public float flapOffset() {
        return this.dataTracker.get(FLAP_OFFSET);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch,
                                               int interpolationSteps, boolean interpolate) {
        this.clientX = x;
        this.clientY = y;
        this.clientZ = z;
        this.clientYaw = yaw;
        this.clientLerpSteps = Math.max(2, interpolationSteps);
    }

    @Override
    public void tick() {
        super.tick();
        if (!initialized) {
            centerX = getX();
            centerY = getY();
            centerZ = getZ();
            initialized = true;
        }

        if (getWorld().isClient) {
            if (this.clientLerpSteps > 0) {
                double nx = getX() + (this.clientX - getX()) / this.clientLerpSteps;
                double ny = getY() + (this.clientY - getY()) / this.clientLerpSteps;
                double nz = getZ() + (this.clientZ - getZ()) / this.clientLerpSteps;
                double dx = nx - getX();
                double dz = nz - getZ();
                float targetYaw = (dx * dx + dz * dz > 1.0e-5)
                        ? (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f
                        : this.clientYaw;
                float nyaw = getYaw() + MathHelper.wrapDegrees(targetYaw - getYaw()) * 0.45f;
                this.clientLerpSteps--;
                setPosition(nx, ny, nz);
                setYaw(nyaw);
            } else {
                // Smooth dead-reckoning along current heading if between network packets
                double rad = Math.toRadians(getYaw() + 90.0f);
                double speed = 0.34;
                setPosition(getX() + Math.cos(rad) * speed, getY(), getZ() + Math.sin(rad) * speed);
            }
            return;
        }

        if (this.age > 1400 || getWorld().getClosestPlayer(this, 136.0) == null) {
            discard();
            return;
        }

        double prevX = getX();
        double prevZ = getZ();
        double nextX;
        double nextY;
        double nextZ;

        if (isCircling()) {
            orbitAngle += angularVelocity;
            nextX = centerX + Math.cos(orbitAngle) * orbitRadius;
            nextZ = centerZ + Math.sin(orbitAngle) * orbitRadius;
            nextY = centerY + MathHelper.sin((this.age + flapOffset()) * 0.04f) * 1.6;
        } else {
            double sway = MathHelper.sin((this.age + flapOffset()) * 0.035f) * 0.06;
            nextX = prevX + glideVx - glideVz * sway;
            nextZ = prevZ + glideVz + glideVx * sway;
            nextY = centerY + MathHelper.sin((this.age + flapOffset()) * 0.05f) * 1.1;
        }

        double dx = nextX - prevX;
        double dz = nextZ - prevZ;
        if (dx * dx + dz * dz > 1.0e-5) {
            this.prevYaw = this.getYaw();
            this.setYaw((float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f);
        }
        setPosition(nextX, nextY, nextZ);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 148.0 * 148.0;
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
        this.centerX = nbt.getDouble("CenterX");
        this.centerY = nbt.getDouble("CenterY");
        this.centerZ = nbt.getDouble("CenterZ");
        this.orbitRadius = nbt.getFloat("OrbitRadius");
        this.orbitAngle = nbt.getFloat("OrbitAngle");
        this.initialized = nbt.getBoolean("Initialized");
        this.dataTracker.set(CIRCLING, nbt.getBoolean("Circling"));
        this.dataTracker.set(FLAP_OFFSET, nbt.getFloat("FlapOffset"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putDouble("CenterX", this.centerX);
        nbt.putDouble("CenterY", this.centerY);
        nbt.putDouble("CenterZ", this.centerZ);
        nbt.putFloat("OrbitRadius", this.orbitRadius);
        nbt.putFloat("OrbitAngle", this.orbitAngle);
        nbt.putBoolean("Initialized", this.initialized);
        nbt.putBoolean("Circling", isCircling());
        nbt.putFloat("FlapOffset", flapOffset());
    }
}
