package com.bobincraft.combat.client;

import com.bobincraft.combat.ImpactDebris;
import com.bobincraft.combat.TransientDebrisCollision;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import java.util.*;

/** Bounded transient impact chips. These are not Stage 5 persistent structural debris. */
public final class ImpactDebrisClient {
    private record FallingProp(BlockState state,BlockPos pos,float dirX,float dirZ,long startTick) {}
    private static final class Chip {
        final BlockState state; final float sx,sy,sz; final double radius;
        double x,y,z,px,py,pz,vx,vy,vz;float rx,ry,rz,spin;int age,light;boolean sleeping;
        Chip(BlockState state,Vec3d p,Vec3d v,float sx,float sy,float sz,float spin) {
            this.state=state;x=px=p.x;y=py=p.y;z=pz=p.z;vx=v.x;vy=v.y;vz=v.z;
            this.sx=sx;this.sy=sy;this.sz=sz;this.spin=spin;
            radius=Math.sqrt(sx*sx+sy*sy+sz*sz)*.5+.002;
        }
        Box box(){return new Box(x-radius,y-radius,z-radius,x+radius,y+radius,z+radius);}
    }
    private static final List<Chip> CHIPS=new ArrayList<>();
    private static final List<FallingProp> FALLING=new ArrayList<>();
    private static ClientWorld lastWorld;
    private static int spawnBudget=48;
    private static final net.minecraft.client.render.VertexConsumerProvider.Immediate WORLD_BUFFERS =
            net.minecraft.client.render.VertexConsumerProvider.immediate(new net.minecraft.client.render.BufferBuilder(262144));
    private ImpactDebrisClient(){}

    private static final ThreadLocal<BlockPos.Mutable> LIGHT_PROBE = ThreadLocal.withInitial(BlockPos.Mutable::new);
    private static final int[][] PROBE_OFFSETS = {{0,0,0}, {0,1,0}, {0,0,-1}, {0,0,1}, {1,0,0}, {-1,0,0}, {0,-1,0}};

    public static int sampleFragmentLight(ClientWorld world, double x, double y, double z, int fallbackLight) {
        if (world == null) return fallbackLight > 0 ? fallbackLight : LightmapTextureManager.pack(0, 12);
        int cx = MathHelper.floor(x), cy = MathHelper.floor(y), cz = MathHelper.floor(z);
        if (!world.isChunkLoaded(cx >> 4, cz >> 4)) {
            return fallbackLight > 0 ? fallbackLight : LightmapTextureManager.pack(0, 12);
        }

        BlockPos.Mutable probe = LIGHT_PROBE.get();
        probe.set(cx, cy, cz);
        BlockState centerSt = world.getBlockState(probe);
        if (centerSt.isAir() || (!centerSt.isOpaqueFullCube(world, probe) && centerSt.getCollisionShape(world, probe).isEmpty())) {
            int raw = WorldRenderer.getLightmapCoordinates(world, centerSt, probe);
            if (raw > 0) return raw;
        }

        int bestSky = 0;
        int bestBlock = 0;
        boolean foundUnobstructed = false;

        for (int i = 0; i < PROBE_OFFSETS.length; i++) {
            int[] off = PROBE_OFFSETS[i];
            probe.set(cx + off[0], cy + off[1], cz + off[2]);
            BlockState st = world.getBlockState(probe);
            boolean opaque = st.isOpaqueFullCube(world, probe) || !st.getCollisionShape(world, probe).isEmpty();
            int sky = world.getLightLevel(LightType.SKY, probe);
            int blk = world.getLightLevel(LightType.BLOCK, probe);
            if (!opaque) {
                foundUnobstructed = true;
                if (sky > bestSky) bestSky = sky;
                if (blk > bestBlock) bestBlock = blk;
                int raw = WorldRenderer.getLightmapCoordinates(world, st, probe);
                int rawBlock = LightmapTextureManager.getBlockLightCoordinates(raw);
                int rawSky = LightmapTextureManager.getSkyLightCoordinates(raw);
                if (rawBlock > bestBlock) bestBlock = rawBlock;
                if (rawSky > bestSky) bestSky = rawSky;
                if (bestSky >= 12 || bestBlock >= 12) break;
            } else {
                if (sky > bestSky) bestSky = sky;
                if (blk > bestBlock) bestBlock = blk;
            }
        }

        if (!foundUnobstructed && bestSky == 0 && bestBlock == 0 && fallbackLight > 0) {
            return fallbackLight;
        }
        if (fallbackLight > 0) {
            int prevBlock = LightmapTextureManager.getBlockLightCoordinates(fallbackLight);
            int prevSky = LightmapTextureManager.getSkyLightCoordinates(fallbackLight);
            bestBlock = Math.max(bestBlock, Math.max(0, prevBlock - 2));
            bestSky = Math.max(bestSky, Math.max(0, prevSky - 2));
        }
        int packed = LightmapTextureManager.pack(bestBlock, bestSky);
        return packed > 0 ? packed : (fallbackLight > 0 ? fallbackLight : LightmapTextureManager.pack(0, 12));
    }

