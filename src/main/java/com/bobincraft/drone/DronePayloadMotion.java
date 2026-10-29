package com.bobincraft.drone;

import com.bobincraft.vehicle.*;
import net.minecraft.entity.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import java.util.*;

/** Native voxel solver with explicit small-charge obstacles. Crew bodies cannot seal a hatch. */
public final class DronePayloadMotion {
    private DronePayloadMotion() { }
    public record Contact(Vec3d actual, boolean x, boolean y, boolean z, boolean supported) { }

    public static Contact move(DronePayloadEntity charge, Vec3d requested) {
        var world = charge.getWorld();
        Box box = charge.getBoundingBox();
        Box stretched = box.stretch(requested).expand(1e-6);
        List<VoxelShape> entities = new ArrayList<>();
        List<ArmoredVehicleEntity> nearbyTanks = new ArrayList<>();
        for (var other : world.getOtherEntities(charge, stretched.expand(1.0), e -> !e.isSpectator() && e.isAlive())) {
            if (other instanceof ArmoredVehicleEntity tank) {
                nearbyTanks.add(tank);
                for (var solid : ArmorGeometry.payloadWorldBoxes(tank)) {
                    if (solid.intersects(stretched)) entities.add(VoxelShapes.cuboid(solid));
                }
            } else if (!(other instanceof LivingEntity) && charge.collidesWith(other)) {
                entities.add(VoxelShapes.cuboid(other.getBoundingBox()));
            }
        }
        Vec3d startPos = charge.getPos();
        Vec3d actual = Entity.adjustMovementForCollisions(charge, requested, box, world, entities);
        Vec3d nextPos = startPos.add(actual);
        boolean x = Math.abs(actual.x - requested.x) > 1e-7;
        boolean y = Math.abs(actual.y - requested.y) > 1e-7;
        boolean z = Math.abs(actual.z - requested.z) > 1e-7;

        // Check open hatch coaming rim in exact turret-local space so diagonal turret rotations never obstruct the center of the open hatch
        for (ArmoredVehicleEntity tank : nearbyTanks) {
            if (!tank.hatchOpen() || tank.armorHealth() <= 0) continue;
            Vec3d localStart = ArmorGeometry.toLocal(tank, "turret", startPos);
            Vec3d localEnd = ArmorGeometry.toLocal(tank, "turret", nextPos);
            if (localStart.y >= 1.22 && localEnd.y < 1.30 && localEnd.y >= 1.05) {
                double lx = localEnd.x, lz = localEnd.z;
                boolean onLeftOrRightRim = (lx >= 0.02 && lx <= 0.14 || lx >= 1.22 && lx <= 1.34) && lz >= -0.46 && lz <= 0.78;
                boolean onFrontOrBackRim = (lz >= -0.48 && lz <= -0.38 || lz >= 0.70 && lz <= 0.80) && lx >= 0.02 && lx <= 1.34;
                if (onLeftOrRightRim || onFrontOrBackRim) {
                    nextPos = ArmorGeometry.toWorld(tank, "turret", new Vec3d(lx, 1.30, lz));
                    actual = nextPos.subtract(startPos);
                    y = true;
                }
            }
        }

        charge.setPosition(nextPos);
        charge.horizontalCollision = x || z;
        charge.verticalCollision = y;
        charge.setOnGround(y && requested.y < 0);
        return new Contact(actual, x, y, z, charge.isOnGround());
    }
}
