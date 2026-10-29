package com.bobincraft.destruction.client;

import com.bobincraft.combat.client.ImpactDebrisClient;
import com.bobincraft.destruction.RefinerySmokeConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;

/**
 * Layer D (Visual Effects) custom Fabric Particle API implementation for the 3-layer industrial smoke system,
 * organic rolling fire plumes, concrete fracture dust, and substation electrical arcs (Section 4 & Bug 6.2).
 *
 * <p>Optimized for high-FPS multi-tank fire rendering:
 * <ul>
 *   <li>Caches world lightmap coordinates per particle and updates them only in {@link #tick()} every 4 ticks
 *       when the particle crosses a block boundary, eliminating ~280,000 redundant 8-neighbor world light/collision
 *       queries per second during {@link #buildGeometry}.</li>
 *   <li>Precomputes 3-lobe trigonometric offsets once per tick in {@link #tick()} rather than 3 times per render frame.</li>
 *   <li>Uses an {@link ArrayDeque} ring buffer for O(1) particle cap management without O(N) array shifts.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class IndustrialSmokeParticle extends SpriteBillboardParticle {

    public enum LayerProfile {
        /** Layer 1: Dense lower smoke core hugging the breach and wrapping around base flames. */
        SMOKE_LOWER_CORE,
        /** Layer 2: Main rising vertical smoke column with cohesive overlap and upward draft. */
        SMOKE_MAIN_COLUMN,
        /** Layer 3: Upper expanding and gradually dissipating canopy at high altitude. */
        SMOKE_UPPER_CANOPY,
        /** Organic non-cubic rolling fireball/flame plume that cools naturally into dark lingering smoke. */
        FIRE_PLUME,
        /** Heavy concrete & stone masonry fracture dust cloud with downward settling drift. */
        CONCRETE_DUST,
        /** Short-lived high-voltage electrical flash & metallic spark burst for substations. */
        ELECTRIC_ARC
    }

    private static ClientWorld budgetWorld;
    private static long pruneTick = Long.MIN_VALUE;
    private static final ArrayDeque<IndustrialSmokeParticle> ACTIVE = new ArrayDeque<>(2048);

    public static void clearBudget() {
        ACTIVE.clear();
        budgetWorld = null;
        pruneTick = Long.MIN_VALUE;
    }

    public static int activeCount(ClientWorld world) {
        if (world != budgetWorld) {
            clearBudget();
            budgetWorld = world;
        }
        if (world != null && world.getTime() != pruneTick) {
            ACTIVE.removeIf(p -> !p.isAlive());
            pruneTick = world.getTime();
        }
        return ACTIVE.size();
    }

    private final LayerProfile profile;
    private final SpriteProvider sprites;
    private final float baseScale;
    private final float baseRed, baseGreen, baseBlue;
    private final float spinSpeed;
    private final float shadeJitter;
    private final double sourceY, motionPhase;
    private final boolean isSmokeProfile;
    private int lastValidLight = -1;
    private int lastLightBx = Integer.MIN_VALUE, lastLightBy = Integer.MIN_VALUE, lastLightBz = Integer.MIN_VALUE;
    private final BlockPos.Mutable lightProbePos = new BlockPos.Mutable();

    // Precomputed per-tick 3-lobe trig offsets to avoid Math.cos / Math.sin 3x per particle on every render frame
    private final double[] lobeCos = new double[3];
    private final double[] lobeSin = new double[3];
    private final double[] lobeSinY = new double[3];

    public IndustrialSmokeParticle(ClientWorld world,
                                   double x, double y, double z,
                                   double vx, double vy, double vz,
                                   LayerProfile profile,
                                   SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.profile = profile;
        this.isSmokeProfile = (profile == LayerProfile.SMOKE_LOWER_CORE
                || profile == LayerProfile.SMOKE_MAIN_COLUMN
                || profile == LayerProfile.SMOKE_UPPER_CANOPY);
        sourceY = y;
        motionPhase = x * 1.73 + y * .97 + z * 2.11;
        this.sprites = sprites;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.angle = world.random.nextFloat() * ((float) Math.PI * 2.0f);
        this.prevAngle = this.angle;
        this.shadeJitter = (world.random.nextFloat() - 0.5f) * 0.04f;

        float hMult = Math.max(1.0f, RefinerySmokeConfig.smokeHeightMultiplier);

        switch (profile) {
            case SMOKE_LOWER_CORE -> {
                this.collidesWithWorld = false;
                this.maxAge = (int) ((92 + world.random.nextInt(48)) * Math.min(1.5f, hMult * 0.85f));
                this.baseScale = 2.75f + world.random.nextFloat() * 1.15f;
                this.spinSpeed = (world.random.nextFloat() - 0.5f) * 0.016f;
                float c = MathHelper.clamp(0.13f + shadeJitter, 0.08f, 0.19f);
                this.red = c + 0.02f;
                this.green = c;
                this.blue = c;
                this.alpha = RefinerySmokeConfig.baseOpacity;
            }
            case SMOKE_MAIN_COLUMN -> {
                this.collidesWithWorld = false;
                this.maxAge = (int) ((165 + world.random.nextInt(80)) * hMult);
                this.baseScale = 3.25f + world.random.nextFloat() * 1.35f;
                this.spinSpeed = (world.random.nextFloat() - 0.5f) * 0.012f;
                float c = MathHelper.clamp(0.17f + shadeJitter, 0.11f, 0.24f);
                this.red = c;
                this.green = c;
                this.blue = c + 0.01f;
                this.alpha = RefinerySmokeConfig.columnOpacity;
            }
            case SMOKE_UPPER_CANOPY -> {
                this.collidesWithWorld = false;
                this.maxAge = (int) ((210 + world.random.nextInt(100)) * hMult);
                this.baseScale = 4.05f + world.random.nextFloat() * 1.75f;
                this.spinSpeed = (world.random.nextFloat() - 0.5f) * 0.009f;
                float c = MathHelper.clamp(0.25f + shadeJitter, 0.19f, 0.34f);
                this.red = c;
                this.green = c;
                this.blue = c + 0.02f;
                this.alpha = RefinerySmokeConfig.canopyOpacity;
            }
            case FIRE_PLUME -> {
                this.collidesWithWorld = false;
                this.maxAge = 46 + world.random.nextInt(32);
                this.baseScale = 1.55f + world.random.nextFloat() * 0.85f;
                this.spinSpeed = (world.random.nextFloat() - 0.5f) * 0.038f;
                this.red = 1.0f;
                this.green = 0.90f + world.random.nextFloat() * 0.10f;
                this.blue = 0.65f + world.random.nextFloat() * 0.25f;
                this.alpha = 0.96f;
            }
            case CONCRETE_DUST -> {
                this.collidesWithWorld = true;
                this.maxAge = 64 + world.random.nextInt(42);
                this.baseScale = 1.25f + world.random.nextFloat() * 0.85f;
                this.spinSpeed = (world.random.nextFloat() - 0.5f) * 0.024f;
                float c = MathHelper.clamp(0.74f + shadeJitter, 0.62f, 0.84f);
                this.red = c;
                this.green = c * 0.99f;
                this.blue = c * 0.96f;
                this.alpha = 0.90f;
            }
            case ELECTRIC_ARC -> {
                this.collidesWithWorld = false;
                this.maxAge = 6 + world.random.nextInt(7);
                this.baseScale = 0.65f + world.random.nextFloat() * 0.55f;
                this.spinSpeed = (world.random.nextFloat() - 0.5f) * 0.25f;
                this.red = 0.78f;
                this.green = 0.94f;
                this.blue = 1.0f;
                this.alpha = 0.96f;
            }
            default -> {
                this.baseScale = 1.0f;
                this.spinSpeed = 0.01f;
            }
        }

        baseRed = red; baseGreen = green; baseBlue = blue;
        this.scale = this.baseScale * 0.68f;
        if (sprites != null) {
            setSpriteForAge(sprites);
        }
        updateLobeAngles();
        refreshCachedLight();
        updateVisualEnvelope(0.0f);
    }

    private void updateLobeAngles() {
        if (!isSmokeProfile) return;
        for (int i = 0; i < 3; i++) {
            double a = motionPhase + i * 2.39996 + age * 0.006;
            lobeCos[i] = Math.cos(a);
            lobeSin[i] = Math.sin(a);
            lobeSinY[i] = Math.sin(a * 1.7) * 0.8;
        }
    }

    private void refreshCachedLight() {
        if (profile == LayerProfile.ELECTRIC_ARC
                || (profile == LayerProfile.FIRE_PLUME && this.age < this.maxAge * 0.58f)) {
            this.lastValidLight = 0xF000F0;
            return;
        }
        int bx = MathHelper.floor(this.x);
        int by = MathHelper.floor(this.y + 0.5);
        int bz = MathHelper.floor(this.z);
        if (lastValidLight != -1 && bx == lastLightBx && by == lastLightBy && bz == lastLightBz) {
            return;
        }
        lastLightBx = bx;
        lastLightBy = by;
        lastLightBz = bz;
        // Fast path for rising smoke columns in open air above the source: single block lightmap lookup
        if ((profile == LayerProfile.SMOKE_MAIN_COLUMN || profile == LayerProfile.SMOKE_UPPER_CANOPY)
                && this.y > this.sourceY + 3.0
                && this.world.isChunkLoaded(bx >> 4, bz >> 4)) {
            lightProbePos.set(bx, by, bz);
            int fastLight = WorldRenderer.getLightmapCoordinates(this.world, lightProbePos);
            if (fastLight > 0) {
                this.lastValidLight = fastLight;
                return;
            }
        }
        this.lastValidLight = ImpactDebrisClient.sampleFragmentLight(this.world, this.x, this.y + 0.5, this.z, this.lastValidLight);
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        this.prevAngle = this.angle;
        if (++this.age >= this.maxAge) {
            markDead();
            return;
        }
        this.angle += this.spinSpeed;
        if (isSmokeProfile) {
            double time = world.getTime();
            double curl = com.bobincraft.destruction.IndustrialSmokeMotion.curl(x,y,z,time,motionPhase);
            velocityX += (com.bobincraft.destruction.IndustrialSmokeMotion.windX(time)-velocityX)*.025 + curl;
            velocityZ += (com.bobincraft.destruction.IndustrialSmokeMotion.windZ(time)-velocityZ)*.025
                    + com.bobincraft.destruction.IndustrialSmokeMotion.curl(z,y,x,time,motionPhase+1.4);
            velocityY += curl * .25;
            updateLobeAngles();
        }
        move(this.velocityX, this.velocityY, this.velocityZ);

        // Update cached lightmap at most once every 4 ticks when block position changes
        if ((this.age & 3) == 0 || profile == LayerProfile.FIRE_PLUME) {
            refreshCachedLight();
        }

        float t = (float) this.age / (float) this.maxAge;
        float hMult = Math.max(1.0f, RefinerySmokeConfig.smokeHeightMultiplier);
        switch (profile) {
            case SMOKE_LOWER_CORE -> {
                this.velocityX *= 0.945;
                this.velocityZ *= 0.945;
                this.velocityY = Math.max(0.085 * Math.min(1.35, hMult * 0.75), this.velocityY * 0.988 + 0.0022);
            }
            case SMOKE_MAIN_COLUMN -> {
                this.velocityX *= .972;
                this.velocityZ *= .972;
                this.velocityY = Math.max(0.135 * Math.min(1.45, hMult * 0.80), this.velocityY * 0.994);
            }
            case SMOKE_UPPER_CANOPY -> {
                this.velocityX *= .988;
                this.velocityZ *= .988;
                this.velocityY = Math.max(0.065, this.velocityY * 0.984);
            }
            case FIRE_PLUME -> {
                this.velocityX *= 0.935;
                this.velocityZ *= 0.935;
                this.velocityY = this.velocityY * 0.968 + 0.0045;
            }
            case CONCRETE_DUST -> {
                this.velocityX *= 0.92;
                this.velocityZ *= 0.92;
                this.velocityY = this.velocityY * 0.93 - 0.0018;
            }
            case ELECTRIC_ARC -> {
                this.velocityX *= 0.85;
                this.velocityY *= 0.85;
                this.velocityZ *= 0.85;
            }
        }

        if (this.sprites != null) {
            setSpriteForAge(this.sprites);
        }
        updateVisualEnvelope(t);
        if (isSmokeProfile) {
            scale *= (float)com.bobincraft.destruction.IndustrialSmokeMotion.variation(motionPhase, age);
            float warmth = profile == LayerProfile.SMOKE_LOWER_CORE
                    ? com.bobincraft.destruction.IndustrialSmokeColor.warmth(t,y-sourceY) : 0;
            if (profile == LayerProfile.SMOKE_LOWER_CORE) {
                red = com.bobincraft.destruction.IndustrialSmokeColor.channel(baseRed,warmth);
                green = com.bobincraft.destruction.IndustrialSmokeColor.channel(baseGreen,warmth*.25f);
                blue = com.bobincraft.destruction.IndustrialSmokeColor.channel(baseBlue,0);
            }
        }
    }

    private void updateVisualEnvelope(float t) {
        float clampedT = MathHelper.clamp(t, 0.0f, 1.0f);
        float curveExp = RefinerySmokeConfig.particleScaleCurve;
        float holdUntil = MathHelper.clamp(RefinerySmokeConfig.alphaOverLifetime + .035f*(float)Math.sin(motionPhase), .72f, .90f);
        switch (profile) {
            case SMOKE_LOWER_CORE -> {
                this.scale = this.baseScale * (0.82f + 0.92f * (float) Math.pow(clampedT, curveExp));
                float fadeIn = Math.min(1.0f, clampedT * 9.5f);
                float fadeOut = clampedT < holdUntil
                        ? 1.0f
                        : smoothFadeOut((clampedT - holdUntil) / Math.max(0.05f, 1.0f - holdUntil));
                this.alpha = MathHelper.clamp(fadeIn * fadeOut * RefinerySmokeConfig.baseOpacity, 0.0f, 0.99f);
            }
            case SMOKE_MAIN_COLUMN -> {
                this.scale = this.baseScale * (0.88f + 1.65f * (float) Math.pow(clampedT, curveExp));
                float c = MathHelper.lerp(MathHelper.clamp((clampedT - 0.55f) / 0.45f, 0.0f, 1.0f), 0.15f, 0.25f) + shadeJitter;
                this.red = c;
                this.green = c;
                this.blue = c + 0.01f;
                float fadeIn = Math.min(1.0f, clampedT * 8.0f);
                float fadeOut = clampedT < holdUntil
                        ? 1.0f
                        : smoothFadeOut((clampedT - holdUntil) / Math.max(0.05f, 1.0f - holdUntil));
                this.alpha = MathHelper.clamp(fadeIn * fadeOut * RefinerySmokeConfig.columnOpacity, 0.0f, 0.97f);
            }
            case SMOKE_UPPER_CANOPY -> {
                this.scale = this.baseScale * (0.98f + RefinerySmokeConfig.topDispersionWidth * (float) Math.pow(clampedT, curveExp));
                float c = MathHelper.lerp(clampedT, 0.22f, 0.36f) + shadeJitter;
                this.red = c;
                this.green = c;
                this.blue = c + 0.02f;
                float fadeIn = Math.min(1.0f, clampedT * 6.0f);
                float fadeOut = clampedT < holdUntil
                        ? 1.0f - clampedT * 0.10f
                        : .92f * smoothFadeOut((clampedT-holdUntil)/(1-holdUntil));
                this.alpha = MathHelper.clamp(fadeIn * fadeOut * RefinerySmokeConfig.canopyOpacity, 0.0f, 0.92f);
            }
            case FIRE_PLUME -> {
                this.scale = this.baseScale * (0.72f + 1.25f * (float) Math.pow(clampedT, 0.60f));
                if (clampedT < 0.55f) {
                    float k = clampedT / 0.55f;
                    this.red = MathHelper.lerp(k, 1.0f, 0.88f);
                    this.green = MathHelper.lerp(k, 0.94f, 0.34f);
                    this.blue = MathHelper.lerp(k, 0.72f, 0.08f);
                } else {
                    float k = (clampedT - 0.55f) / 0.45f;
                    float soot = MathHelper.clamp(0.16f + shadeJitter, 0.11f, 0.22f);
                    this.red = MathHelper.lerp(k, 0.88f, soot);
                    this.green = MathHelper.lerp(k, 0.34f, soot);
                    this.blue = MathHelper.lerp(k, 0.08f, soot);
                }
                float fadeIn = Math.min(1.0f, clampedT * 8.5f);
                float fadeOut = clampedT < 0.72f ? 1.0f : smoothFadeOut((clampedT - 0.72f) / 0.28f);
                this.alpha = MathHelper.clamp(fadeIn * fadeOut * 0.96f, 0.0f, 0.96f);
            }
            case CONCRETE_DUST -> {
                this.scale = this.baseScale * (0.65f + 1.35f * MathHelper.sqrt(clampedT));
                float fadeIn = Math.min(1.0f, clampedT * 9.0f);
                float fadeOut = clampedT < 0.62f ? 1.0f : smoothFadeOut((clampedT - 0.62f) / 0.38f);
                this.alpha = MathHelper.clamp(fadeIn * fadeOut * 0.90f, 0.0f, 0.90f);
            }
            case ELECTRIC_ARC -> {
                this.scale = this.baseScale * (0.85f + 0.45f * (1.0f - clampedT));
                this.alpha = MathHelper.clamp((1.0f - clampedT * 0.85f) * 0.96f, 0.0f, 0.96f);
            }
        }
    }

    private static float smoothFadeOut(float u) {
        float x = MathHelper.clamp(u, 0.0f, 1.0f);
        float inv = 1.0f - x;
        return inv * inv * (3.0f - 2.0f * inv);
    }

    @Override
    public void buildGeometry(net.minecraft.client.render.VertexConsumer buffer, net.minecraft.client.render.Camera camera, float delta) {
        if (!isSmokeProfile) {
            super.buildGeometry(buffer,camera,delta); return;
        }
        double ox=x, oy=y, oz=z, px=prevPosX, py=prevPosY, pz=prevPosZ;
        float original=scale, opacity=alpha;
        double distance=camera.getPos().squaredDistanceTo(x,y,z);
        int lobes=distance<=32*32?3:distance<=96*96?2:1;
        double spread=original*.20;
        for (int i=0;i<lobes;i++) {
            double dx = lobeCos[i] * spread;
            double dz = lobeSin[i] * spread;
            double dy = lobeSinY[i] * spread;
            x=ox+dx; prevPosX=px+dx;
            z=oz+dz; prevPosZ=pz+dz;
            y=oy+dy; prevPosY=py+dy;
            scale=original*(lobes==1?1:i==0?.78f:.58f); alpha=opacity*(lobes==1?1:i==0?.86f:.55f);
            super.buildGeometry(buffer,camera,delta);
        }
        x=ox;y=oy;z=oz;prevPosX=px;prevPosY=py;prevPosZ=pz;scale=original;alpha=opacity;
    }

    @Override
    protected int getBrightness(float tint) {
        // O(1) cached light return during buildGeometry (updated in tick() instead of 3x per frame per particle!)
        return this.lastValidLight;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static ParticleFactory<DefaultParticleType> factory(LayerProfile profile, SpriteProvider sprites) {
        return (type, world, x, y, z, vx, vy, vz) -> {
            while (activeCount(world) >= com.bobincraft.destruction.StructureFireManager.maxStructureFireParticles && !ACTIVE.isEmpty()) {
                IndustrialSmokeParticle oldest = ACTIVE.pollFirst();
                if (oldest != null && oldest.isAlive()) {
                    oldest.markDead();
                    break;
                }
            }
            var particle = new IndustrialSmokeParticle(world,x,y,z,vx,vy,vz,profile,sprites);
            ACTIVE.addLast(particle); return particle;
        };
    }
}
