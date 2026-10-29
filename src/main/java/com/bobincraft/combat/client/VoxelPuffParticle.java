package com.bobincraft.combat.client;

import com.bobincraft.combat.VoxelExplosionShape;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

final class VoxelPuffParticle extends VoxelParticle {
    private final float initial, cooling;
    private final int mode;
    private final SpriteProvider sprites;
    private final float lobeVariance;

    VoxelPuffParticle(ClientWorld world, double x, double y, double z, VoxelExplosionShape.Puff puff, SpriteProvider sprites) {
        super(world, x, y, z, puff.vx(), puff.vy(), puff.vz());
        this.initial = puff.radius();
        this.cooling = puff.cooling();
        this.mode = puff.mode();
        this.sprites = sprites;
        this.maxAge = puff.life();
        this.angle = (float) (puff.seed() & 1023) * .00614f;
        this.lobeVariance = ((puff.seed() >>> 16) & 1023) / 1023f * 0.07f;
        if (mode == VoxelExplosionShape.MODE_REFINERY_PLUME || mode == VoxelExplosionShape.MODE_DEMOLITION_BLAST) {
            collidesWithWorld = false;
        }
        setSpriteForAge(sprites);
        updateColor();
    }

    @Override public void tick() {
        prevPosX = x; prevPosY = y; prevPosZ = z;
        if (++age >= maxAge) { markDead(); return; }
        move(velocityX, velocityY, velocityZ);
        if (mode == VoxelExplosionShape.MODE_REFINERY_PLUME) {
            // Buoyant hydrocarbon smoke column: maintains strong upward thermal draft and billows outward into lobes
            velocityX = velocityX * 0.965 + 0.0008;
            velocityZ = velocityZ * 0.965 + 0.0005;
            velocityY = Math.max(0.085, velocityY * 0.988);
            angle += 0.009f;
        } else if (mode == VoxelExplosionShape.MODE_MEGA_STRIKE || mode == VoxelExplosionShape.MODE_DEMOLITION_BLAST) {
            velocityX *= 0.942;
            velocityZ *= 0.942;
            velocityY = velocityY * 0.972 + 0.0028;
            angle += 0.011f;
        } else {
            velocityX *= .925; velocityZ *= .925; velocityY = velocityY * .958 + .0018;
            angle += 0.013f;
        }
        if (sprites != null) setSpriteForAge(sprites);
        updateColor();
    }

