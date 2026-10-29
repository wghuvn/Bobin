package com.bobincraft.drone;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/** Continuous swept physical-box contact, with no broad-phase proximity trigger. */
public final class DroneCollision {
    private static final double EPSILON = 1e-7;
    private static final double CONTACT_EPSILON = 0.0001;
    private DroneCollision() { }

    public static boolean sweptContact(Box body, Vec3d movement, Box target) {
        if (body.intersects(target)) return true;
        if (movement.lengthSquared() < EPSILON * EPSILON) return false;
        double[] start = {(body.minX + body.maxX) * 0.5, (body.minY + body.maxY) * 0.5, (body.minZ + body.maxZ) * 0.5};
        double[] velocity = {movement.x, movement.y, movement.z};
        double[] half = {(body.maxX - body.minX) * 0.5, (body.maxY - body.minY) * 0.5, (body.maxZ - body.minZ) * 0.5};
        double[] min = {target.minX - half[0], target.minY - half[1], target.minZ - half[2]};
        double[] max = {target.maxX + half[0], target.maxY + half[1], target.maxZ + half[2]};
        double entry = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(velocity[axis]) < EPSILON) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return false;
                continue;
            }
            double a = (min[axis] - start[axis]) / velocity[axis];
            double b = (max[axis] - start[axis]) / velocity[axis];
            entry = Math.max(entry, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
            if (entry > exit + EPSILON) return false;
        }
        return entry >= -EPSILON && entry <= 1 + EPSILON && exit > EPSILON;
    }

    public static boolean blockContact(boolean flightFuseReady, Vec3d requested, boolean horizontalCollision, boolean verticalCollision) {
        // Entity.move intentionally skips tiny position updates even in empty air. Its collision flags compare
        // requested motion with collision-clipped motion, so only those flags prove contact with a solid shape.
        // The launch floor is ignored while resting; an intentional ceiling/wall contact is always physical contact.
        return horizontalCollision || verticalCollision && (flightFuseReady || requested.y > EPSILON);
    }

    /** Real collision shapes catch flush/tiny-speed contacts even when Entity.move clips movement to zero. */
    public static boolean solidContact(Box body, Vec3d requested, boolean previouslyAirborne, Iterable<VoxelShape> shapes) {
        for (VoxelShape shape : shapes) for (Box obstacle : shape.getBoundingBoxes()) {
            // Ignore only the starting launch floor. A side wall/ceiling is never launch support.
            if (!previouslyAirborne && obstacle.maxY <= body.minY + CONTACT_EPSILON) continue;
            if (sweptContact(body, requested, obstacle.expand(CONTACT_EPSILON))) return true;
        }
        return false;
    }

    public static boolean entityContact(Box body, Vec3d movement, Box target) {
        return sweptContact(body, movement, target.expand(CONTACT_EPSILON));
    }

    /** Exact separating axes for a moving box and a cable segment (a 3-D zonotope). */
    public static boolean wireContact(Box body, Vec3d movement, Vec3d a, Vec3d b) {
        Vec3d center=body.getCenter().add(movement.multiply(.5)).subtract(a.add(b).multiply(.5));
        Vec3d[] generators={new Vec3d((body.maxX-body.minX)*.5+.026,0,0),
                new Vec3d(0,(body.maxY-body.minY)*.5+.026,0),new Vec3d(0,0,(body.maxZ-body.minZ)*.5+.026),
                movement.multiply(.5),b.subtract(a).multiply(.5)};
        for(int i=0;i<generators.length;i++)for(int j=i+1;j<generators.length;j++){
            Vec3d axis=generators[i].crossProduct(generators[j]);if(axis.lengthSquared()<1e-16)continue;
            double radius=0;for(Vec3d generator:generators)radius+=Math.abs(generator.dotProduct(axis));
            if(Math.abs(center.dotProduct(axis))>radius+1e-9)return false;
        }
        return true;
    }

    /** Only nearby, actually loaded authored conductors participate; invisible anchor cubes never collide. */
    public static boolean powerLineContact(net.minecraft.server.world.ServerWorld world,Box body,Vec3d movement) {
        var generator=com.bobincraft.warzone.WarzoneDimension.generatorOf(world);
        if(generator==null||!generator.terrain().isAuthoredZone())return false;
        var grid=generator.terrain().zonePlan().power();Box sweep=body.stretch(movement).expand(.55);
        for(int x=net.minecraft.util.math.MathHelper.floor(sweep.minX)>>4;x<=net.minecraft.util.math.MathHelper.floor(sweep.maxX)>>4;x++)
            for(int z=net.minecraft.util.math.MathHelper.floor(sweep.minZ)>>4;z<=net.minecraft.util.math.MathHelper.floor(sweep.maxZ)>>4;z++){
                if(!world.isChunkLoaded(x,z))continue;
                for(var segment:grid.cablesInChunk(x,z)){
                    Vec3d a=new Vec3d(segment.a().x(),segment.a().y(),segment.a().z()),b=new Vec3d(segment.b().x(),segment.b().y(),segment.b().z());
                    if(wireContact(body,movement,a,b))return true;
                }
            }
        return false;
    }

    public static boolean hasTakenOff(boolean onGround, Vec3d movement) {
        return !onGround && (movement.y > EPSILON || movement.x * movement.x + movement.z * movement.z > EPSILON * EPSILON);
    }

    public static boolean flightFuseAfterUpdate(boolean ready, boolean armed, boolean onGround, Vec3d movement) {
        return armed && (ready || hasTakenOff(onGround, movement));
    }
}
