package com.bobincraft.combat.client;

import com.bobincraft.combat.VoxelExplosionShape;
import com.bobincraft.combat.WeaponExplosionLayers;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;

/** Bounded transient weapon smoke, independent of RefinerySmokeConfig and industrial factories. */
final class ExplosionSmokeParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final float initial, cooling, intensity, expansion;
    private final WeaponExplosionLayers.Kind kind;
    ExplosionSmokeParticle(ClientWorld world, double x, double y, double z,
                           WeaponExplosionLayers.Layer layer, SpriteProvider sprites) {
        super(world, x, y, z);
        var puff = layer.puff();
        this.sprites = sprites; initial = puff.radius(); cooling = puff.cooling();
        kind = layer.kind(); intensity = layer.intensity(); expansion = layer.expansion();
        setVelocity(puff.vx(), puff.vy(), puff.vz());
        maxAge = puff.life(); collidesWithWorld = true;
        angle = prevAngle = (puff.seed() & 1023) * .00614f;
        setSpriteForAge(sprites); envelope();
    }
    @Override public void tick() {
        prevPosX = x; prevPosY = y; prevPosZ = z; prevAngle = angle;
        if (++age >= maxAge) { markDead(); return; }
        move(velocityX, velocityY, velocityZ);
        velocityX *= .94; velocityZ *= .94; velocityY = velocityY * .95 + .0008;
        angle += .008f; setSpriteForAge(sprites); envelope();
    }
    private void envelope() {
        float t = (float) age / maxAge;
        scale = initial * (.65f + expansion * (1 - (float)Math.exp(-t * 3)));
        float heat = t + cooling;
        if (kind == WeaponExplosionLayers.Kind.FLASH) { red = 1; green = .94f; blue = .72f; }
        else if (kind == WeaponExplosionLayers.Kind.FIRE || kind == WeaponExplosionLayers.Kind.EMBER) { red = 1; green = .60f * (1-t) + .18f; blue = .08f; }
        else if (kind == WeaponExplosionLayers.Kind.DUST) { red = .57f; green = .53f; blue = .47f; }
        else if (heat < .18f) { red = 1; green = .67f; blue = .22f; }
        else { float c = MathHelper.clamp(.30f + t * .16f, .30f, .46f); red = c; green = c * .97f; blue = c * .94f; }
        alpha = (kind == WeaponExplosionLayers.Kind.FLASH ? 1-t : Math.min(1, t*12) * Math.min(1,(1-t)*3)) * intensity;
    }
    @Override protected int getBrightness(float tint) {
        return kind == WeaponExplosionLayers.Kind.FLASH || kind == WeaponExplosionLayers.Kind.FIRE || kind == WeaponExplosionLayers.Kind.EMBER
                ? 0xF000F0 : super.getBrightness(tint);
    }
    @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT; }
}
