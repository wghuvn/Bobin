package com.bobincraft.destruction.client;

import com.bobincraft.destruction.DestructibleStructureType;
import com.bobincraft.destruction.DestructionParticles;
import com.bobincraft.destruction.RefinerySmokeConfig;
import com.bobincraft.destruction.StructuralSupportSolver;
import com.bobincraft.destruction.StructureDamageState;
import com.bobincraft.destruction.StructureFireManager;
import com.bobincraft.destruction.IndustrialSmokeBudget;
import com.bobincraft.destruction.StructureNetworkSync;
import com.bobincraft.drone.client.DroneClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer D (Visual Effects) client-side manager for Scripted Structural Destruction (Section 2, Section 4, Section 6).
 */
@Environment(EnvType.CLIENT)
public final class StructureDestructionClient {
    private static final net.minecraft.client.render.VertexConsumerProvider.Immediate CRACK_BUFFERS =
            net.minecraft.client.render.VertexConsumerProvider.immediate(new net.minecraft.client.render.BufferBuilder(16384));
    private static final Map<String, StructureNetworkSync.ClientStructureView> CLIENT_STRUCTURES = new ConcurrentHashMap<>();
    private static final Map<String, Double> PARTICLE_ACCUMULATORS = new HashMap<>();
    private static volatile boolean debugRender = false;
    private static int activeClientParticlesEstimate = 0;

    private StructureDestructionClient() {}

