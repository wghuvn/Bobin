package com.bobincraft.armament.client.render.fabric;

import com.bobincraft.armament.client.resource.fabric.GlbGpuAsset;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix3f;

/** Root placement and camera are explicit. No implicit local-player pose. */
public record GlbRenderContext(Matrix4f viewProjection,int light,Vec3d cameraInModel,GlbGpuAsset gpu,Matrix3f normalTransform,GlbMaterialBindings bindings) {
    public GlbRenderContext(Matrix4f viewProjection,int light,Vec3d cameraInModel,GlbGpuAsset gpu,Matrix3f normalTransform){this(viewProjection,light,cameraInModel,gpu,normalTransform,null);}
    public GlbRenderContext(Matrix4f viewProjection,int light,Vec3d cameraInModel,GlbGpuAsset gpu){this(viewProjection,light,cameraInModel,gpu,new Matrix3f());}
}
