package com.bobincraft.combat.client;

import com.bobincraft.combat.*;
import com.bobincraft.destruction.DestructionParticles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.*;
import java.io.IOException;
import java.util.*;

public final class VoxelBlastClient {
    private static SpriteProvider cloudSprites;
    private static final int MAX_ACTIVE_SMOKE = 384;
    private static final List<ExplosionSmokeParticle> SMOKE = new ArrayList<>();
    private static final ExplosionVisualEventWindow SEEN = new ExplosionVisualEventWindow(512);
    private record Pending(ExplosionVisualEvent event, WeaponExplosionLayers.Layer layer, long due) {}
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int auxiliaryBudget = 160;
    private static ClientWorld lastWorld;
    private VoxelBlastClient() { }
    private static void reset(ClientWorld world) { lastWorld = world; SMOKE.clear(); PENDING.clear(); SEEN.clear(); }

    public static void register() {
        ParticleFactoryRegistry.getInstance().register(CombatContent.VOXEL_CLOUD, sprites -> {
            cloudSprites = sprites;
            return (type, world, x, y, z, vx, vy, vz) -> new VoxelPuffParticle(world, x, y, z,
                    new VoxelExplosionShape.Puff(0, 0, 0, vx, vy, vz, .20f, 52, .14f, world.random.nextLong()), sprites);
        });
        ParticleFactoryRegistry.getInstance().register(CombatContent.BLOOD_DROPLET, sprites ->
                (type, world, x, y, z, vx, vy, vz) -> new BloodDropletParticle(world, x, y, z, vx, vy, vz, sprites));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset(null));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != lastWorld) reset(client.world);
            auxiliaryBudget = 160;
            SMOKE.removeIf(particle -> !particle.isAlive());
            if (client.world == null || client.isPaused()) return;
            var it = PENDING.iterator();
            while (it.hasNext()) {
                var pending = it.next();
                if (pending.due() <= client.world.getTime()) {
                    spawnLayer(client, pending.event(), pending.layer()); it.remove();
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatEffects.EXPLOSION_VISUAL, (client, handler, buf, sender) -> {
            int length = buf.readableBytes();
            if (length <= 0 || length > ExplosionVisualEvent.MAX_BYTES) return;
            byte[] bytes = new byte[length]; buf.readBytes(bytes);
            final ExplosionVisualEvent event;
            try { event = ExplosionVisualEvent.decode(bytes); }
            catch (IOException ex) { return; }
            client.execute(() -> receive(client, event));
        });
    }

    private static void receive(MinecraftClient client, ExplosionVisualEvent event) {
        ClientWorld world = client.world;
        if (world == null || !world.getRegistryKey().getValue().toString().equals(event.dimension())) return;
        if (world != lastWorld) reset(world);
        if (!SEEN.accept(event.eventId())) return;
        if(Boolean.getBoolean("bobin.destruction.trace"))System.out.println("[Bobin visual] event="+event.eventId()+" owner=VoxelBlastClient profile="+event.profileId()+" material="+event.material()+" position="+event.x()+","+event.y()+","+event.z());
        var profile = ExplosionVisualProfile.find(event.profileId());
        if (profile == null) {
            world.addParticle(ParticleTypes.POOF, event.x(), event.y(), event.z(), 0, .02, 0);
            return;
        }
        var rng = new Random(event.seed());
        var mode = client.options.getParticles().getValue();
        double quality = mode == ParticlesMode.MINIMAL ? .25 : mode == ParticlesMode.DECREASED ? .55 : 1;
        double scale = profile.scale;
        if (event.water()) {
            int count = reserveAuxiliary(Math.max(3, (int)(profile.smokeCount * quality)));
            for (int i = 0; i < count; i++) {
                world.addParticle(i % 2 == 0 ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE,
                        event.x(), event.y(), event.z(), (rng.nextDouble() - .5) * scale * .25,
                        rng.nextDouble() * scale * .2, (rng.nextDouble() - .5) * scale * .25);
            }
            return;
        }

        SMOKE.removeIf(particle -> !particle.isAlive());
        if (cloudSprites != null) {
            int budget = Math.min(MAX_ACTIVE_SMOKE - SMOKE.size() - PENDING.size(), (int)(profile.smokeCount * quality));
            for (var puff : WeaponExplosionLayers.create(profile, event.seed(), budget)) {
                if (puff.delayTicks() == 0) spawnLayer(client, event, puff);
                else PENDING.add(new Pending(event, puff, world.getTime() + puff.delayTicks()));
            }
        }
        int streaks = reserveAuxiliary((int)(profile.streakCount * quality));
        for (int i = 0; i < streaks; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double speed = (.12 + rng.nextDouble() * .18) * scale;
            world.addParticle(ParticleTypes.CRIT, event.x(), event.y() + .1, event.z(),
                    Math.cos(angle) * speed, .08 + rng.nextDouble() * .18, Math.sin(angle) * speed);
        }
        int dust = Math.max(3, (int)(profile.smokeCount * quality * .65));
        var material = event.material() >= 0 ? Block.getStateFromRawId(event.material()) : null;
        if (material != null && !material.isAir() && Block.getRawIdFromState(material) == event.material()) {
            int chips = reserveAuxiliary(dust);
            for (int i = 0; i < chips; i++) {
                double angle = i * Math.PI * 2 / dust;
                world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, material),
                        event.surfaceX(), event.surfaceY() + .06, event.surfaceZ(),
                        Math.cos(angle) * scale * .15 * profile.spread, .12 + rng.nextDouble() * scale * .18,
                        Math.sin(angle) * scale * .15 * profile.spread);
            }
        }
        // Vanilla secondary layers share their own per-tick emission budget.
        dust = reserveAuxiliary(dust);
        // A low dust ring makes wide HE profiles distinct from concentrated breaching profiles.
        for (int i = 0; i < dust; i++) {
            double a = i * Math.PI * 2 / dust;
            world.addParticle(ParticleTypes.CLOUD, event.x(), event.y() + .08, event.z(),
                    Math.cos(a) * .16 * scale * profile.spread, .012, Math.sin(a) * .16 * scale * profile.spread);
        }
    }

    private static int reserveAuxiliary(int requested) {
        int n=Math.max(0,Math.min(auxiliaryBudget,requested));auxiliaryBudget-=n;return n;
    }

    private static void spawnLayer(MinecraftClient client, ExplosionVisualEvent event, WeaponExplosionLayers.Layer layer) {
        if (client.world == null || cloudSprites == null) return;
        // Orient a surface-local effect using a trustworthy loaded collision ray; the wire format stays v1.
        var puff = layer.puff();
        net.minecraft.util.math.Vec3d offset = new net.minecraft.util.math.Vec3d(puff.x(), puff.y(), puff.z());
        net.minecraft.util.math.Vec3d velocity = new net.minecraft.util.math.Vec3d(puff.vx(), puff.vy(), puff.vz());
        if (event.profileId().equals(ExplosionVisualProfile.GROM7_DEMOLITION.id) && client.player != null) {
            var center = new net.minecraft.util.math.Vec3d(event.x(),event.y(),event.z());
            var surface = new net.minecraft.util.math.Vec3d(event.surfaceX(),event.surfaceY(),event.surfaceZ());
            if (center.squaredDistanceTo(surface) > .0001 && center.squaredDistanceTo(surface) < 16) {
                var pos = net.minecraft.util.math.BlockPos.ofFloored(surface);
                if (client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                    var hit = client.world.raycast(new net.minecraft.world.RaycastContext(center,
                            surface.add(surface.subtract(center).normalize().multiply(.2)),
                            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                            net.minecraft.world.RaycastContext.FluidHandling.NONE,client.player));
                    if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                        var normal = net.minecraft.util.math.Vec3d.of(hit.getSide().getVector());
                        var tangent = Math.abs(normal.y) > .9 ? new net.minecraft.util.math.Vec3d(1,0,0)
                                : new net.minecraft.util.math.Vec3d(0,1,0).crossProduct(normal).normalize();
                        var bitangent = tangent.crossProduct(normal);
                        offset = tangent.multiply(puff.x()).add(normal.multiply(puff.y()+.12)).add(bitangent.multiply(puff.z()));
                        velocity = tangent.multiply(puff.vx()).add(normal.multiply(puff.vy())).add(bitangent.multiply(puff.vz()));
                    }
                }
            }
        }
        var oriented = new WeaponExplosionLayers.Layer(layer.kind(), new VoxelExplosionShape.Puff(
                offset.x,offset.y,offset.z,velocity.x,velocity.y,velocity.z,puff.radius(),puff.life(),puff.cooling(),puff.seed()),
                layer.intensity(),layer.expansion(),0);
        var particle = new ExplosionSmokeParticle(client.world,event.x()+offset.x,event.y()+offset.y,event.z()+offset.z,oriented,cloudSprites);
        SMOKE.add(particle);client.particleManager.addParticle(particle);
    }

    /**
     * Spawns a soft billboard volumetric smoke/fire plume particle for burning oil refinery tanks
     * (never spawns 3D cubic VoxelPuffParticle blocks).
     */
    public static void spawnRefineryPlumePuff(ClientWorld world,
                                              double x, double y, double z,
                                              double vx, double vy, double vz,
                                              float initialRadius, int life, float cooling) {
        if (world == null) return;
        world.addImportantParticle(
                cooling < 0.10f ? DestructionParticles.FIRE_PLUME : DestructionParticles.SMOKE_COLUMN,
                true, x, y, z, vx, vy, vz
        );
    }
}
