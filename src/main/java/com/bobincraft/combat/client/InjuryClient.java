package com.bobincraft.combat.client;

import com.bobincraft.combat.GuardInjuryPose;
import com.bobincraft.combat.HitRegion;
import com.bobincraft.combat.InjurySeverity;
import com.bobincraft.combat.InjurySystem;
import com.bobincraft.combat.InjuryCarrier;
import com.bobincraft.combat.InjuryState;
import com.bobincraft.combat.PlayerInjuries;
import com.bobincraft.raid.client.GuardWoundFeature;
import com.bobincraft.raid.client.PlayerWoundFeature;
import com.bobincraft.raid.client.WarzoneGuardModel;
import com.bobincraft.raid.client.WarzoneGuardRenderer;
import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.*;

/**
 * Injury HUD, permanent textured enemy corpses preserving severed limbs/head,
 * and detached textured limbs & heads flying off and lying permanently on the ground.
 */
public final class InjuryClient {
    private static final net.minecraft.client.render.VertexConsumerProvider.Immediate WORLD_BUFFERS =
            net.minecraft.client.render.VertexConsumerProvider.immediate(new net.minecraft.client.render.BufferBuilder(131072));
    private static final Identifier DEFAULT_SKIN = new Identifier("textures/entity/player/wide/steve.png");
    private static final int MAX_CORPSES = 512;
    private static final int MAX_SEVERED_PARTS = 512;
    private static final int[] PART_MASKS = {
        HitRegion.LEFT_ARM.limbMask,
        HitRegion.RIGHT_ARM.limbMask,
        HitRegion.LEFT_LEG.limbMask,
        HitRegion.RIGHT_LEG.limbMask,
        HitRegion.HEAD_SEVERED
    };

    private static int severity, remainingTicks, faceSeverity;
    private static float tint;
    private static boolean countdownRunning;
    private static ClientWorld trackedWorld;

    private static final class Corpse {
        final UUID entityId; final boolean permanentGuard; final Identifier skin;
        Vec3d pos; float bodyYaw; int packedMissing; boolean backpack; final long startTick;
        double prevY, vy; boolean settled; boolean blast;
        Corpse(UUID entityId, boolean permanentGuard, Identifier skin, Vec3d pos, float bodyYaw,
               int packedMissing, boolean backpack, long startTick, boolean blast) {
            this.entityId = entityId; this.permanentGuard = permanentGuard; this.skin = skin; this.pos = pos;
            this.prevY = pos.y; this.bodyYaw = bodyYaw; this.packedMissing = packedMissing;
            this.backpack = backpack; this.startTick = startTick; this.blast = blast;
        }
    }

    private static final class SeveredPart {
        final Identifier skin;
        final int partType; // 0=leftArm, 1=rightArm, 2=leftLeg, 3=rightLeg, 4=head
        double x, y, z, prevX, prevY, prevZ;
        double vx, vy, vz;
        float yaw, pitch, roll;
        float spinYaw, spinPitch, spinRoll;
        boolean grounded, dead, blast;
        Identifier capTex = GuardWoundFeature.TEXTURE;
        int age;

        SeveredPart(Identifier skin, int partType, Vec3d pos, Vec3d vel, float yaw, Random rng, boolean blast) {
            this.skin = skin;
            this.blast = blast;
            this.partType = partType;
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.vx = vel.x;
            this.vy = vel.y;
            this.vz = vel.z;
            this.yaw = yaw;
            this.pitch = partType == 4 ? (rng.nextFloat() - 0.5f) * 35f : 25f;
            this.roll = (rng.nextFloat() - 0.5f) * 40f;
            this.spinYaw = (rng.nextFloat() - 0.5f) * 22f;
            this.spinPitch = (partType == 4 ? 18f : 26f) * (rng.nextBoolean() ? 1f : -1f);
            this.spinRoll = (rng.nextFloat() - 0.5f) * 18f;
        }
    }

    private static final List<Corpse> CORPSES = new ArrayList<>();
    private static final List<SeveredPart> SEVERED_PARTS = new ArrayList<>();
    private static final Set<UUID> DEAD_SEEN = new HashSet<>();
    private static final Map<UUID, Integer> LAST_MISSING = new HashMap<>();
    /** Bodies that lost several parts in one instant (shell/grenade burst) - they get scorched visuals. */
    private static final Set<UUID> BLASTED = new HashSet<>();

