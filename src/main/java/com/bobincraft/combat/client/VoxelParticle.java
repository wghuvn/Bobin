package com.bobincraft.combat.client;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Six world-space textured faces, rather than a quad which always faces the camera. */
abstract class VoxelParticle extends SpriteBillboardParticle {
    private static final int[][] FACES = {{1,0,3,2},{4,5,6,7},{0,4,7,3},{5,1,2,6},{3,7,6,2},{0,1,5,4}};
    private static final float[] SHADE = {.78f,.90f,.70f,.84f,1,.58f};
    private static final float[][] CORNERS = {{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1},
            {-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}};
    protected float halfSize, halfHeight;
    protected boolean hot;
    private int lastValidLight = -1;

    protected VoxelParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
        super(world, x, y, z);
        setVelocity(vx, vy, vz);
        collidesWithWorld = true;
        setBoundingBoxSpacing(.04f, .04f);
    }

    @Override
    protected int getBrightness(float tint) {
        int sampled = ImpactDebrisClient.sampleFragmentLight(this.world, this.x, this.y + Math.max(0.15, this.halfHeight), this.z, this.lastValidLight);
        this.lastValidLight = sampled;
        return sampled;
    }

    @Override public void buildGeometry(VertexConsumer buffer, Camera camera, float delta) {
        Vec3d view = camera.getPos();
        float px = (float) (MathHelper.lerp(delta, prevPosX, x) - view.x);
        float py = (float) (MathHelper.lerp(delta, prevPosY, y) - view.y);
        float pz = (float) (MathHelper.lerp(delta, prevPosZ, z) - view.z);
        float cs = MathHelper.cos(angle), sn = MathHelper.sin(angle);
        int light = hot ? 0xF000F0 : getBrightness(delta);
        for (int f = 0; f < FACES.length; f++) for (int v = 0; v < 4; v++) {
            float[] c = CORNERS[FACES[f][v]];
            float dx = c[0] * halfSize, dz = c[2] * halfSize;
            float u = v == 0 || v == 3 ? getMinU() : getMaxU();
            float texV = v < 2 ? getMaxV() : getMinV();
            buffer.vertex(px + dx * cs - dz * sn, py + c[1] * halfHeight, pz + dx * sn + dz * cs)
                    .texture(u, texV).color(red * SHADE[f], green * SHADE[f], blue * SHADE[f], alpha).light(light).next();
        }
    }

    @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT; }
}