    private void updateColor() {
        float t = (float) age / maxAge;
        float r = VoxelExplosionShape.plumeRadius(initial, t, mode);
        halfSize = r;
        halfHeight = r * (0.82f + 0.24f * t);
        float heat = Math.min(1.0f, t + cooling);

        if (mode == VoxelExplosionShape.MODE_REFINERY_PLUME) {
            if (heat < 0.14f) {
                float k = heat / 0.14f;
                red = MathHelper.lerp(k, 1.00f, 0.96f);
                green = MathHelper.lerp(k, 0.68f, 0.40f);
                blue = MathHelper.lerp(k, 0.18f, 0.08f);
                hot = true;
            } else if (heat < 0.26f) {
                float k = (heat - 0.14f) / 0.12f;
                red = MathHelper.lerp(k, 0.96f, 0.20f);
                green = MathHelper.lerp(k, 0.40f, 0.18f);
                blue = MathHelper.lerp(k, 0.08f, 0.17f);
                hot = k < 0.50f;
            } else {
                // Rich multi-toned dark charcoal & black soot lobes with depth shading
                float baseDark = MathHelper.lerp(Math.min(1.0f, (heat - 0.26f) / 0.74f), 0.18f, 0.12f) + lobeVariance;
                red = baseDark;
                green = baseDark * 0.98f;
                blue = baseDark * 1.02f;
                hot = false;
            }
            float fadeIn = Math.min(1.0f, t * 8.5f);
            float fadeOut = t < 0.78f ? 1.0f : smoothFade((t - 0.78f) / 0.22f);
            alpha = MathHelper.clamp(fadeIn * fadeOut * 0.94f, 0.0f, 0.94f);
            return;
        }

        if (mode == VoxelExplosionShape.MODE_DEMOLITION_BLAST) {
            // Custom GROM-7 & structural demolition profile:
            // Intense white-amber flash -> sustained rolling crimson/orange fire -> thick, opaque charcoal shock smoke
            if (heat < 0.10f) {
                float k = heat / 0.10f;
                red = 1.00f;
                green = MathHelper.lerp(k, 0.96f, 0.66f);
                blue = MathHelper.lerp(k, 0.74f, 0.18f);
                hot = true;
            } else if (heat < 0.36f) {
                float k = (heat - 0.10f) / 0.26f;
                red = MathHelper.lerp(k, 0.98f, 0.22f);
                green = MathHelper.lerp(k, 0.52f, 0.19f);
                blue = MathHelper.lerp(k, 0.12f, 0.18f);
                hot = k < 0.68f;
            } else {
                float shade = MathHelper.lerp(Math.min(1.0f, (heat - 0.36f) / 0.64f), 0.22f, 0.14f) + lobeVariance;
                red = shade;
                green = shade * 0.98f;
                blue = shade * 0.96f;
                hot = false;
            }
            float fadeIn = Math.min(1.0f, t * 9.5f);
            float fadeOut = t < 0.74f ? 1.0f : smoothFade((t - 0.74f) / 0.26f);
            alpha = MathHelper.clamp(fadeIn * fadeOut * 0.92f, 0.0f, 0.92f);
            return;
        }

        if (mode == VoxelExplosionShape.MODE_MEGA_STRIKE) {
            // Epic 3x FP-1 detonation: white-gold flash -> roaring orange-red fireball -> dark charcoal mushroom cloud
            if (heat < 0.08f) {
                float k = heat / 0.08f;
                red = 1.00f;
                green = MathHelper.lerp(k, 0.94f, 0.62f);
                blue = MathHelper.lerp(k, 0.68f, 0.15f);
                hot = true;
            } else if (heat < 0.34f) {
                float k = (heat - 0.08f) / 0.26f;
                red = MathHelper.lerp(k, 0.98f, 0.24f);
                green = MathHelper.lerp(k, 0.48f, 0.21f);
                blue = MathHelper.lerp(k, 0.10f, 0.19f);
                hot = k < 0.68f;
            } else {
                float shade = MathHelper.lerp(Math.min(1.0f, (heat - 0.34f) / 0.66f), 0.22f, 0.14f);
                red = shade;
                green = shade * 0.97f;
                blue = shade * 0.95f;
                hot = false;
            }
            float fadeIn = Math.min(1.0f, t * 9.0f);
            float fadeOut = t < 0.72f ? 1.0f : smoothFade((t - 0.72f) / 0.28f);
            alpha = MathHelper.clamp(fadeIn * fadeOut * 0.92f, 0.0f, 0.92f);
            return;
        }

        // Standard blast dust/smoke mode with rich opacity (0.86f instead of weak 0.44f) and longer fire core
        if (heat < 0.16f) {
            float k = heat / 0.16f;
            red = MathHelper.lerp(k, 0.96f, 0.74f);
            green = MathHelper.lerp(k, 0.72f, 0.48f);
            blue = MathHelper.lerp(k, 0.38f, 0.32f);
        } else if (heat < 0.34f) {
            float k = (heat - 0.16f) / 0.18f;
            red = MathHelper.lerp(k, 0.74f, 0.42f);
            green = MathHelper.lerp(k, 0.48f, 0.39f);
            blue = MathHelper.lerp(k, 0.32f, 0.37f);
        } else {
            float shade = MathHelper.clamp(0.42f - (heat - 0.34f) * 0.18f, 0.24f, 0.42f);
            red = shade;
            green = shade * 0.98f;
            blue = shade * 0.95f;
        }
        hot = heat < 0.20f;
        float fadeIn = Math.min(1.0f, t * 8.0f);
        float fadeOut = t < 0.65f ? 1.0f : smoothFade((t - 0.65f) / 0.35f);
        alpha = MathHelper.clamp(fadeIn * fadeOut * 0.86f, 0.0f, 0.86f);
    }

    private static float smoothFade(float u) {
        float x = MathHelper.clamp(u, 0.0f, 1.0f);
        float inv = 1.0f - x;
        return inv * inv * (3.0f - 2.0f * inv);
    }
}
