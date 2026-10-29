package com.bobincraft.combat.client;

import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

final class BloodDropletParticle extends VoxelParticle {
    BloodDropletParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z, vx, vy + .025, vz);
        setSprite(sprites);
        halfSize = halfHeight = .022f + random.nextFloat() * .016f;
        red=.48f + random.nextFloat()*.10f; green=.022f; blue=.014f;
        maxAge = 160 + random.nextInt(60);
    }
    @Override public void tick() {
        prevPosX=x; prevPosY=y; prevPosZ=z;
        if (++age >= maxAge) { markDead(); return; }
        if (onGround) { velocityX=velocityY=velocityZ=0; halfSize=.085f; halfHeight=.0035f; }
        else { velocityY-=.025; move(velocityX,velocityY,velocityZ); velocityX*=.96;velocityZ*=.96; }
        alpha=Math.min(.92f,(maxAge-age)/25f);
    }
}
