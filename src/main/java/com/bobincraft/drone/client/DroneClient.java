package com.bobincraft.drone.client;

import com.bobincraft.drone.DroneContent;
import com.bobincraft.drone.DroneEntity;
import com.bobincraft.drone.DroneNetworking;
import com.bobincraft.drone.DroneType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client presentation and safe dual-zone camera/chunk streaming handoff. */
@Environment(EnvType.CLIENT)
public final class DroneClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("bobin_craft/drone_client");
    private static int droneId = -1;
    private static UUID droneUuid;
    private static int sequence;
    private static int missingTicks;
    private static DroneEntity drone;
    private static Entity previousCamera;
    private static ClientWorld controlWorld;
    private static DroneScreen controlScreen;
    private static boolean screenSuspended;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;
    private static float previousBank;
    private static float bank;
    private static float previousLookPitch;
    private static float lookPitch;
    private static boolean lookPitchInitialized;
    private static float speed;
    private static Vec3d previousDronePosition;
    private static DroneRotorSound rotorSound;
    private static final Map<UUID, DroneRotorSound> observerSounds = new HashMap<>();
    private static ClientWorld observerWorld;
    private static int observerScanTicks;

    /** Protected 5x5 local chunk anchor around the operator's stationary body. */
    private static volatile ChunkPos protectedPlayerChunk;
    private static volatile Vec3d protectedPlayerPos;
    private static volatile boolean returningToPlayer;
    private static int returnSafetyTicks;

    private static final int MAX_REPAIR_RADIUS = 8;
    private static final int REPAIR_SCAN_PER_PASS = 121;
    private static final int REPAIR_MAX_BATCH = 8;
    private static final long[] REPAIR_OFFSETS;
    private static int repairCursor;
    private static int repairTicks;

    static {
        long[] packed = new long[(2 * MAX_REPAIR_RADIUS + 1) * (2 * MAX_REPAIR_RADIUS + 1)];
        int count = 0;
        for (int x = -MAX_REPAIR_RADIUS; x <= MAX_REPAIR_RADIUS; x++) {
            for (int z = -MAX_REPAIR_RADIUS; z <= MAX_REPAIR_RADIUS; z++) {
                int distance = x * x + z * z;
                if (distance <= MAX_REPAIR_RADIUS * MAX_REPAIR_RADIUS) {
                    packed[count++] = ((long) distance << 16) | ((long) (x + 128) << 8) | (long) (z + 128);
                }
            }
        }
        Arrays.sort(packed, 0, count);
        REPAIR_OFFSETS = Arrays.copyOf(packed, count);
    }

    private DroneClient() { }

    public static void register() {
        EntityRendererRegistry.register(DroneContent.FPV_DRONE, DroneRenderer::new);
        EntityRendererRegistry.register(DroneContent.BOMBER_DRONE, DroneRenderer::new);
        EntityRendererRegistry.register(DroneContent.HEAVY_DRONE, DroneRenderer::new);
        EntityRendererRegistry.register(DroneContent.FIXED_WING_DRONE, DroneRenderer::new);
        EntityRendererRegistry.register(DroneContent.DRONE_PAYLOAD, DronePayloadRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(DroneNetworking.SESSION_ID, (client, handler, buffer, sender) -> {
            int id = buffer.readInt();
            UUID uuid = buffer.readUuid();
            client.execute(() -> {
                if (id < 0) endControl(false, "server_session_closed");
                else beginControl(client, id, uuid);
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(DroneClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DroneClient::tickObserverSounds);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            endControl(false, "disconnected");
            returningToPlayer = false;
            returnSafetyTicks = 0;
            protectedPlayerChunk = null;
            protectedPlayerPos = null;
            clearObserverSounds(client);
            observerWorld = null;
        });
    }

    private static void beginControl(MinecraftClient client, int id, UUID uuid) {
        if (client.player == null || client.world == null) return;
        if (isActive()) endControl(false, "session_replaced");
        returningToPlayer = false;
        returnSafetyTicks = 0;
        protectedPlayerPos = client.player.getPos();
        protectedPlayerChunk = new ChunkPos(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4);
        // Trigger a chunk map center touch while still at the player so ClientChunkManagerDroneMixin
        // snapshots all 5x5 local chunks around the player's body before the drone shifts the center away.
        client.world.getChunkManager().setChunkMapCenter(protectedPlayerChunk.x, protectedPlayerChunk.z);

        previousCamera = client.getCameraEntity();
        controlWorld = client.world;
        previousPerspective = client.options.getPerspective();
        droneId = id;
        droneUuid = uuid;
        sequence = 0;
        missingTicks = 0;
        repairCursor = 0;
        repairTicks = 0;
        drone = null;
        previousDronePosition = null;
        speed = 0;
        bank = previousBank = 0;
        lookPitch = previousLookPitch = 0;
        lookPitchInitialized = false;
        screenSuspended = false;
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        controlScreen = new DroneScreen();
        client.setScreen(controlScreen);
        client.inGameHud.setOverlayMessage(Text.empty(), false);
        LOGGER.info("Drone control opened: id={} uuid={}", id, uuid);
    }

    private static void tick(MinecraftClient client) {
        if (returningToPlayer) {
            tickReturnSafety(client);
        }
        if (!isActive()) return;
        if (client.player == null || client.world == null || client.world != controlWorld || !client.player.isAlive()) {
            endControl(true, "operator_or_world_unavailable");
            return;
        }
        // Keep the player body completely stationary while operating a drone so client physics can never drift off edges
        if (protectedPlayerPos != null && client.player.squaredDistanceTo(protectedPlayerPos) < 4.0) {
            client.player.setVelocity(Vec3d.ZERO);
        }
        protectedPlayerChunk = new ChunkPos(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4);

        if (client.currentScreen == null && controlScreen != null) client.setScreen(controlScreen);
        if (client.currentScreen != controlScreen) {
            if (!screenSuspended) {
                screenSuspended = true;
                controlScreen.suspendControls();
                LOGGER.info("Drone input suspended: uuid={} overlay={}", droneUuid,
                        client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName());
            }
            controlScreen.sendSuspendedHeartbeat();
            return;
        }
        if (screenSuspended) {
            screenSuspended = false;
            LOGGER.info("Drone input resumed: uuid={}", droneUuid);
        }
        Entity entity = client.world.getEntityById(droneId);
        if (!(entity instanceof DroneEntity current) || !entity.getUuid().equals(droneUuid)) {
            drone = null;
            int flags = controlScreen != null ? controlScreen.drainQueuedActions() : 0;
            sendControls(0, 0, 0, 0.5f, 0, flags);
            if (++missingTicks > 80) {
                client.player.sendMessage(Text.translatable("gui.bobin_craft.drone.link.missing"), true);
                endControl(true, "entity_tracking_timeout");
            }
            return;
        }
        drone = current;
        missingTicks = 0;
        if (!drone.isAlive()) { endControl(false, "drone_removed"); return; }

        if (client.getCameraEntity() != drone) {
            client.setCameraEntity(drone);
            if (client.worldRenderer != null) {
                client.worldRenderer.scheduleTerrainUpdate();
            }
        }
        probeMissingChunks(client);
        client.player.input.movementForward = 0;
        client.player.input.movementSideways = 0;
        client.player.input.jumping = false;
        client.player.input.sneaking = false;
        previousBank = bank;
        bank = drone.getBankAngle();
        if (!lookPitchInitialized) {
            previousLookPitch = lookPitch = drone.getLookPitch();
            lookPitchInitialized = true;
        } else {
            previousLookPitch = lookPitch;
            lookPitch = drone.getLookPitch();
        }
        if (previousDronePosition != null) {
            float measured = (float) drone.getPos().distanceTo(previousDronePosition) * 20;
            speed = MathHelper.lerp(0.35f, speed, measured);
        }
        previousDronePosition = drone.getPos();
        if (!drone.isArmed() && rotorSound != null) {
            client.getSoundManager().stop(rotorSound);
            rotorSound = null;
        } else if (drone.isArmed() && (rotorSound == null || rotorSound.isDone())) {
            rotorSound = new DroneRotorSound(drone, true);
            client.getSoundManager().play(rotorSound);
        }
        controlScreen.flightTick();
    }

    /**
     * Ensures the player's local chunk is present, stabilized, and rebuilt in WorldRenderer upon camera return
     * so the operator never falls into empty space after long-range signal loss or exit.
     */
    private static void tickReturnSafety(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            returningToPlayer = false;
            returnSafetyTicks = 0;
            return;
        }
        int px = client.player.getBlockX() >> 4;
        int pz = client.player.getBlockZ() >> 4;
        boolean centerReady = client.world.getChunkManager().getChunk(px, pz, ChunkStatus.FULL, false) != null;
        if (!centerReady) {
            // Freeze downward velocity while the local chunk is being restored
            if (protectedPlayerPos != null) {
                client.player.setPosition(protectedPlayerPos.x, protectedPlayerPos.y, protectedPlayerPos.z);
            }
            client.player.setVelocity(Vec3d.ZERO);
            if (returnSafetyTicks % 4 == 0 && ClientPlayNetworking.canSend(DroneNetworking.MISSING_CHUNKS_ID)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeByte(1);
                buf.writeLong(ChunkPos.toLong(px, pz));
                ClientPlayNetworking.send(DroneNetworking.MISSING_CHUNKS_ID, buf);
            }
        }
        if ((centerReady && returnSafetyTicks >= 2) || ++returnSafetyTicks > 40) {
            returningToPlayer = false;
            returnSafetyTicks = 0;
            client.world.getChunkManager().setChunkMapCenter(px, pz);
            if (client.worldRenderer != null) {
                client.worldRenderer.scheduleTerrainUpdate();
            }
        }
    }

    private static void probeMissingChunks(MinecraftClient client) {
        if (client.world == null || drone == null || !ClientPlayNetworking.canSend(DroneNetworking.MISSING_CHUNKS_ID)) return;
        if (++repairTicks < 5) return;
        repairTicks = 0;
        int radius = Math.min(MAX_REPAIR_RADIUS, client.options.getViewDistance().getValue());
        int radiusSq = radius * radius;
        int centerX = drone.getBlockX() >> 4, centerZ = drone.getBlockZ() >> 4;
        long[] missing = new long[REPAIR_MAX_BATCH];
        int count = 0;

        // Always check the immediate 3x3 around the drone first
        for (int dx = -1; dx <= 1 && count < REPAIR_MAX_BATCH; dx++) {
            for (int dz = -1; dz <= 1 && count < REPAIR_MAX_BATCH; dz++) {
                int x = centerX + dx, z = centerZ + dz;
                if (client.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false) == null) {
                    missing[count++] = ChunkPos.toLong(x, z);
                }
            }
        }

        for (int checked = 0; checked < REPAIR_SCAN_PER_PASS && count < REPAIR_MAX_BATCH; checked++) {
            long value = REPAIR_OFFSETS[repairCursor++ % REPAIR_OFFSETS.length];
            int d2 = (int) (value >>> 16);
            if (d2 <= 2 || d2 > radiusSq) continue;
            int x = centerX + (int) ((value >>> 8) & 0xFF) - 128;
            int z = centerZ + (int) (value & 0xFF) - 128;
            if (client.world.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false) == null) {
                missing[count++] = ChunkPos.toLong(x, z);
            }
        }
        if (count == 0) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeByte(count);
        for (int i = 0; i < count; i++) buffer.writeLong(missing[i]);
        ClientPlayNetworking.send(DroneNetworking.MISSING_CHUNKS_ID, buffer);
    }

    private static void tickObserverSounds(MinecraftClient client) {
        if (client.world != observerWorld) {
            clearObserverSounds(client);
            observerWorld = client.world;
            observerScanTicks = 9;
        }
        if (client.world == null || client.player == null) {
            clearObserverSounds(client);
            return;
        }
        observerSounds.entrySet().removeIf(entry -> {
            DroneRotorSound sound = entry.getValue();
            if (!sound.isDone() && sound.canContinue()) return false;
            client.getSoundManager().stop(sound);
            return true;
        });
        if (++observerScanTicks % 10 != 0) return;
        Entity listener = isActive() && drone != null ? drone : client.getCameraEntity() == null ? client.player : client.getCameraEntity();
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof DroneEntity nearby) || !nearby.isAlive() || !(nearby.isArmed() || nearby.isAutoLanding())
                    || isViewing(nearby)) continue;
            double maxRangeSq = nearby.getDroneType() == DroneType.FIXED_WING
                    ? DroneRotorSound.FIXED_WING_OBSERVER_RANGE_SQUARED
                    : DroneRotorSound.OBSERVER_RANGE_SQUARED;
            if (listener.squaredDistanceTo(nearby) > maxRangeSq) continue;
            if (!observerSounds.containsKey(nearby.getUuid())) {
                DroneRotorSound sound = new DroneRotorSound(nearby, false);
                observerSounds.put(nearby.getUuid(), sound);
                client.getSoundManager().play(sound);
            }
        }
    }

    private static void clearObserverSounds(MinecraftClient client) {
        for (DroneRotorSound sound : observerSounds.values()) client.getSoundManager().stop(sound);
        observerSounds.clear();
    }

    static void sendControls(float pitch, float roll, float yaw, float throttle, float lookPitch, int flags) {
        if (!isActive() || !ClientPlayNetworking.canSend(DroneNetworking.CONTROL_ID)) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(droneId);
        buffer.writeInt(++sequence);
        buffer.writeFloat(MathHelper.clamp(pitch, -1, 1));
        buffer.writeFloat(MathHelper.clamp(roll, -1, 1));
        buffer.writeFloat(MathHelper.clamp(yaw, -1, 1));
        buffer.writeFloat(MathHelper.clamp(throttle, 0, 1));
        buffer.writeFloat(MathHelper.clamp(lookPitch, -80, 80));
        buffer.writeByte(flags);
        if (flags != 0) LOGGER.info("Drone action sent: uuid={} sequence={} flags={}", new Object[]{droneUuid, sequence, flags});
        ClientPlayNetworking.send(DroneNetworking.CONTROL_ID, buffer);
    }

    public static void endControl(boolean notifyServer) {
        endControl(notifyServer, "operator_exit");
    }

    static void logAction(String action) {
        LOGGER.info("Drone input action: uuid={} action={}", droneUuid, action);
    }

    private static void endControl(boolean notifyServer, String reason) {
        if (!isActive()) return;
        DroneVideoFeed.reset();
        LOGGER.info("Drone control ended: uuid={} reason={} sendExit={}", new Object[]{droneUuid, reason, notifyServer});
        if (notifyServer) sendControls(0, 0, 0, 0.5f, 0, DroneNetworking.EXIT);
        MinecraftClient client = MinecraftClient.getInstance();
        droneId = -1;
        droneUuid = null;
        drone = null;
        previousDronePosition = null;
        lookPitch = previousLookPitch = 0;
        lookPitchInitialized = false;
        if (rotorSound != null) client.getSoundManager().stop(rotorSound);
        rotorSound = null;

        // Activate return-to-player safety guard BEFORE snapping the camera back
        returningToPlayer = true;
        returnSafetyTicks = 0;
        if (client.world != null && client.player != null) {
            int px = client.player.getBlockX() >> 4;
            int pz = client.player.getBlockZ() >> 4;
            protectedPlayerChunk = new ChunkPos(px, pz);
            // Immediately restore the client chunk map center and protected 5x5 local chunks around the player
            client.world.getChunkManager().setChunkMapCenter(px, pz);
            if (protectedPlayerPos != null && client.player.squaredDistanceTo(protectedPlayerPos) < 9.0) {
                client.player.setVelocity(Vec3d.ZERO);
            }
        }

        Entity restore = previousCamera;
        if (restore == null || restore.isRemoved() || restore.getWorld() != client.world) restore = client.player;
        client.setCameraEntity(restore);
        if (client.worldRenderer != null) {
            client.worldRenderer.scheduleTerrainUpdate();
        }
        previousCamera = null;
        controlWorld = null;
        controlScreen = null;
        screenSuspended = false;
        client.options.setPerspective(previousPerspective);
        if (client.currentScreen instanceof DroneScreen) client.setScreen(null);
    }

    public static boolean isActive() { return droneId >= 0; }
    public static boolean isReturningToPlayer() { return returningToPlayer; }
    public static ChunkPos getProtectedPlayerChunk() { return protectedPlayerChunk; }

    /**
     * Returns the active drone entity for {@code WorldRenderer.setupTerrain} when viewing a remote drone,
     * or {@code fallbackPlayer} otherwise.
     */
    public static Entity activeViewEntity(Entity fallbackPlayer) {
        if (isActive() && drone != null && !drone.isRemoved()) {
            return drone;
        }
        return fallbackPlayer;
    }

    public static boolean isViewing(Entity entity) {
        return isActive() && entity.getId() == droneId && entity.getUuid().equals(droneUuid) && entity.getWorld() == controlWorld;
    }
    public static DroneEntity getDrone() { return drone; }
    static float getSpeed() { return speed; }
    public static boolean isDownwardCamera() { return drone != null && drone.isDownCamera(); }

    public static boolean isFpvCamera() { return isActive() && drone != null && drone.getDroneType().isStrikeImpact(); }
    public static float cameraFieldOfView() {
        float base = drone == null ? 70 : DroneCameraGeometry.fieldOfView(drone.getDroneType());
        float zoom = controlScreen == null ? 1 : controlScreen.zoom();
        return DroneHudProjection.zoomedFov(base, zoom);
    }

    public static Vec3d cameraPosition(float tickDelta) {
        if (drone == null) return null;
        return DroneCameraGeometry.mount(drone.getDroneType(), drone.getLerpedPos(tickDelta), cameraYaw(tickDelta),
                MathHelper.lerpAngleDegrees(tickDelta, drone.prevPitch, drone.getPitch()),
                cameraRoll(tickDelta), isDownwardCamera());
    }

    public static float cameraYaw(float tickDelta) {
        return drone == null ? 0 : MathHelper.lerpAngleDegrees(tickDelta, drone.prevYaw, drone.getYaw());
    }

    public static float cameraPitch(float tickDelta) {
        if (drone == null) return 0;
        if (isDownwardCamera()) return 90;
        if (drone.isAngleMode()) return lookPitchInitialized
                ? MathHelper.lerp(tickDelta, previousLookPitch, lookPitch) : drone.getLookPitch();
        return MathHelper.lerpAngleDegrees(tickDelta, drone.prevPitch, drone.getPitch()) + drone.getCameraTilt();
    }

    public static float cameraRoll(float tickDelta) {
        if (drone == null || drone.getDroneType().isCarrier() || isDownwardCamera()) return 0;
        return MathHelper.lerpAngleDegrees(tickDelta, previousBank, bank);
    }
}