    private static void clear(ClientWorld world) {lastWorld=world;CHIPS.clear();FALLING.clear();spawnBudget=48;}

    private static List<TransientDebrisCollision.Bounds> obstacles(ClientWorld world,Box swept) {
        for(int x=MathHelper.floor(swept.minX)>>4;x<=MathHelper.floor(swept.maxX)>>4;x++)
            for(int z=MathHelper.floor(swept.minZ)>>4;z<=MathHelper.floor(swept.maxZ)>>4;z++)
                if(!world.isChunkLoaded(x,z))return null;
        var result=new ArrayList<TransientDebrisCollision.Bounds>();
        for(var shape:world.getBlockCollisions(null,swept.expand(.001))) for(var b:shape.getBoundingBoxes()) {
            if(result.size()>=64)return null;
            result.add(new TransientDebrisCollision.Bounds(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ));
        }
        return result;
    }
    private static TransientDebrisCollision.Bounds bounds(Box b) {
        return new TransientDebrisCollision.Bounds(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
    }
    private static boolean free(ClientWorld world,Box box) {
        var obstacles=obstacles(world,box);if(obstacles==null)return false;
        var b=bounds(box);return obstacles.stream().noneMatch(b::overlaps);
    }
    private static void tick(MinecraftClient client) {
        if(client.world!=lastWorld)clear(client.world);
        spawnBudget=48;
        if(client.world==null||client.isPaused())return;
        long now=client.world.getTime();FALLING.removeIf(f->now<f.startTick()||now-f.startTick()>=14);
        var it=CHIPS.iterator();while(it.hasNext()) {
            var c=it.next();c.px=c.x;c.py=c.y;c.pz=c.z;
            if(++c.age>=120||!free(client.world,c.box())){it.remove();continue;}
            if(c.sleeping) {
                if((c.age & 7) != 0) continue;
                var near=obstacles(client.world,c.box().offset(0,-.025,0));
                if(near==null){it.remove();continue;}
                if(TransientDebrisCollision.move(bounds(c.box()),0,-.025,0,near).supported())continue;
                c.sleeping=false;c.vy=0;
            }
            if((c.age & 3) == 0 || c.light <= 0) {
                c.light=sampleFragmentLight(client.world,c.x,c.y,c.z,c.light);
            }
            c.vy=Math.max(-.8,c.vy-.035);
            Box swept=c.box().stretch(c.vx,c.vy,c.vz);
            var near=obstacles(client.world,swept);
            if(near==null){it.remove();continue;}
            var motion=TransientDebrisCollision.move(bounds(c.box()),c.vx,c.vy,c.vz,near);
            c.x+=motion.x();c.y+=motion.y();c.z+=motion.z();
            if(Math.abs(motion.x()-c.vx)>1e-8)c.vx=0;
            if(Math.abs(motion.z()-c.vz)>1e-8)c.vz=0;
            if(Math.abs(motion.y()-c.vy)>1e-8)c.vy=0;
            c.vx*=motion.supported()?.45:.97;c.vz*=motion.supported()?.45:.97;
            c.sleeping=motion.supported()&&c.vx*c.vx+c.vz*c.vz<.0002;
            if(!c.sleeping){c.rx+=c.spin;c.ry+=c.spin*.6f;c.rz+=c.spin*.3f;}
        }
    }
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear(null));
        ClientTickEvents.END_CLIENT_TICK.register(ImpactDebrisClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(ImpactDebris.PACKET,(client,handler,b,sender)->{
            if(b.readableBytes()!=52)return;
            int raw=b.readInt();var p=new Vec3d(b.readDouble(),b.readDouble(),b.readDouble());
            var n=new Vec3d(b.readFloat(),b.readFloat(),b.readFloat());float power=b.readFloat();long seed=b.readLong();
            if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||Math.abs(p.x)>30000000||Math.abs(p.z)>30000000
                    ||!Double.isFinite(n.lengthSquared())||!Float.isFinite(power)||Math.abs(power)>10||raw<0)return;
            BlockState state=Block.getStateFromRawId(raw);
            if(Block.getRawIdFromState(state)!=raw)return;
            client.execute(()->spawn(state,p,n,power,seed));
        });
        WorldRenderEvents.LAST.register(ctx->{
            var client=MinecraftClient.getInstance();if(client.world==null||client.world!=lastWorld)return;
            var matrices=ctx.matrixStack();var consumers=WORLD_BUFFERS;if(matrices==null)return;
            var cam=ctx.camera().getPos();var brm=client.getBlockRenderManager();float dt=ctx.tickDelta();
            for(var f:FALLING) {
                float t=MathHelper.clamp(((client.world.getTime()-f.startTick())+dt)/14f,0,1),ease=t*t;
                matrices.push();matrices.translate(f.pos().getX()+.5-cam.x,f.pos().getY()-cam.y-ease*.12,f.pos().getZ()+.5-cam.z);
                if(f.dirX()*f.dirX()+f.dirZ()*f.dirZ()>1e-4) {
                    float yaw=(float)Math.toDegrees(Math.atan2(f.dirX(),f.dirZ()));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ease*88));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                }
                matrices.translate(-.5,0,-.5);
                brm.renderBlockAsEntity(f.state(),matrices,consumers,sampleFragmentLight(client.world,f.pos().getX()+.5,f.pos().getY()+.65,f.pos().getZ()+.5,-1),OverlayTexture.DEFAULT_UV);
                matrices.pop();
            }
            for(var c:CHIPS) {
                if(cam.squaredDistanceTo(c.x,c.y,c.z)>96*96)continue;
                matrices.push();matrices.translate(MathHelper.lerp(dt,c.px,c.x)-cam.x,MathHelper.lerp(dt,c.py,c.y)-cam.y,MathHelper.lerp(dt,c.pz,c.z)-cam.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(c.ry));matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(c.rx));matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c.rz));
                matrices.scale(c.sx,c.sy,c.sz);matrices.translate(-.5,-.5,-.5);
                brm.renderBlockAsEntity(c.state,matrices,consumers,c.light,OverlayTexture.DEFAULT_UV);matrices.pop();
            }
            consumers.draw();
        });
    }
    public static void spawn(BlockState state,Vec3d p,Vec3d normal,float power,long seed) {
        var client=MinecraftClient.getInstance();if(client.world==null||state==null||state.isAir())return;
        if(client.world!=lastWorld)clear(client.world);
        if(!Double.isFinite(p.x+p.y+p.z+normal.lengthSquared())||!Float.isFinite(power))return;
        Vec3d n=normal.lengthSquared()>1e-6?normal.normalize():new Vec3d(0,1,0);
        if(power<0) {
            if(FALLING.size()<64)FALLING.add(new FallingProp(state,BlockPos.ofFloored(p.x,p.y+.05,p.z),(float)n.x,(float)n.z,client.world.getTime()));
            power=Math.abs(power)*.45f;
        }
        var mode=client.options.getParticles().getValue();var rng=new Random(seed);
        boolean isSmallBulletImpact = power <= 0.25f;
        int count = isSmallBulletImpact
                ? (mode==ParticlesMode.MINIMAL?0:1)
                : (mode==ParticlesMode.MINIMAL?0:mode==ParticlesMode.DECREASED?2:4);
        for(int i=0;i<count&&CHIPS.size()<96&&spawnBudget>0;i++,spawnBudget--) {
            float scale = isSmallBulletImpact ? (.035f+rng.nextFloat()*.030f) : (.055f+rng.nextFloat()*.065f);
            var vel=new Vec3d((rng.nextDouble()-.5)*.28,.12+rng.nextDouble()*.18,(rng.nextDouble()-.5)*.28).add(n.multiply(.14));
            var chip=new Chip(state,p.add(n.multiply(.10)),vel,scale,scale*.55f,scale*.85f,(rng.nextFloat()-.5f)*24);
            if(free(client.world,chip.box()))CHIPS.add(chip);
        }
        int baseDust = isSmallBulletImpact
                ? (mode==ParticlesMode.MINIMAL?1:mode==ParticlesMode.DECREASED?2:3)
                : (mode==ParticlesMode.MINIMAL?3:mode==ParticlesMode.DECREASED?8:14);
        int dust=Math.min(spawnBudget, baseDust);
        for(int i=0;i<dust;i++,spawnBudget--) {
            var scatter=new Vec3d(rng.nextGaussian(),rng.nextGaussian()*.65,rng.nextGaussian());
            if(scatter.dotProduct(n)<0)scatter=scatter.subtract(n.multiply(2*scatter.dotProduct(n)));
            var v=scatter.multiply(.045+Math.min(3,power)*.08).add(n.multiply(.07));
            Vec3d pos=p.add(n.multiply(.05));
            if(free(client.world,new Box(pos.x-.025,pos.y-.025,pos.z-.025,pos.x+.025,pos.y+.025,pos.z+.025)))
                client.particleManager.addParticle(new Chunk(client.world,state,pos,v,rng,power));
        }
    }
    public static int settledRubbleCount(){return (int)CHIPS.stream().filter(c->c.sleeping).count();}

    private static final class Chunk extends VoxelParticle {
        private final float spin;
        Chunk(ClientWorld w, BlockState state, Vec3d p, Vec3d v, java.util.Random r, float power) {
            super(w, p.x, p.y, p.z, v.x, v.y, v.z);
            setSprite(MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(state));
            halfSize = .012f + r.nextFloat() * .018f;
            halfHeight = halfSize * (.35f + r.nextFloat() * .55f);
            maxAge = 32 + r.nextInt(28);
            angle = r.nextFloat() * 6.28f;
            spin = (r.nextFloat() - .5f) * .34f;
            red = green = blue = .8f + r.nextFloat() * .2f;
            setBoundingBoxSpacing(halfSize * 2, halfHeight * 2);
        }
        @Override public void tick() {
            prevPosX = x; prevPosY = y; prevPosZ = z;
            if (++age >= maxAge) { markDead(); return; }
            velocityY -= .035;
            move(velocityX, velocityY, velocityZ);
            if (onGround) {
                velocityX *= .58;
                velocityZ *= .58;
                velocityY = 0;
            } else {
                angle += spin;
                velocityX *= .97;
                velocityZ *= .97;
            }
            alpha = MathHelper.clamp((maxAge - age) / 12f, 0, 1);
        }
        @Override public ParticleTextureSheet getType() { return ParticleTextureSheet.TERRAIN_SHEET; }
    }
}
