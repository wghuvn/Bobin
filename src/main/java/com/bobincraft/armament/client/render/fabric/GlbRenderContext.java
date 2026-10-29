package com.bobincraft.armament.client.render.fabric;

import com.bobincraft.armament.client.resource.fabric.GlbGpuAsset;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Root placement and camera are explicit. No implicit local-player pose. */
public record GlbRenderContext(Matrix4f viewProjection,int light,Vec3d cameraInModel,GlbGpuAsset gpu) {}