    public static void register() {
        DestructionParticles.register();

        ParticleFactoryRegistry.getInstance().register(DestructionParticles.SMOKE_CORE,
                sprites -> IndustrialSmokeParticle.factory(IndustrialSmokeParticle.LayerProfile.SMOKE_LOWER_CORE, sprites));
        ParticleFactoryRegistry.getInstance().register(DestructionParticles.SMOKE_COLUMN,
                sprites -> IndustrialSmokeParticle.factory(IndustrialSmokeParticle.LayerProfile.SMOKE_MAIN_COLUMN, sprites));
        ParticleFactoryRegistry.getInstance().register(DestructionParticles.SMOKE_CANOPY,
                sprites -> IndustrialSmokeParticle.factory(IndustrialSmokeParticle.LayerProfile.SMOKE_UPPER_CANOPY, sprites));
        ParticleFactoryRegistry.getInstance().register(DestructionParticles.FIRE_PLUME,
                sprites -> IndustrialSmokeParticle.factory(IndustrialSmokeParticle.LayerProfile.FIRE_PLUME, sprites));
        ParticleFactoryRegistry.getInstance().register(DestructionParticles.CONCRETE_DUST,
                sprites -> IndustrialSmokeParticle.factory(IndustrialSmokeParticle.LayerProfile.CONCRETE_DUST, sprites));
        ParticleFactoryRegistry.getInstance().register(DestructionParticles.ELECTRIC_ARC,
                sprites -> IndustrialSmokeParticle.factory(IndustrialSmokeParticle.LayerProfile.ELECTRIC_ARC, sprites));

        ClientPlayNetworking.registerGlobalReceiver(StructureNetworkSync.STATE_SYNC_ID, (client, handler, buf, sender) -> {
            List<StructureNetworkSync.ClientStructureView> batch = StructureNetworkSync.decodeStateBatch(buf);
            client.execute(() -> {
                for (StructureNetworkSync.ClientStructureView view : batch) {
                    CLIENT_STRUCTURES.put(view.structureId(), view);
                }
            });
        });

        // STRUCTURE_HIT (HP > 0): local hit dust, sparks, surface crack decal, NO collapse animation!
        ClientPlayNetworking.registerGlobalReceiver(StructureNetworkSync.HIT_EFFECT_ID, (client, handler, buf, sender) -> {
            StructureNetworkSync.StructureHitPacket pkt = StructureNetworkSync.decodeStructureHit(buf);
            client.execute(() -> {
                if (client.world != null) {
                    DestructionKeyframeAnimator.addHitCrackDecal(client.world, pkt.structureId(), pkt.impactPoint());
                    playLocalHitEffect(client, pkt);
                }
            });
        });

        // STRUCTURE_DESTROYED (HP reaches 0): full collapse keyframe animation + terminal burst
        ClientPlayNetworking.registerGlobalReceiver(StructureNetworkSync.TRANSITION_EFFECT_ID, (client, handler, buf, sender) -> {
            StructureNetworkSync.TransitionScenarioPacket pkt = StructureNetworkSync.decodeTransitionScenario(buf);
            client.execute(() -> {
                if (client.world != null) {
                    DestructionKeyframeAnimator.startScenario(client.world, pkt);
                }
                playTransitionBurst(client, pkt.structureId(), pkt.origin(), pkt.oldStage(), pkt.newStage());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(StructureNetworkSync.DEBUG_TOGGLE_ID, (client, handler, buf, sender) -> {
            boolean enabled = buf.readBoolean();
            client.execute(() -> debugBoundsEnabled(enabled));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CLIENT_STRUCTURES.clear();
            PARTICLE_ACCUMULATORS.clear();
            DestructionKeyframeAnimator.clear();
            debugRender = false;
            activeClientParticlesEstimate = 0;
            IndustrialSmokeParticle.clearBudget();
        });

        ClientTickEvents.END_CLIENT_TICK.register(StructureDestructionClient::tickClientEffects);

        WorldRenderEvents.LAST.register(context -> {
            if (context.matrixStack() == null) return;
            var consumers = CRACK_BUFFERS;
            DestructionKeyframeAnimator.renderActive(
                    context.matrixStack(),
                    consumers,
                    context.camera().getPos(),
                    context.tickDelta()
            );
            consumers.draw();
        });

        WorldRenderEvents.LAST.register(context -> {
            if (!debugRender || CLIENT_STRUCTURES.isEmpty()) return;
            MatrixStack matrices = context.matrixStack();
            Vec3d cam = context.camera().getPos();
            var consumers = context.consumers();
            if (consumers == null) return;
            VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);
            for (StructureNetworkSync.ClientStructureView view : CLIENT_STRUCTURES.values()) {
                BlockPos o = view.origin();
                int r = Math.max(3, view.radius());
                int h = Math.max(4, view.height());
                Box box = new Box(o.getX() - r, o.getY(), o.getZ() - r, o.getX() + r + 1, o.getY() + h + 1, o.getZ() + r + 1);
                float red = view.stage().isTerminalOrDestroying() ? 0.95f : (view.isBurning() ? 1.0f : 0.2f);
                float green = view.stage() == StructureDamageState.INTACT ? 0.9f : (view.stage() == StructureDamageState.DAMAGED ? 0.8f : 0.15f);
                float blue = view.stage() == StructureDamageState.CRITICAL ? 0.9f : 0.2f;
                WorldRenderer.drawBox(matrices, lines, box, red, green, blue, 0.85f);
            }
            matrices.pop();
        });
    }

    private static void debugBoundsEnabled(boolean enabled) {
        debugRender = enabled;
    }

    private static boolean isSmokeEmitter(StructureNetworkSync.ClientStructureView view,
                                          ClientWorld world, Vec3d observer) {
        BlockPos origin = view.origin();
        Vec3d center = new Vec3d(origin.getX() + 0.5, origin.getY() + view.height() * 0.70, origin.getZ() + 0.5);
        boolean activeRefineryTank = view.category() == DestructibleStructureType.Category.REFINERY_TANK
                && (view.isBurning() || view.stage() != StructureDamageState.INTACT || !view.breachOpenings().isEmpty());
        boolean emitting = view.isBurning() || activeRefineryTank;
        float intensity = view.isBurning() ? Math.max(0.65f, view.fireIntensity()) : (activeRefineryTank ? 0.78f : 0.0f);
        return IndustrialSmokeBudget.isEmitter(
                view.category() != DestructibleStructureType.Category.BRIDGE_SECTION,
                emitting, intensity,
                world.isChunkLoaded(origin.getX() >> 4, origin.getZ() >> 4),
                observer.distanceTo(center), StructureFireManager.farLodDistance);
    }

    private static void tickClientEffects(MinecraftClient client) {
        ClientWorld world = client.world;
        DestructionKeyframeAnimator.tick(world);
        if (world == null || client.isPaused() || CLIENT_STRUCTURES.isEmpty()) return;

        activeClientParticlesEstimate = IndustrialSmokeParticle.activeCount(world);

        Vec3d observer = client.gameRenderer != null && client.gameRenderer.getCamera() != null
                ? client.gameRenderer.getCamera().getPos()
                : (DroneClient.isActive() && DroneClient.getDrone() != null ? DroneClient.getDrone().getPos()
                : (client.player != null ? client.player.getEyePos() : Vec3d.ZERO));

        int activeSmokeEmitters = 0;
        for (StructureNetworkSync.ClientStructureView v : CLIENT_STRUCTURES.values()) {
            if (isSmokeEmitter(v, world, observer)) activeSmokeEmitters++;
        }

        float hMult = Math.max(1.0f, RefinerySmokeConfig.smokeHeightMultiplier);
        float densityMult = Math.max(1.0f, RefinerySmokeConfig.columnDensity);

        for (StructureNetworkSync.ClientStructureView v : CLIENT_STRUCTURES.values()) {
            if (v.category() == DestructibleStructureType.Category.BRIDGE_SECTION) continue;

            BlockPos origin = v.origin();
            if (!world.isChunkLoaded(origin.getX() >> 4, origin.getZ() >> 4)) continue;

            Vec3d center = new Vec3d(origin.getX() + 0.5, origin.getY() + v.height() * 0.70, origin.getZ() + 0.5);
            double dist = observer.distanceTo(center);
            if (dist > StructureFireManager.farLodDistance) continue;

            if ((v.category() == DestructibleStructureType.Category.SUBSTATION_TRANSFORMER
                    || v.category() == DestructibleStructureType.Category.SUBSTATION_EQUIPMENT)
                    && (v.stage() != StructureDamageState.INTACT || !v.breachOpenings().isEmpty())
                    && dist <= StructureFireManager.mediumLodDistance) {
                int arcInterval = v.stage() == StructureDamageState.CRITICAL ? 4
                        : (v.stage() == StructureDamageState.DAMAGED ? 9 : 14);
                if (world.random.nextInt(arcInterval) == 0) {
                    double sx = center.x + (world.random.nextDouble() - 0.5) * v.radius();
                    double sy = origin.getY() + 2.2 + world.random.nextDouble() * 3.8;
                    double sz = center.z + (world.random.nextDouble() - 0.5) * v.radius();
                    if (!v.breachOpenings().isEmpty()) {
                        var b = v.breachOpenings().get(world.random.nextInt(v.breachOpenings().size()));
                        sx = b.centerX() + 0.5 + (world.random.nextDouble() - 0.5) * 1.6;
                        sy = b.centerY() + 0.5 + (world.random.nextDouble() - 0.5) * 1.6;
                        sz = b.centerZ() + 0.5 + (world.random.nextDouble() - 0.5) * 1.6;
                    }
                    world.addParticle(DestructionParticles.ELECTRIC_ARC, sx, sy, sz,
                            (world.random.nextDouble() - 0.5) * 0.10, 0.05, (world.random.nextDouble() - 0.5) * 0.10);
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK, sx, sy, sz,
                            (world.random.nextDouble() - 0.5) * 0.18, 0.08, (world.random.nextDouble() - 0.5) * 0.18);
                    activeClientParticlesEstimate += 2;
                    if (dist <= StructureFireManager.nearLodDistance && world.random.nextInt(4) == 0) {
                        world.playSound(sx, sy, sz, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.35f, 1.85f, false);
                    }
                }
            }

            if (!isSmokeEmitter(v, world, observer)) continue;
            boolean isRefineryTank = v.category() == DestructibleStructureType.Category.REFINERY_TANK;
            boolean tankSmoking = isRefineryTank && (v.isBurning() || v.stage() != StructureDamageState.INTACT || !v.breachOpenings().isEmpty());
            float effectiveIntensity = v.isBurning() ? Math.max(0.75f, v.fireIntensity()) : (tankSmoking ? 0.78f : 0.65f);

            double ratePerSecond = StructureFireManager.budgetedParticlesPerSecond(dist, effectiveIntensity, Math.max(1, activeSmokeEmitters));
            double pulseMultiplier = tankSmoking ? (3.0 * densityMult) : 1.6;
            double ratePerTick = Math.max(0.35, (ratePerSecond * pulseMultiplier) / 20.0);
            double acc = PARTICLE_ACCUMULATORS.getOrDefault(v.structureId(), 0.0) + ratePerTick;

            int toSpawn = Math.min(8, (int) Math.floor(acc));
            acc -= toSpawn;
            PARTICLE_ACCUMULATORS.put(v.structureId(), acc);

            double baseHeight = origin.getY() + Math.max(2.0, v.height() * (v.stage().isTerminalOrDestroying() ? 0.35 : 0.72));
            List<StructuralSupportSolver.BreachOpening> openings = v.breachOpenings();

            // Every active tank gets its own guaranteed spawn pass (IndustrialSmokeParticle evicts oldest if at cap)
            for (int i = 0; i < toSpawn; i++) {
                StructuralSupportSolver.BreachOpening activeBreach = (!openings.isEmpty() && (v.stage() != StructureDamageState.DESTROYED || i % 2 == 0))
                        ? openings.get(i % openings.size())
                        : null;

                double px, py, pz;
                double jetVx = 0.0, jetVy = 0.0, jetVz = 0.0;
                if (activeBreach != null) {
                    double spread = Math.max(0.65, activeBreach.radius() * 0.45);
                    px = activeBreach.centerX() + 0.5 + activeBreach.normalX() * 0.45 + (world.random.nextDouble() - 0.5) * spread;
                    py = activeBreach.centerY() + 0.5 + activeBreach.normalY() * 0.35 + (world.random.nextDouble() - 0.5) * spread;
                    pz = activeBreach.centerZ() + 0.5 + activeBreach.normalZ() * 0.45 + (world.random.nextDouble() - 0.5) * spread;
                    jetVx = activeBreach.normalX() * (0.055 + effectiveIntensity * 0.045);
                    jetVy = Math.max(0.02, activeBreach.normalY() * 0.04);
                    jetVz = activeBreach.normalZ() * (0.055 + effectiveIntensity * 0.045);
                } else {
                    double angle = world.random.nextDouble() * Math.PI * 2.0;
                    double radial = Math.sqrt(world.random.nextDouble()) * Math.max(1.4, v.radius() * 0.62);
                    px = center.x + Math.cos(angle) * radial;
                    py = baseHeight + (world.random.nextDouble() - 0.3) * 1.3;
                    pz = center.z + Math.sin(angle) * radial;
                }

                if (tankSmoking) {
                    world.addImportantParticle(DestructionParticles.SMOKE_CORE, true,
                            px, py + 0.3, pz,
                            jetVx + (world.random.nextDouble() - 0.5) * 0.018,
                            0.085 + effectiveIntensity * 0.040 + jetVy,
                            jetVz + (world.random.nextDouble() - 0.5) * 0.018);
                    world.addImportantParticle(DestructionParticles.SMOKE_CORE, true,
                            px + jetVx * 6.0 + (world.random.nextDouble() - 0.5) * 0.9,
                            py + 1.1 + world.random.nextDouble() * 1.2,
                            pz + jetVz * 6.0 + (world.random.nextDouble() - 0.5) * 0.9,
                            jetVx * 0.6 + (world.random.nextDouble() - 0.5) * 0.016,
                            0.095 + effectiveIntensity * 0.040,
                            jetVz * 0.6 + (world.random.nextDouble() - 0.5) * 0.016);

                    double colVy = (0.145 + effectiveIntensity * 0.065) * Math.min(1.45, hMult * 0.82);
                    world.addImportantParticle(DestructionParticles.SMOKE_COLUMN, true,
                            px + jetVx * 10.0 + (world.random.nextDouble() - 0.5) * 0.9,
                            py + 2.8 + world.random.nextDouble() * 3.8,
                            pz + jetVz * 10.0 + (world.random.nextDouble() - 0.5) * 0.9,
                            jetVx * 0.35 + (world.random.nextDouble() - 0.5) * 0.022,
                            colVy,
                            jetVz * 0.35 + (world.random.nextDouble() - 0.5) * 0.022);

                    if (world.random.nextFloat() < 0.88f) {
                        double canopyStartOffset = (7.5 + world.random.nextDouble() * 9.0) * Math.min(1.5, hMult * 0.85);
                        world.addImportantParticle(DestructionParticles.SMOKE_CANOPY, true,
                                px + jetVx * 14.0 + (world.random.nextDouble() - 0.5) * 2.2,
                                py + canopyStartOffset,
                                pz + jetVz * 14.0 + (world.random.nextDouble() - 0.5) * 2.2,
                                (world.random.nextDouble() - 0.5) * 0.045,
                                (0.115 + effectiveIntensity * 0.045) * Math.min(1.4, hMult * 0.80),
                                (world.random.nextDouble() - 0.5) * 0.045);
                    }

                    if (dist <= StructureFireManager.mediumLodDistance) {
                        double fx = px + (world.random.nextDouble() - 0.5) * 0.85;
                        double fy = py - 0.1 + world.random.nextDouble() * 1.1;
                        double fz = pz + (world.random.nextDouble() - 0.5) * 0.85;
                        world.addImportantParticle(DestructionParticles.FIRE_PLUME, true,
                                fx, fy, fz,
                                jetVx * 1.15 + (world.random.nextDouble() - 0.5) * 0.025,
                                0.055 + world.random.nextDouble() * 0.035 + jetVy,
                                jetVz * 1.15 + (world.random.nextDouble() - 0.5) * 0.025);
                        world.addParticle(ParticleTypes.FLAME, fx, fy, fz,
                                jetVx * 1.3 + (world.random.nextDouble() - 0.5) * 0.03,
                                0.045 + world.random.nextDouble() * 0.03 + jetVy,
                                jetVz * 1.3 + (world.random.nextDouble() - 0.5) * 0.03);
                    }
                    activeClientParticlesEstimate += dist <= StructureFireManager.mediumLodDistance ? 6 : 4;
                } else if (dist <= StructureFireManager.nearLodDistance) {
                    world.addImportantParticle(DestructionParticles.SMOKE_CORE, true,
                            px, py + 0.4, pz,
                            jetVx + (world.random.nextDouble() - 0.5) * 0.018,
                            0.075 + effectiveIntensity * 0.035,
                            jetVz + (world.random.nextDouble() - 0.5) * 0.018);
                    activeClientParticlesEstimate++;
                    if (v.isBurning()) {
                        world.addImportantParticle(DestructionParticles.FIRE_PLUME, true,
                                px, py - 0.1, pz,
                                jetVx + (world.random.nextDouble() - 0.5) * 0.02, 0.04, jetVz + (world.random.nextDouble() - 0.5) * 0.02);
                        world.addImportantParticle(DestructionParticles.SMOKE_COLUMN, true,
                                px, py + 1.8, pz, jetVx * 0.4, 0.11, jetVz * 0.4);
                        activeClientParticlesEstimate += 2;
                    }
                } else {
                    world.addImportantParticle(DestructionParticles.SMOKE_COLUMN, true,
                            px, py + 1.2, pz, 0.0, 0.125, 0.0);
                    activeClientParticlesEstimate++;
                }
            }

            if ((v.isBurning() || tankSmoking) && dist <= StructureFireManager.mediumLodDistance && (world.getTime() + v.structureId().hashCode()) % 22 == 0) {
                world.playSound(center.x, center.y, center.z,
                        SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS,
                        0.85f * effectiveIntensity, 0.68f + world.random.nextFloat() * 0.2f, false);
                if (isRefineryTank && world.random.nextBoolean()) {
                    world.playSound(center.x, center.y, center.z,
                            SoundEvents.BLOCK_BLASTFURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS,
                            0.95f * effectiveIntensity, 0.55f + world.random.nextFloat() * 0.2f, false);
                }
            }
        }
    }

    private static boolean electrical(DestructibleStructureType.Category cat) {
        return cat == DestructibleStructureType.Category.SUBSTATION_TRANSFORMER
                || cat == DestructibleStructureType.Category.SUBSTATION_EQUIPMENT;
    }

    private static BlockState hitMaterial(ClientWorld world, Vec3d point) {
        BlockPos center = BlockPos.ofFloored(point);
        for (BlockPos p : new BlockPos[]{center, center.down(), center.north(), center.south(), center.east(), center.west()}) {
            if (!world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
            BlockState state = world.getBlockState(p);
            if (!state.isAir() && !state.getCollisionShape(world, p).isEmpty()) return state;
        }
        return null;
    }

    private static void transientBurst(ClientWorld world, Vec3d point, DestructibleStructureType.Category cat, int count, double spread) {
        BlockState material = hitMaterial(world, point);
        for (int i = 0; i < count; i++) {
            double x = point.x + (world.random.nextDouble() - .5) * spread;
            double y = point.y + world.random.nextDouble() * .8;
            double z = point.z + (world.random.nextDouble() - .5) * spread;
            world.addParticle(ParticleTypes.POOF, x, y, z, (world.random.nextDouble() - .5) * .10, .025, (world.random.nextDouble() - .5) * .10);
            if (material != null) {
                world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, material),
                        x, y, z, (world.random.nextDouble() - .5) * .18, .06, (world.random.nextDouble() - .5) * .18);
            }
            if (electrical(cat) && i % 3 == 0) {
                world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, .06, 0);
                world.addParticle(DestructionParticles.ELECTRIC_ARC, x, y, z,
                        (world.random.nextDouble() - .5) * .12, .05, (world.random.nextDouble() - .5) * .12);
            }
        }
    }

