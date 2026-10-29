package com.bobincraft.warzone;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Non-intrusive cloud of midges/flies hovering over marsh pools, damp reeds, and muddy tracks.
 * Gently scatters when a player walks directly through it.
 */
public final class FlySwarmEntity extends Entity {
    private static final TrackedData<Integer> SWARM_SEED = DataTracker.registerData(
            FlySwarmEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> DISPERSE = DataTracker.registerData(
            FlySwarmEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private boolean anchored;

    public FlySwarmEntity(EntityType<? extends FlySwarmEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public void configureAnchor(double x, double y, double z, int seed) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.anchored = true;
        this.dataTracker.set(SWARM_SEED, seed);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(SWARM_SEED, 1337);
        this.dataTracker.startTracking(DISPERSE, 0.0f);
    }

    public int swarmSeed() {
        return this.dataTracker.get(SWARM_SEED);
    }

    public float disperse() {
        return this.dataTracker.get(DISPERSE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!anchored) {
            anchorX = getX();
            anchorY = getY();
            anchorZ = getZ();
            anchored = true;
        }

        float t = (this.age + (swarmSeed() & 255)) * 0.045f;
        double nx = anchorX + MathHelper.sin(t) * 0.35;
        double ny = anchorY + MathHelper.sin(t * 1.6f) * 0.14;
        double nz = anchorZ + MathHelper.cos(t * 0.85f) * 0.35;
        setPosition(nx, ny, nz);

        if (getWorld().isClient) {
            if (this.age % 5 == 0 && disperse() < 0.7f) {
                double ox = (getWorld().random.nextDouble() - 0.5) * 0.9;
                double oy = (getWorld().random.nextDouble() - 0.2) * 0.6;
                double oz = (getWorld().random.nextDouble() - 0.5) * 0.9;
                getWorld().addParticle(ParticleTypes.ASH,
                        getX() + ox, getY() + 0.35 + oy, getZ() + oz, 0.0, 0.005, 0.0);
            }
            return;
        }

        if (this.age > 1100 || getWorld().getClosestPlayer(this, 68.0) == null) {
            discard();
            return;
        }

        PlayerEntity close = getWorld().getClosestPlayer(this, 2.35);
        float currentDisperse = disperse();
        if (close != null && !close.isSpectator()) {
            currentDisperse = Math.min(1.0f, currentDisperse + 0.09f);
            this.dataTracker.set(DISPERSE, currentDisperse);
            if (currentDisperse >= 0.98f) {
                discard();
                return;
            }
        } else if (currentDisperse > 0.0f) {
            this.dataTracker.set(DISPERSE, Math.max(0.0f, currentDisperse - 0.03f));
        }

        if (this.age % 55 == 0 && getWorld().getClosestPlayer(this, 7.5) != null) {
            getWorld().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENTITY_BEE_LOOP, SoundCategory.AMBIENT, 0.06f, 1.85f);
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
        this.anchorX = nbt.getDouble("AnchorX");
        this.anchorY = nbt.getDouble("AnchorY");
        this.anchorZ = nbt.getDouble("AnchorZ");
        this.anchored = nbt.getBoolean("Anchored");
        this.dataTracker.set(SWARM_SEED, nbt.getInt("SwarmSeed"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putDouble("AnchorX", this.anchorX);
        nbt.putDouble("AnchorY", this.anchorY);
        nbt.putDouble("AnchorZ", this.anchorZ);
        nbt.putBoolean("Anchored", this.anchored);
        nbt.putInt("SwarmSeed", swarmSeed());
    }
}
