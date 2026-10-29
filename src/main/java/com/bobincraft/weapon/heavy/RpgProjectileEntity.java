package com.bobincraft.weapon.heavy;

import com.bobincraft.destruction.StructureDamageManager;
import com.bobincraft.destruction.StructureDamageSource;
import com.bobincraft.drone.DroneBlastType;
import com.bobincraft.drone.DroneExplosion;
import com.bobincraft.raid.HubSafety;
import com.bobincraft.vehicle.ArmorContent;
import com.bobincraft.vehicle.ArmorGeometry;
import com.bobincraft.vehicle.ArmoredVehicleEntity;
import com.bobincraft.weapon.ShotServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Short-lived RPG rocket entity using swept raycast collision, ArmorGeometry vehicle checks,
 * DroneExplosion / ArmorShockwave blast effects, ShotServer character injury integration,
 * and StructureDamageManager structural hit dispatch (Section 11).
 */
public final class RpgProjectileEntity extends Entity {
    private static final TrackedData<Vector3f> MOTION = DataTracker.registerData(RpgProjectileEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private UUID shooterUuid;
    private boolean live;
    private boolean detonated;
    private Vec3d impactNormal;

    public RpgProjectileEntity(EntityType<? extends RpgProjectileEntity> type, World world) {
        super(type, world);
    }

    public void launch(LivingEntity shooter, Vec3d position, Vec3d velocity) {
        this.shooterUuid = shooter != null ? shooter.getUuid() : null;
        this.live = true;
        this.detonated = false;
        setPosition(position);
        setVelocity(velocity);
        syncMotion(velocity);
        orient(velocity);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(MOTION, new Vector3f());
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            Vec3d v = getVelocity().add(0.0, -RpgContent.projectileGravity, 0.0);
            setPosition(getPos().add(v));
            setVelocity(v);
            orient(v);
            // Short client-side exhaust trail (1 particle per tick, zero extra Entities)
            getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(),
                    -v.x * 0.08, -v.y * 0.08, -v.z * 0.08);
            return;
        }

        if (!live || detonated || age > RpgContent.maxFlightTicks || ArmorContent.inHub(getWorld())) {
            discard();
            return;
        }

        ServerWorld world = (ServerWorld) getWorld();
        Vec3d before = getPos();
        Vec3d velocity = getVelocity().add(0.0, -RpgContent.projectileGravity, 0.0);
        Vec3d after = before.add(velocity);
        Box sweep = getBoundingBox().stretch(velocity);

        for (int cx = MathHelper.floor(sweep.minX) >> 4; cx <= MathHelper.floor(sweep.maxX) >> 4; cx++) {
            for (int cz = MathHelper.floor(sweep.minZ) >> 4; cz <= MathHelper.floor(sweep.maxZ) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    discard();
                    return;
                }
            }
        }
        if (!world.getWorldBorder().contains(sweep) || after.y < world.getBottomY()) {
            discard();
            return;
        }

        var blockHit = world.raycast(new RaycastContext(
                before, after, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
        double closestDistSq = blockHit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY
                : before.squaredDistanceTo(blockHit.getPos());
        Vec3d impactPos = blockHit.getPos();
        Entity struckEntity = null;

        for (Entity candidate : world.getOtherEntities(this, sweep.expand(0.25),
                e -> e.canHit() && e.isAlive() && !e.getUuid().equals(shooterUuid))) {
            if (candidate instanceof ArmoredVehicleEntity tank) {
                var tankHit = ArmorGeometry.raycast(tank, before, after);
                if (tankHit.isPresent()) {
                    double d2 = before.squaredDistanceTo(tankHit.get().point());
                    if (d2 < closestDistSq) {
                        closestDistSq = d2;
                        impactPos = tankHit.get().point();
                        struckEntity = tank;
                    }
                    continue;
                }
            }
            var boxHit = candidate.getBoundingBox().expand(0.15).raycast(before, after);
            if (boxHit.isPresent()) {
                double d2 = before.squaredDistanceTo(boxHit.get());
                if (d2 < closestDistSq) {
                    closestDistSq = d2;
                    impactPos = boxHit.get();
                    struckEntity = candidate;
                }
            }
        }

        if (Double.isFinite(closestDistSq)) {
            impactNormal=struckEntity==null&&blockHit.getType()==HitResult.Type.BLOCK?Vec3d.of(blockHit.getSide().getVector()):null;
            detonateAt(world, impactPos, velocity, struckEntity);
            return;
        }

        setPosition(after);
        setVelocity(velocity);
        syncMotion(velocity);
        orient(velocity);
        velocityDirty = true;
    }

    public void detonateAt(ServerWorld world, Vec3d impactPos, Vec3d velocity, Entity struckEntity) {
        if (detonated || HubSafety.isHub(world)) {
            discard();
            return;
        }
        detonated = true;
        setPosition(impactPos);
        Entity owner = shooterUuid == null ? null : world.getEntity(shooterUuid);
        ServerPlayerEntity ownerPlayer = owner instanceof ServerPlayerEntity sp ? sp : null;

        if (struckEntity != null) {
            struckEntity.damage(world.getDamageSources().explosion(this, owner), RpgContent.directEntityDamage);
            if (struckEntity instanceof LivingEntity living) {
                ShotServer.recordRegionalInjury(living, impactPos, RpgContent.directEntityDamage);
            }
        }

        Vec3d normVel = velocity.lengthSquared() > 1e-6 ? velocity.normalize() : new Vec3d(0, -1, 0);
        Vec3d blastCenter = impactPos.subtract(normVel.multiply(0.12));

        // 1. Dispatch structural damage event to StructureDamageManager (Section 4.2 & 11)
        StructureDamageSource structSource = new StructureDamageSource(
                getUuid(),
                StructureDamageSource.Kind.RPG_PROJECTILE,
                shooterUuid,
                impactPos,
                RpgContent.blastEffectRadius
        );
        structSource.withSurfaceNormal(impactNormal);
        StructureDamageManager.applyDamage(world, impactPos, structSource, RpgContent.structuralGameDamage);

        // 2. Trigger existing DroneExplosion pipeline (which handles pressure, shrapnel, terrain, and visuals)
        DroneExplosion.explodeAt(world, this, ownerPlayer, DroneBlastType.KAMIKAZE_BREACH, blastCenter);
        discard();
    }

    private void syncMotion(Vec3d v) {
        dataTracker.set(MOTION, new Vector3f((float) v.x, (float) v.y, (float) v.z));
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data == MOTION && getWorld().isClient) {
            Vector3f v = dataTracker.get(MOTION);
            setVelocity(v.x, v.y, v.z);
        }
    }

    @Override
    public void setVelocityClient(double x, double y, double z) {
        Vector3f v = dataTracker.get(MOTION);
        if (v.lengthSquared() > 0) super.setVelocityClient(v.x, v.y, v.z);
        else super.setVelocityClient(x, y, z);
    }

    private void orient(Vec3d v) {
        if (v.lengthSquared() < 1e-6) return;
        setYaw((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        setPitch((float) -Math.toDegrees(Math.atan2(v.y, v.horizontalLength())));
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int ticks, boolean interpolate) {
        setPosition(x, y, z);
        setYaw(yaw);
        setPitch(pitch);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (shooterUuid != null) nbt.putUuid("Shooter", shooterUuid);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Shooter")) shooterUuid = nbt.getUuid("Shooter");
        live = false;
    }
}
