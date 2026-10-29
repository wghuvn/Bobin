package com.bobincraft.drone.client;

import com.bobincraft.drone.DroneType;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The bomber retains its clear view; FPV and FP-1 fixed-wing optics follow the nose lens and frame. */
public final class DroneCameraGeometry {
    private DroneCameraGeometry() { }

    /** Game vertical FOVs: wide FPV optics, medium-wide fixed-wing nose optics, and narrower stabilized bomber camera. */
    public static float fieldOfView(DroneType type) {
        return switch (type) {
            case FPV -> 110;
            case FIXED_WING -> 84;
            case HEAVY -> 65;
            default -> 70;
        };
    }

    public static Vec3d mount(DroneType type, Vec3d feet, float yaw, float pitch, float bank, boolean downward) {
        if (downward) return feet.add(0, 0.06, 0);
        float scale = type.renderScale;
        if (type.isCarrier()) {
            double radians = Math.toRadians(yaw);
            return feet.add(-Math.sin(radians) * .42 * scale, .30 * scale, Math.cos(radians) * .42 * scale);
        }
        if (type == DroneType.FIXED_WING) {
            // Mounted just ahead of the FP-1 nose optical dome at z = -11.75 model units
            Vector3f offset = new Vector3f(0, -0.55f * scale / 16, 12.1f * scale / 16 + .005f);
            offset.rotate(new Quaternionf().rotationY((float) Math.toRadians(-yaw))
                    .rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.toRadians(-bank)));
            return feet.add(offset.x, type.height * .5 + offset.y, offset.z);
        }
        // Centre of the cyan lens's front face in DroneRenderer.modelData, in model pixels.
        // The 1 mm clearance avoids looking through the lens surface itself.
        Vector3f offset = new Vector3f(0, -.20f * scale / 16, 7.95f * scale / 16 + .001f);
        offset.rotate(new Quaternionf().rotationY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.toRadians(-bank)));
        return feet.add(offset.x, type.height * .5 + offset.y, offset.z);
    }

    /** The contact probe is only 3 cm ahead of the FPV lens, inside Minecraft's usual 5 cm clip. */
    public static float nearPlane(DroneType type, float vanilla) {
        return type.isStrikeImpact() ? .01f : vanilla;
    }
}