    public static boolean blastLike(UUID id, int missing) {
        return BLASTED.contains(id) || GuardInjuryPose.deathPose(missing) == GuardInjuryPose.POSE_BLAST;
    }
    private static WarzoneGuardModel corpseModel;
    private static ModelPart[] corpseCaps;

    private InjuryClient() { }

    private static void ensureModels() {
        if (corpseModel == null) {
            corpseModel = new WarzoneGuardModel(WarzoneGuardModel.getTexturedModelData().createModel());
            ModelPart root = GuardWoundFeature.modelData().createModel();
            corpseCaps = new ModelPart[5];
            for (int i = 0; i < 5; i++) corpseCaps[i] = root.getChild("cap" + i);
        }
    }

    private static Identifier skinOf(PlayerEntity p) {
        try {
            if (PlayerWoundFeature.PLAYER_RENDERER != null) {
                Identifier tex = PlayerWoundFeature.PLAYER_RENDERER.getTexture(p);
                if (tex != null) return tex;
            }
        } catch (Throwable ignored) { }
        return DEFAULT_SKIN;
    }

    /**
     * Top of the first solid surface at or below (x,y,z), searching up to {@code depth} blocks down.
     * Returns NaN when there is no floor in range (so callers keep falling instead of "landing" in mid-air).
     */
    private static double groundHeightAt(ClientWorld world, double x, double y, double z, int depth) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z), by = MathHelper.floor(y);
        for (int checkY = by + 1; checkY >= by - depth; checkY--) {
            BlockPos pos = new BlockPos(bx, checkY, bz);
            if (!world.isChunkLoaded(pos)) continue;
            BlockState st = world.getBlockState(pos);
            if (st.isAir()) continue;
            VoxelShape shape = st.getCollisionShape(world, pos);
            if (!shape.isEmpty()) {
                double top = checkY + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                if (top <= y + 0.65) return top;
            }
        }
        return Double.NaN;
    }

    private static void checkAndSpawnSeveredParts(UUID id, Identifier skin, Vec3d basePos, float bodyYaw, boolean prone, int rawMissing) {
        int prev = LAST_MISSING.getOrDefault(id, 0);
        int newlySevered = (rawMissing & ~prev) & HitRegion.ALL_SEVERABLE;
        LAST_MISSING.put(id, rawMissing & HitRegion.ALL_SEVERABLE);
        if (newlySevered == 0 || basePos.y < -500) return;
        // Several parts gone at the same instant = explosion, not a clean bullet wound.
        boolean blast = Integer.bitCount(newlySevered) >= 2;
        if (blast) BLASTED.add(id);

        Random rng = new Random(id.getLeastSignificantBits() ^ (long) newlySevered * 0x9E3779B97F4A7C15L ^ System.nanoTime());
        double rad = Math.toRadians(bodyYaw);
        double rightX = -Math.cos(rad), rightZ = -Math.sin(rad);
        double fwdX = -Math.sin(rad), fwdZ = Math.cos(rad);

        for (int partType = 0; partType < 5; partType++) {
            if ((newlySevered & PART_MASKS[partType]) == 0) continue;
            double sideSign = (partType == 0 || partType == 2) ? -1.0 : (partType == 1 || partType == 3) ? 1.0 : 0.0;
            double hOff = prone ? 0.25 : (partType == 4 ? 1.52 : (partType < 2 ? 1.15 : 0.55));
            double sideOff = sideSign * (partType < 2 ? 0.32 : 0.16);
            Vec3d spawnPos = new Vec3d(
                basePos.x + rightX * sideOff + fwdX * (partType == 4 ? 0.08 : 0.0),
                basePos.y + hOff,
                basePos.z + rightZ * sideOff + fwdZ * (partType == 4 ? 0.08 : 0.0)
            );
            double outSpeed = (partType == 4 ? 0.18 : 0.15) + rng.nextDouble() * 0.12;
            double outAngle = rad + sideSign * (0.9 + rng.nextDouble() * 0.5) + (rng.nextDouble() - 0.5) * 0.8;
            double vy = (partType == 4 ? 0.24 : 0.16) + rng.nextDouble() * 0.10;
            Vec3d vel = new Vec3d(-Math.sin(outAngle) * outSpeed, vy, Math.cos(outAngle) * outSpeed);
            if (SEVERED_PARTS.size() >= MAX_SEVERED_PARTS) SEVERED_PARTS.remove(0);
            SeveredPart part = new SeveredPart(skin, partType, spawnPos, vel, bodyYaw + (rng.nextFloat() - 0.5f) * 70f, rng, blast);
            part.capTex = GuardWoundFeature.textureFor(new UUID(rng.nextLong(), rng.nextLong()), blast);
            SEVERED_PARTS.add(part);
        }
    }

    private static void tickSeveredParts(ClientWorld world) {
        for (SeveredPart p : SEVERED_PARTS) {
            p.prevX = p.x; p.prevY = p.y; p.prevZ = p.z;
            p.age++;
            if (p.grounded) {
                // Ground can disappear later (crater, broken block): re-check once in a while.
                if (p.age % 10 != 0) continue;
                double f = groundHeightAt(world, p.x, p.y + 0.1, p.z, 12);
                if (!Double.isNaN(f) && p.y <= f + 0.05) continue;
                p.grounded = false;
            }
            p.vy = Math.max(p.vy - 0.042, -0.6);
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            p.vx *= 0.94;
            p.vz *= 0.94;
            p.yaw = MathHelper.wrapDegrees(p.yaw + p.spinYaw);
            p.pitch = MathHelper.wrapDegrees(p.pitch + p.spinPitch);
            p.roll = MathHelper.wrapDegrees(p.roll + p.spinRoll);

            double floorY = groundHeightAt(world, p.x, p.y, p.z, 40);
            if (Double.isNaN(floorY)) {
                // No floor below: keep falling (never "land" in the air); drop it if it left the world.
                if (p.y < world.getBottomY() - 16) p.dead = true;
                continue;
            }
            if (p.y <= floorY + 0.02) {
                p.y = floorY;
                if (Math.abs(p.vy) > 0.09) {
                    p.vy = -p.vy * 0.30;
                    p.vx *= 0.55;
                    p.vz *= 0.55;
                    p.spinYaw *= 0.5f;
                    p.spinPitch *= 0.5f;
                    p.spinRoll *= 0.5f;
                } else {
                    p.vy = 0; p.vx = 0; p.vz = 0;
                    p.grounded = true;
                    if (p.partType < 4) {
                        p.pitch = 90f;
                        p.roll = 0f;
                    } else {
                        p.pitch = MathHelper.clamp(p.pitch, -22f, 22f);
                        p.roll = MathHelper.clamp(p.roll, -18f, 18f);
                    }
                }
            }
        }
        SEVERED_PARTS.removeIf(p -> p.dead);
    }

    /** Corpses fall to the ground if their owner died in the air, and re-settle if the ground below is removed. */
    private static void settleCorpses(ClientWorld world) {
        long now = world.getTime();
        for (Corpse c : CORPSES) {
            c.prevY = c.pos.y;
            if (c.settled && (now + c.startTick) % 20 != 0) continue;
            double floor = groundHeightAt(world, c.pos.x, c.pos.y + 0.1, c.pos.z, 40);
            if (Double.isNaN(floor)) {
                // Unloaded or bottomless: leave it where it is rather than dropping it into the void.
                c.vy = 0;
                continue;
            }
            if (c.pos.y > floor + 0.02) {
                c.settled = false;
                c.vy = Math.max(c.vy - 0.08, -1.2);
                c.pos = new Vec3d(c.pos.x, Math.max(floor, c.pos.y + c.vy), c.pos.z);
            } else {
                c.pos = new Vec3d(c.pos.x, floor, c.pos.z);
                c.vy = 0;
                c.settled = true;
            }
        }
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(InjurySystem.SYNC, (client, handler, buf, sender) -> {
            if (buf.readableBytes() != 8) return;
            int incoming = buf.readUnsignedByte(), ticks = buf.readInt(), face = buf.readUnsignedByte();
            int rawMask = buf.readUnsignedByte();
            int missing = rawMask & HitRegion.ALL_SEVERABLE;
            int dPose = (rawMask >> 4) & 3;
            boolean running = buf.readBoolean();
            client.execute(() -> {
                severity = InjurySeverity.fromId(incoming).ordinal();
                remainingTicks = MathHelper.clamp(ticks, 0, InjurySeverity.fromId(severity).durationTicks);
                faceSeverity = MathHelper.clamp(face, 0, severity);
                countdownRunning = running;
                if (client.player != null) {
                    PlayerInjuries.setDeathPose(((PlayerEntity) client.player).getUuid(), dPose);
                    if (client.player instanceof InjuryCarrier carrier)
                        carrier.bobinInjuries().restore(new InjuryState.Snapshot(severity, remainingTicks, faceSeverity, missing));
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset(); CORPSES.clear(); SEVERED_PARTS.clear(); DEAD_SEEN.clear(); LAST_MISSING.clear(); BLASTED.clear(); trackedWorld = null;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != trackedWorld) {
                CORPSES.clear(); SEVERED_PARTS.clear(); DEAD_SEEN.clear(); LAST_MISSING.clear(); BLASTED.clear();
                trackedWorld = client.world;
            }
            if (client.world != null && !client.isPaused()) {
                long now = client.world.getTime();
                // Player corpses expire after CORPSE_TICKS; enemy guard corpses are permanent (permanentGuard == true)
                CORPSES.removeIf(c -> !c.permanentGuard && (now - c.startTick >= GuardInjuryPose.CORPSE_TICKS || now < c.startTick));

                // Track enemy guards: spawn flying severed limbs/heads and record permanent corpses on death
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof WarzoneGuardEntity g && !g.isHubGuard()) {
                        UUID id = g.getUuid();
                        Identifier skin = WarzoneGuardRenderer.textureFor(g);
                        int packed = g.missingLimbs();
                        int rawMissing = packed & HitRegion.ALL_SEVERABLE;
                        Vec3d curPos = g.getPos();
                        checkAndSpawnSeveredParts(id, skin, curPos, g.getBodyYaw(), g.isInjuryProne() || !g.isAlive(), rawMissing);

                        if (g.isAlive()) {
                            DEAD_SEEN.remove(id);
                        } else {
                            boolean backpack = Math.floorMod(g.appearanceVariant(), 3) == 2;
                            double floorY = curPos.y > -500 ? groundHeightAt(client.world, curPos.x, curPos.y, curPos.z, 40) : curPos.y;
                            if (Double.isNaN(floorY)) floorY = curPos.y;
                            // Start at the entity's own height if it died airborne; settleCorpses() lets it fall to the floor.
                            Vec3d groundPos = curPos.y > -500 ? new Vec3d(curPos.x, Math.max(floorY, curPos.y), curPos.z) : curPos;
                            if (!DEAD_SEEN.contains(id)) {
                                // Never create a corpse from a parked/teleported entity position.
                                if (curPos.y > -500) {
                                    DEAD_SEEN.add(id);
                                    if (CORPSES.size() >= MAX_CORPSES) CORPSES.remove(0);
                                    CORPSES.add(new Corpse(id, true, skin, groundPos, g.getBodyYaw(), packed, backpack, now,
                                            blastLike(id, packed)));
                                }
                            } else {
                                // The corpse position/yaw/pose are frozen at creation. The dead entity is parked at
                                // y=-1000 and then lerped back by server position packets; following it made the
                                // corpse jump underground and back (visible flicker for as long as the entity lived).
                                for (int i = CORPSES.size() - 1; i >= 0; i--) {
                                    Corpse c = CORPSES.get(i);
                                    if (c.entityId.equals(id)) {
                                        int limbs = (c.packedMissing | packed) & HitRegion.ALL_SEVERABLE;
                                        int pose = GuardInjuryPose.deathPose(c.packedMissing) != 0
                                                ? GuardInjuryPose.deathPose(c.packedMissing) : GuardInjuryPose.deathPose(packed);
                                        c.packedMissing = GuardInjuryPose.pack(limbs, pose);
                                        c.backpack = backpack;
                                        c.blast |= blastLike(id, c.packedMissing);
                                        break;
                                    }
                                }
                            }
                            if (g.deathTime >= 2 && curPos.y > -500) {
                                ((Entity) g).setPosition(curPos.x, -1000.0, curPos.z);
                            }
                        }
                    }
                }

                // Track players: spawn severed limbs/heads and record temporary player corpse
                for (PlayerEntity p : ((World) client.world).getPlayers()) {
                    UUID id = p.getUuid();
                    Identifier skin = skinOf(p);
                    int packed = PlayerInjuries.missing(p);
                    int rawMissing = packed & HitRegion.ALL_SEVERABLE;
                    Vec3d curPos = p.getPos();
                    checkAndSpawnSeveredParts(id, skin, curPos, p.getYaw(), PlayerInjuries.prone(p) || !p.isAlive(), rawMissing);

                    if (p.isAlive()) {
                        DEAD_SEEN.remove(id);
                    } else {
                        if (DEAD_SEEN.add(id)) {
                            if (CORPSES.size() >= MAX_CORPSES) CORPSES.remove(0);
                            CORPSES.add(new Corpse(id, false, skin, curPos, p.getYaw(), packed, false, now, blastLike(id, packed)));
                        } else {
                            for (int i = CORPSES.size() - 1; i >= 0; i--) {
                                Corpse c = CORPSES.get(i);
                                if (c.entityId.equals(id) && now - c.startTick < 10) {
                                    Vec3d savedPos = curPos.y > -500 ? curPos : c.pos;
                                    CORPSES.set(i, new Corpse(id, false, skin, savedPos, c.bodyYaw, c.packedMissing | packed, false, c.startTick, c.blast || blastLike(id, packed)));
                                    break;
                                }
                            }
                        }
                        if (p.getY() > -500) {
                            ((Entity) p).setPosition(p.getX(), -1000.0, p.getZ());
                        }
                    }
                }

                tickSeveredParts(client.world);
                settleCorpses(client.world);
            }
            if (client.player == null || !client.player.isAlive()) { reset(); return; }
            if (!client.isPaused() && countdownRunning && remainingTicks > 0) remainingTicks--;
            float target = client.player.isSpectator() ? 0 : faceSeverity * 0.055f;
            tint += (target - tint) * 0.20f;
        });

        WorldRenderEvents.LAST.register(ctx -> {
            var client = MinecraftClient.getInstance();
            if (client.world == null || (CORPSES.isEmpty() && SEVERED_PARTS.isEmpty())) return;
            var matrices = ctx.matrixStack();
            var consumers = WORLD_BUFFERS;
            if (matrices == null) return;
            ensureModels();
            Vec3d cam = ctx.camera().getPos();
            float delta = ctx.tickDelta();

            // 1. Render permanent enemy corpses and player corpses as opaque textured bodies with missing limbs/head & wound caps
            for (Corpse c : CORPSES) {
                if (c.pos.squaredDistanceTo(cam) > 128 * 128) continue;
                double cy = MathHelper.lerp(delta, c.prevY, c.pos.y);
                int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(c.pos.x, cy + 0.2, c.pos.z));
                int dPose = GuardInjuryPose.deathPose(c.packedMissing);
                if (dPose == 0) dPose = GuardInjuryPose.POSE_FALLEN;
                int mask = GuardInjuryPose.pack(c.packedMissing & HitRegion.ALL_SEVERABLE, dPose);

                corpseModel.backpack.visible = c.backpack;
                corpseModel.head.pitch = corpseModel.head.yaw = corpseModel.head.roll = 0;
                corpseModel.body.pitch = corpseModel.body.yaw = corpseModel.body.roll = 0;
                corpseModel.leftArm.pitch = corpseModel.leftArm.yaw = corpseModel.leftArm.roll = 0;
                corpseModel.rightArm.pitch = corpseModel.rightArm.yaw = corpseModel.rightArm.roll = 0;
                corpseModel.leftLeg.pitch = corpseModel.leftLeg.yaw = corpseModel.leftLeg.roll = 0;
                corpseModel.rightLeg.pitch = corpseModel.rightLeg.yaw = corpseModel.rightLeg.roll = 0;
                corpseModel.applyInjuryPose(mask, 1f, 0f, 0f);

                boolean blast = c.blast || dPose == GuardInjuryPose.POSE_BLAST;
                if (c.settled) renderGround(matrices, consumers, c, mask, dPose, blast, cam, light, cy);

                matrices.push();
                matrices.translate(c.pos.x - cam.x, cy - cam.y, c.pos.z - cam.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - c.bodyYaw));
                if (dPose == GuardInjuryPose.POSE_BLAST) {
                    matrices.translate(0, 0.24, -0.85);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                } else {
                    matrices.translate(0, 0.26, 0.85);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                }
                matrices.scale(-1.0F, -1.0F, 1.0F);
                matrices.scale(0.9375F, 0.9375F, 0.9375F);
                matrices.translate(0.0F, -1.501F, 0.0F);

                var bodyBuf = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(c.skin));
                if (blast) corpseModel.renderCorpse(matrices, bodyBuf, light, OverlayTexture.DEFAULT_UV, 0.55f, 0.52f, 0.50f, 1.0f);
                else corpseModel.renderCorpse(matrices, bodyBuf, light, OverlayTexture.DEFAULT_UV, 1.0f);

                ModelPart[] limbs = {corpseModel.leftArm, corpseModel.rightArm, corpseModel.leftLeg, corpseModel.rightLeg};
                var capBuf = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GuardWoundFeature.textureFor(c.entityId, blast)));
                for (int i = 0; i < 4; i++) {
                    if ((mask & (1 << i)) != 0) {
                        corpseCaps[i].copyTransform(limbs[i]);
                        corpseCaps[i].visible = true;
                        corpseCaps[i].render(matrices, capBuf, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1.0f);
                    }
                }
                if ((mask & HitRegion.HEAD_SEVERED) != 0) {
                    corpseCaps[4].copyTransform(corpseModel.body);
                    corpseCaps[4].visible = true;
                    corpseCaps[4].render(matrices, capBuf, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1.0f);
                }
                matrices.pop();
            }

            // 2. Render severed heads, arms, and legs flying and lying permanently on the ground as textured parts
            for (SeveredPart sp : SEVERED_PARTS) {
                double lx = MathHelper.lerp(delta, sp.prevX, sp.x);
                double ly = MathHelper.lerp(delta, sp.prevY, sp.y);
                double lz = MathHelper.lerp(delta, sp.prevZ, sp.z);
                if (cam.squaredDistanceTo(lx, ly, lz) > 128 * 128) continue;
                int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(lx, ly + 0.2, lz));

                matrices.push();
                double yLift = sp.partType == 4 ? 0.02 : 0.13;
                matrices.translate(lx - cam.x, ly - cam.y + yLift, lz - cam.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - sp.yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sp.pitch));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sp.roll));
                matrices.scale(-0.9375F, -0.9375F, 0.9375F);

                // IMPORTANT: Immediate#getBuffer() flushes the previously requested layer. Never hold two
                // buffers at once: request each one right before drawing into it, and finish it first.
                var skinLayer = RenderLayer.getEntityCutoutNoCull(sp.skin);
                var capLayer = RenderLayer.getEntityCutoutNoCull(sp.capTex);
                float tr = sp.blast ? 0.55f : 1f, tg = sp.blast ? 0.52f : 1f, tb = sp.blast ? 0.50f : 1f;

                if (sp.partType == 4) {
                    // Severed head + helmet/hat + neck cut cap
                    corpseModel.head.visible = true;
                    corpseModel.hat.visible = true;
                    corpseModel.head.pivotX = corpseModel.head.pivotY = corpseModel.head.pivotZ = 0f;
                    corpseModel.head.pitch = corpseModel.head.yaw = corpseModel.head.roll = 0f;
                    corpseModel.hat.copyTransform(corpseModel.head);
                    var partBuf = consumers.getBuffer(skinLayer);
                    corpseModel.head.render(matrices, partBuf, light, OverlayTexture.DEFAULT_UV, tr, tg, tb, 1.0f);
                    corpseModel.hat.render(matrices, partBuf, light, OverlayTexture.DEFAULT_UV, tr, tg, tb, 1.0f);
                    corpseCaps[4].pivotX = corpseCaps[4].pivotY = corpseCaps[4].pivotZ = 0f;
                    corpseCaps[4].pitch = corpseCaps[4].yaw = corpseCaps[4].roll = 0f;
                    corpseCaps[4].visible = true;
                    var capBuf = consumers.getBuffer(capLayer);
                    corpseCaps[4].render(matrices, capBuf, light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1.0f);
                } else {
                    ModelPart[] limbs = {corpseModel.leftArm, corpseModel.rightArm, corpseModel.leftLeg, corpseModel.rightLeg};
                    ModelPart limb = limbs[sp.partType];
                    float origX = limb.pivotX, origY = limb.pivotY, origZ = limb.pivotZ;
                    limb.visible = true;
                    limb.pivotX = 0f; limb.pivotY = -5.0f; limb.pivotZ = 0f;
                    limb.pitch = limb.yaw = limb.roll = 0f;
                    limb.render(matrices, consumers.getBuffer(skinLayer), light, OverlayTexture.DEFAULT_UV, tr, tg, tb, 1.0f);
                    corpseCaps[sp.partType].copyTransform(limb);
                    corpseCaps[sp.partType].visible = true;
                    corpseCaps[sp.partType].render(matrices, consumers.getBuffer(capLayer), light, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1.0f);
                    limb.pivotX = origX; limb.pivotY = origY; limb.pivotZ = origZ;
                }
                matrices.pop();
            }
            consumers.draw();
        });
        HudRenderCallback.EVENT.register(InjuryClient::render);
    }

    private static final Identifier[] POOLS = {
        new Identifier("bobin_craft", "textures/entity/wound/pool_0.png"),
        new Identifier("bobin_craft", "textures/entity/wound/pool_1.png"),
        new Identifier("bobin_craft", "textures/entity/wound/pool_2.png")
    };

    /** Blood pool under every body, bigger with more lost parts. */
    private static void renderGround(MatrixStack matrices, VertexConsumerProvider consumers, Corpse c, int mask,
                                     int dPose, boolean blast, Vec3d cam, int light, double cy) {
        long h = c.entityId.getLeastSignificantBits() * 0x9E3779B97F4A7C15L;
        int lost = Integer.bitCount(mask & HitRegion.ALL_SEVERABLE);
        float rot = (h & 255) * 360f / 256f;
        // Torso centre in the corpse frame (see the body transform in the render loop).
        double zOff = dPose == GuardInjuryPose.POSE_BLAST ? 0.2 : -0.2;
        matrices.push();
        matrices.translate(c.pos.x - cam.x, cy - cam.y, c.pos.z - cam.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - c.bodyYaw));
        matrices.translate(0, 0, zOff);
        float pool = 0.42f + 0.20f * lost + (((h >> 12) & 7) * 0.03f) + ((mask & HitRegion.HEAD_SEVERED) != 0 ? 0.18f : 0f);
        decal(matrices, consumers, POOLS[(int) ((h >>> 20) % 3)], light, 0.028, Math.min(1.5f, pool), rot + 90f);
        matrices.pop();
    }

    private static void decal(MatrixStack matrices, VertexConsumerProvider consumers, Identifier tex, int light,
                              double lift, float half, float rotDeg) {
        matrices.push();
        matrices.translate(0, lift, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotDeg));
        var entry = matrices.peek();
        var pm = entry.getPositionMatrix();
        var nm = entry.getNormalMatrix();
        var b = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
        b.vertex(pm, -half, 0, -half).color(255, 255, 255, 255).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        b.vertex(pm, -half, 0,  half).color(255, 255, 255, 255).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        b.vertex(pm,  half, 0,  half).color(255, 255, 255, 255).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        b.vertex(pm,  half, 0, -half).color(255, 255, 255, 255).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nm, 0, 1, 0).next();
        matrices.pop();
    }

    private static void reset() { severity = remainingTicks = faceSeverity = 0; tint = 0; countdownRunning = false; }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator()) return;
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        if (tint > 0.002f) {
            float pulse = 1 + 0.07f * (float) Math.sin((client.player.age + tickDelta) * 0.13);
            int alpha = MathHelper.clamp((int) (tint * pulse * 255), 0, 70);
            context.fill(0, 0, w, h, alpha << 24 | 0x8E0805);
            int edge = Math.min(190, alpha * 3) << 24 | 0x6E0000;
            context.fillGradient(0, 0, w, h / 4, edge, 0x006E0000);
            context.fillGradient(0, h * 3 / 4, w, h, 0x006E0000, edge);
        }
        if (severity == 0 || client.options.hudHidden) return;
        int seconds = Math.max(0, (remainingTicks + 19) / 20);
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        Text label = Text.translatable("injury.bobin_craft.countdown", time);
        int x = (w - client.textRenderer.getWidth(label)) / 2;
        context.fill(x - 5, 6, w - x + 5, 34, 0xAA190604);
        context.drawTextWithShadow(client.textRenderer, label, x, 9, 0xFF7469);
        Text help = Text.translatable("injury.bobin_craft.treatment_hint");
        context.drawTextWithShadow(client.textRenderer, help, (w - client.textRenderer.getWidth(help)) / 2, 22, 0xFFE4CE);
    }
}