    private static void playLocalHitEffect(MinecraftClient client, StructureNetworkSync.StructureHitPacket pkt) {
        ClientWorld world = client.world;
        if (world == null || !world.isChunkLoaded(pkt.origin().getX() >> 4, pkt.origin().getZ() >> 4)) return;
        var view = CLIENT_STRUCTURES.get(pkt.structureId());
        var cat = view != null ? view.category() : inferCategory(pkt.structureId());
        transientBurst(world, pkt.impactPoint(), cat, pkt.oldStage() == pkt.newStage() ? 12 : 18, 2.5);
    }

    private static void playTransitionBurst(MinecraftClient client, String id, BlockPos origin,
                                            StructureDamageState oldStage, StructureDamageState newStage) {
        ClientWorld world = client.world;
        if (world == null || !world.isChunkLoaded(origin.getX() >> 4, origin.getZ() >> 4)) return;
        var view = CLIENT_STRUCTURES.get(id);
        var cat = view != null ? view.category() : inferCategory(id);
        transientBurst(world, Vec3d.ofCenter(origin).add(0, .5, 0), cat, 28,
                cat == DestructibleStructureType.Category.BRIDGE_SECTION ? 8 : 5);
    }

    private static DestructibleStructureType.Category inferCategory(String id) {
        if (id == null) return DestructibleStructureType.Category.REFINERY_TANK;
        if (id.contains("bridge")) return DestructibleStructureType.Category.BRIDGE_SECTION;
        if (id.contains("transformer")) return DestructibleStructureType.Category.SUBSTATION_TRANSFORMER;
        if (id.contains("substation")) return DestructibleStructureType.Category.SUBSTATION_EQUIPMENT;
        return DestructibleStructureType.Category.REFINERY_TANK;
    }

    public static Map<String, StructureNetworkSync.ClientStructureView> clientStructures() {
        return Collections.unmodifiableMap(CLIENT_STRUCTURES);
    }

    public static int activeClientParticlesEstimate() {
        return activeClientParticlesEstimate;
    }
}
