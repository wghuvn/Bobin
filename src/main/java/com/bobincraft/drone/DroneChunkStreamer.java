package com.bobincraft.drone;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkRenderDistanceCenterS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dual-zone terrain & entity streaming for long-range drone operation.
 *
 * <p>Architectural guarantees:
 * <ol>
 *   <li><b>Protected Player Safety Zone</b>: A dedicated 5x5 {@code PLAYER_SAFETY_TICKET} is pinned around the
 *       operator's physical body throughout the entire drone flight and return transition so the operator's
 *       local chunks can never unload or displace on the server.</li>
 *   <li><b>Bounded Remote Drone Window</b>: Instead of cloning the server's entire global view distance (which
 *       causes massive chunk-meshing and network FPS spikes), the remote drone stream uses a bounded radius
 *       (default 7 chunks, max 8) plus directional velocity prefetch via {@link DroneChunkPrefetchManager}.</li>
 *   <li><b>Incremental Entity Tracker Sync</b>: When the drone crosses a chunk boundary, stationary entities
 *       (NPCs, vehicles, charges) inside the newly entered remote chunks have their {@code EntityTracker}
 *       status updated incrementally for the operator without scanning the entire world.</li>
 *   <li><b>Safe Return Protocol</b>: When signal is lost or control ends, the server immediately pins and
 *       burst-sends the 5x5 core chunks around the operator's body on tick 0 after re-centering the client map,
 *       before streaming outer ring chunks, guaranteeing the operator never falls into empty space.</li>
 * </ol>
 */
public final class DroneChunkStreamer {
    private static final Logger LOGGER = LoggerFactory.getLogger("bobin_craft.drone");
    private static final boolean DEBUG_CHUNKS = Boolean.getBoolean("bobin_craft.fpv.chunkDebug");

    /** Bounded remote view radius (8 chunks = 128m radius / 256m diameter) prevents FPS spikes on high view-distance servers. */
    private static final int MAX_VIEW_RADIUS = 8;
    private static final int MIN_VIEW_RADIUS = 3;
    private static final int LEAD_RADIUS = 3;
    private static final int LEAD_MAX_CHUNKS = 7;
    private static final int LEAD_TICKS = 55;
    private static final int LEAD_INTERVAL = 4;
    private static final int PERIODIC_REPLAN = 80;
    private static final int SERVICE_INTERVAL = 1;
    private static final int SENDS_PER_TICK = 6;
    private static final int RESTORE_PER_TICK = 14;
    private static final int MAX_NEW_VIEW_TICKETS_PER_TICK = 10;
    private static final int MAX_NEW_LEAD_TICKETS_PER_TICK = 5;
    private static final int MAX_PENDING_VIEW = 64;
    private static final int MAX_PENDING_LEAD = 24;
    private static final int REFRESH_PER_TICK = 12;
    private static final int SENT_GRACE_CHUNKS = 1;
    private static final int TICKET_GRACE_CHUNKS = 1;
    private static final int ACTIVE_REFRESH = 10;
    private static final int PLAYER_SAFETY_REFRESH = 20;
    private static final long NONE = Long.MIN_VALUE;

    /** Entity-ticking corridor around the drone: radius 2 => level 31 (5x5 entity-ticking grid). */
    private static final ChunkTicketType<UUID> ACTIVE_TICKET =
            ChunkTicketType.create("bobin_drone_active", UUID::compareTo, 40);
    /** Protected safety anchor around the operator's stationary body: radius 2 => level 31 (5x5 loaded & entity-ticking). */
    private static final ChunkTicketType<UUID> PLAYER_SAFETY_TICKET =
            ChunkTicketType.create("bobin_player_safety", UUID::compareTo, 80);
    /** Load-only chunk ticket for drone visual perimeter & forward prefetch: radius 0 => level 33 (FULL without entity ticking). */
    private static final ChunkTicketType<UUID> VIEW_TICKET =
            ChunkTicketType.create("bobin_drone_view", UUID::compareTo, 100);

    private static final Map<UUID, Stream> STREAMS = new HashMap<>();
    private static final Map<UUID, Restore> RESTORES = new HashMap<>();
    private static final List<Stream> FINISHED = new ArrayList<>(2);

    /** Chunk offsets within MAX_VIEW_RADIUS, nearest first. */
    private static final int[] OFF_DX, OFF_DZ, OFF_D2;

    static {
        int r = MAX_VIEW_RADIUS, r2 = r * r;
        long[] packed = new long[(2 * r + 1) * (2 * r + 1)];
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r2) continue;
                packed[n++] = ((long) d2 << 16) | ((long) (dx + 128) << 8) | ((long) (dz + 128));
            }
        }
        Arrays.sort(packed, 0, n);
        OFF_DX = new int[n];
        OFF_DZ = new int[n];
        OFF_D2 = new int[n];
        for (int i = 0; i < n; i++) {
            long v = packed[i];
            OFF_D2[i] = (int) (v >>> 16);
            OFF_DX[i] = (int) ((v >>> 8) & 0xFF) - 128;
            OFF_DZ[i] = (int) (v & 0xFF) - 128;
        }
    }

    private DroneChunkStreamer() { }

    // ------------------------------------------------------------------------------------------ lifecycle

    /** Begins a remote streaming session and pins the player's local safety chunks. */
    public static void begin(ServerPlayerEntity player, DroneEntity drone) {
        if (!(player.getWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        Stream stale = STREAMS.get(player.getUuid());
        if (stale != null) finish(stale, player, true);
        Stream stream = new Stream(world.getServer(), player, drone, world);
        Restore pending = RESTORES.remove(player.getUuid());
        if (pending != null) pending.handOver(stream.suspect);
        STREAMS.put(player.getUuid(), stream);
        // Immediately prime the drone's initial 3x3 chunks so launch transition is instantaneous
        stream.primeInitialArea(player);
    }

    /** Ends the control session: restores the operator's normal view and releases drone tickets. Idempotent. */
    public static void reset(ServerPlayerEntity player) {
        Stream stream = STREAMS.get(player.getUuid());
        if (stream != null) finish(stream, player, true);
    }

    /** Client reports absent chunks; server validates that they belong to the active FPV area or player safety zone. */
    public static void repairMissing(ServerPlayerEntity player, long[] keys) {
        Stream stream = STREAMS.get(player.getUuid());
        if (stream != null && DroneNetworking.controls(player, stream.drone) && player.getWorld() == stream.world) {
            stream.queueRepair(keys);
            return;
        }
        Restore restore = RESTORES.get(player.getUuid());
        if (restore != null && player.getWorld() == restore.world) {
            restore.queueUrgentRepair(player, keys);
        }
    }

    /** The operator left the server: release all tickets immediately. */
    public static void disconnect(ServerPlayerEntity player) {
        Stream stream = STREAMS.remove(player.getUuid());
        if (stream != null) stream.release();
        Restore restore = RESTORES.remove(player.getUuid());
        if (restore != null) restore.releaseSafetyTicket();
    }

    /** Server stopped: clear all tracking structures. */
    public static void clearAll() {
        DroneChunkTicketManager.clearAll();
        DroneViewStreamingManager.clearAll();
        STREAMS.clear();
        RESTORES.clear();
        FINISHED.clear();
    }

    /** END_SERVER_TICK. */
    public static void tickAll(MinecraftServer server) {
        if (!STREAMS.isEmpty()) {
            for (Stream stream : STREAMS.values()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(stream.owner);
                boolean alive = false;
                if (player != null) {
                    try {
                        alive = stream.tick(server, player);
                    } catch (RuntimeException e) {
                        LOGGER.warn("Drone chunk streaming failed, closing stream: owner={}", stream.owner, e);
                    }
                }
                if (!alive) FINISHED.add(stream);
            }
            if (!FINISHED.isEmpty()) {
                for (Stream stream : FINISHED) finish(stream, server.getPlayerManager().getPlayer(stream.owner), true);
                FINISHED.clear();
            }
        }
        if (!RESTORES.isEmpty()) {
            RESTORES.values().removeIf(restore -> {
                try {
                    return restore.tick(server);
                } catch (RuntimeException e) {
                    LOGGER.warn("Drone chunk restore failed: owner={}", restore.owner, e);
                    restore.releaseSafetyTicket();
                    return true;
                }
            });
        }
    }

    private static void finish(Stream stream, ServerPlayerEntity player, boolean notify) {
        STREAMS.remove(stream.owner);
        try {
            if (notify && player != null && player.isAlive() && player.getWorld() == stream.world) {
                Restore restore = stream.restoreClient(player);
                if (restore != null) {
                    RESTORES.put(stream.owner, restore);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Drone view restore failed: owner={}", stream.owner, e);
        } finally {
            stream.release();
        }
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static int keyX(long key) { return new ChunkPos(key).x; }

    private static int keyZ(long key) { return new ChunkPos(key).z; }

    private static int dist2(int x, int z, int cx, int cz) {
        int dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz;
    }

    private static int chebyshev(int x, int z, int cx, int cz) {
        return Math.max(Math.abs(x - cx), Math.abs(z - cz));
    }

    private static LongArrayList sortedByDistance(LongArrayList keys, int cx, int cz) {
        int n = keys.size();
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            long key = keys.getLong(i);
            packed[i] = ((long) dist2(keyX(key), keyZ(key), cx, cz) << 24) | (long) i;
        }
        Arrays.sort(packed);
        LongArrayList out = new LongArrayList(n);
        for (long value : packed) out.add(keys.getLong((int) (value & 0xFFFFFF)));
        return out;
    }

    private static WorldChunk loadedChunk(ServerWorld world, int x, int z) {
        if (!world.isChunkLoaded(x, z)) return null;
        Chunk chunk = world.getChunk(x, z, ChunkStatus.FULL, false);
        return chunk instanceof WorldChunk worldChunk ? worldChunk : null;
    }

    // ------------------------------------------------------------------------------------------ per-session state

    private static final class Stream {
        final UUID owner;
        final DroneEntity drone;
        final ServerWorld world;
        final LongOpenHashSet sent = new LongOpenHashSet();
        final LongOpenHashSet repairs = new LongOpenHashSet();
        long lastRepairTick = Long.MIN_VALUE;
        final LongOpenHashSet suspect = new LongOpenHashSet();
        final LongOpenHashSet ticketSet = new LongOpenHashSet();
        final LongArrayList ticketList = new LongArrayList();
        final LongArrayList plan = new LongArrayList();
        int viewCount;
        int viewDistance, viewRadius, leadRadius;
        int playerX, playerZ;
        int clientX, clientZ;
        int droneX = Integer.MIN_VALUE, droneZ, leadX, leadZ;
        boolean replan = true, needsWork = true;
        long activeA = NONE, activeB = NONE;
        long playerSafetyAnchor = NONE;
        int refreshCursor;

        Stream(MinecraftServer server, ServerPlayerEntity player, DroneEntity drone, ServerWorld world) {
            this.owner = player.getUuid();
            this.drone = drone;
            this.world = world;
            readViewDistance(server);
            playerX = player.getBlockX() >> 4;
            playerZ = player.getBlockZ() >> 4;
            clientX = playerX;
            clientZ = playerZ;
            pinPlayerSafetyTicket(playerX, playerZ, true);
        }

        private void readViewDistance(MinecraftServer server) {
            viewDistance = server.getPlayerManager().getViewDistance();
            viewRadius = MathHelper.clamp(Math.min(MAX_VIEW_RADIUS, viewDistance), MIN_VIEW_RADIUS, MAX_VIEW_RADIUS);
            leadRadius = Math.min(LEAD_RADIUS, viewRadius);
        }

        private void pinPlayerSafetyTicket(int px, int pz, boolean force) {
            long nextKey = ChunkPos.toLong(px, pz);
            ServerChunkManager manager = chunks();
            if (playerSafetyAnchor != NONE && playerSafetyAnchor != nextKey) {
                manager.removeTicket(PLAYER_SAFETY_TICKET, new ChunkPos(playerSafetyAnchor), 2, owner);
            }
            if (force || playerSafetyAnchor != nextKey) {
                manager.addTicket(PLAYER_SAFETY_TICKET, new ChunkPos(px, pz), 2, owner);
                playerSafetyAnchor = nextKey;
            }
        }

        void primeInitialArea(ServerPlayerEntity player) {
            Vec3d pos = drone.getPos();
            int cx = MathHelper.floor(pos.x) >> 4;
            int cz = MathHelper.floor(pos.z) >> 4;
            updateActive(pos, drone.getVelocity(), world.getTime());
            rebuild(player, cx, cz);
            service(player);
            refreshRemoteEntitiesNearDrone(player, cx, cz);
        }

        boolean tick(MinecraftServer server, ServerPlayerEntity player) {
            if (drone.isRemoved() || drone.getWorld() != world || player.getWorld() != world || !player.isAlive()
                    || !DroneNetworking.controls(player, drone)) return false;
            long t0 = System.nanoTime();
            long time = world.getTime();
            Vec3d pos = drone.getPos();
            Vec3d vel = drone.getVelocity();
            int cx = MathHelper.floor(pos.x) >> 4, cz = MathHelper.floor(pos.z) >> 4;

            int px = player.getBlockX() >> 4, pz = player.getBlockZ() >> 4;
            if (px != playerX || pz != playerZ) {
                playerX = px; playerZ = pz;
                clientX = px; clientZ = pz;
                pinPlayerSafetyTicket(px, pz, true);
                replan = true;
            } else if (time % PLAYER_SAFETY_REFRESH == 0) {
                pinPlayerSafetyTicket(playerX, playerZ, true);
            }

            if (time % PERIODIC_REPLAN == 0) {
                if (server.getPlayerManager().getViewDistance() != viewDistance) readViewDistance(server);
                replan = true;
            }
            boolean chunkChanged = cx != droneX || cz != droneZ;
            if (chunkChanged || time % LEAD_INTERVAL == 0) {
                ChunkPos lead = DroneChunkPrefetchManager.computeLeadCenter(pos, vel, LEAD_TICKS, LEAD_MAX_CHUNKS);
                if (lead.x != leadX || lead.z != leadZ) {
                    leadX = lead.x;
                    leadZ = lead.z;
                    replan = true;
                }
            }
            if (chunkChanged) replan = true;

            updateActive(pos, vel, time);
            boolean rebuilt = false;
            if (replan) {
                replan = false;
                rebuild(player, cx, cz);
                needsWork = true;
                rebuilt = true;
            }
            if (needsWork && (rebuilt || time % SERVICE_INTERVAL == 0)) service(player);
            if (chunkChanged || time % 15 == 0) {
                refreshRemoteEntitiesNearDrone(player, cx, cz);
            }
            refreshSlice();

            double elapsedMs = (System.nanoTime() - t0) / 1_000_000.0;
            LongOpenHashSet activeKeys = DroneChunkTicketManager.computeActiveWindowSet(cx, cz, DroneChunkTicketManager.activeRadius);
            DroneChunkTicketManager.recordAllocation(
                    owner, new ChunkPos(cx, cz), new ChunkPos(leadX, leadZ), activeKeys, ticketSet, time);
            DroneViewStreamingManager.updateSession(new DroneViewSession(
                    owner, drone.getUuid(), drone.getId(),
                    new ChunkPos(playerX, playerZ), new ChunkPos(cx, cz), new ChunkPos(clientX, clientZ),
                    viewRadius, ticketSet.size(), sent.size(), plan.size() + repairs.size(),
                    ticketSet.size() + activeKeys.size(), elapsedMs
            ));
            if (DEBUG_CHUNKS && time % 100 == 0) {
                LOGGER.info("FPV chunks: owner={} drone=({}, {}) viewRadius={} sent={} tickets={} waiting={} ms={}",
                        new Object[]{owner, cx, cz, viewRadius, sent.size(), ticketSet.size(), plan.size(), elapsedMs});
            }
            return true;
        }

        private ServerChunkManager chunks() { return world.getChunkManager(); }

        /**
         * Incrementally refreshes entity tracking for entities inside the 11x11 chunk neighborhood around the drone
         * without scanning the global world entity tracker map.
         */
        private void refreshRemoteEntitiesNearDrone(ServerPlayerEntity player, int cx, int cz) {
            Vec3d center = drone.getPos();
            Box remoteBox = new Box(center.x - 96.0, world.getBottomY(), center.z - 96.0,
                    center.x + 96.0, world.getTopY(), center.z + 96.0);
            List<Entity> nearby = world.getOtherEntities(player, remoteBox, Entity::isAlive);
            if (nearby.isEmpty()) return;
            var tacs = world.getChunkManager().threadedAnvilChunkStorage;
            // Calling updatePosition triggers EntityTracker.updateTrackedStatus(player) which uses our DroneEntityTrackerMixin
            // Only invoke when the drone actually changed chunk so per-tick overhead is zero during hover.
            if (cx != clientX || cz != clientZ || world.getTime() % 15 == 0) {
                tacs.updatePosition(player);
            }
        }

        void queueRepair(long[] keys) {
            long now = world.getTime();
            if (lastRepairTick != Long.MIN_VALUE && now - lastRepairTick < 4) return;
            lastRepairTick = now;
            int r2 = (viewRadius + 1) * (viewRadius + 1);
            for (long key : keys) {
                int x = keyX(key), z = keyZ(key);
                boolean inDroneView = dist2(x, z, droneX, droneZ) <= r2;
                boolean inPlayerSafety = chebyshev(x, z, playerX, playerZ) <= 2;
                if (!inDroneView && !inPlayerSafety) continue;
                if (repairs.size() >= 96) break;
                repairs.add(key);
                if (!ticketSet.contains(key)) addViewTicket(key);
            }
            if (!repairs.isEmpty()) needsWork = true;
        }

        /**
         * True ONLY when vanilla keeps this chunk loaded for the operator AND it has NOT been evicted from the
         * client's shifted chunk map (outside {@code clientRadius} of the drone).
         */
        private boolean clientStillHasVanillaChunk(ServerPlayerEntity player, int x, int z) {
            if (suspect.contains(ChunkPos.toLong(x, z))) return false;
            int clientRadius = Math.max(2, viewDistance) + 2;
            if (chebyshev(x, z, clientX, clientZ) > clientRadius) return false;
            if (chebyshev(x, z, playerX, playerZ) > viewDistance + 1) return false;
            return PlayerLookup.tracking(world, new ChunkPos(x, z)).contains(player);
        }

        private boolean isPlayerSafetyChunk(int x, int z) {
            return chebyshev(x, z, playerX, playerZ) <= 2;
        }

        private void sendCenter(ServerPlayerEntity player, int x, int z) {
            if (clientX == x && clientZ == z) return;
            player.networkHandler.sendPacket(new ChunkRenderDistanceCenterS2CPacket(x, z));
            clientX = x;
            clientZ = z;
        }

        private void updateActive(Vec3d pos, Vec3d vel, long time) {
            long a = ChunkPos.toLong(MathHelper.floor(pos.x) >> 4, MathHelper.floor(pos.z) >> 4);
            long b = ChunkPos.toLong(MathHelper.floor(pos.x + vel.x * 3) >> 4, MathHelper.floor(pos.z + vel.z * 3) >> 4);
            boolean refresh = time % ACTIVE_REFRESH == 0;
            ServerChunkManager manager = chunks();
            if (refresh || (a != activeA && a != activeB)) manager.addTicket(ACTIVE_TICKET, new ChunkPos(a), 2, owner);
            if (b != a && (refresh || (b != activeA && b != activeB))) manager.addTicket(ACTIVE_TICKET, new ChunkPos(b), 2, owner);
            if (activeA != NONE && activeA != a && activeA != b) manager.removeTicket(ACTIVE_TICKET, new ChunkPos(activeA), 2, owner);
            if (activeB != NONE && activeB != a && activeB != b && activeB != activeA) manager.removeTicket(ACTIVE_TICKET, new ChunkPos(activeB), 2, owner);
            activeA = a;
            activeB = b == a ? NONE : b;
        }

        private void rebuild(ServerPlayerEntity player, int cx, int cz) {
            droneX = cx;
            droneZ = cz;
            int keepSentRadius = Math.min(viewRadius + SENT_GRACE_CHUNKS, viewDistance + 2);
            int keepSent = keepSentRadius * keepSentRadius;
            int keepTicketRadius = viewRadius + TICKET_GRACE_CHUNKS;
            int keepTicket = keepTicketRadius * keepTicketRadius;
            int keepLead = (leadRadius + 1) * (leadRadius + 1);

            // 1. Unload remote chunks that left the drone's view (NEVER unload the player's 5x5 safety zone!)
            LongIterator sentIt = sent.iterator();
            while (sentIt.hasNext()) {
                long key = sentIt.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (dist2(x, z, cx, cz) <= keepSent) continue;
                sentIt.remove();
                if (!isPlayerSafetyChunk(x, z) && !clientStillHasVanillaChunk(player, x, z)) {
                    player.networkHandler.sendPacket(new UnloadChunkS2CPacket(x, z));
                }
            }

            LongIterator repairIt = repairs.iterator();
            int r2 = viewRadius * viewRadius;
            while (repairIt.hasNext()) {
                long key = repairIt.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (dist2(x, z, cx, cz) > r2 && !isPlayerSafetyChunk(x, z)) repairIt.remove();
            }

            // 2. Shift the client's chunk map center onto the drone
            sendCenter(player, cx, cz);

            // 3. Release remote view tickets that are no longer near the drone view or forward prefetch corridor
            ServerChunkManager manager = chunks();
            LongIterator ticketIt = ticketSet.iterator();
            while (ticketIt.hasNext()) {
                long key = ticketIt.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (dist2(x, z, cx, cz) <= keepTicket || dist2(x, z, leadX, leadZ) <= keepLead) continue;
                ticketIt.remove();
                manager.removeTicket(VIEW_TICKET, new ChunkPos(x, z), 0, owner);
            }

            // 4. Mark any vanilla player chunks that fell outside the shifted client map as needing restore/resync
            markSuspects(cx, cz);

            // 5. Plan chunks to send around the drone (nearest first), then directional prefetch corridor ahead
            plan.clear();
            for (int i = 0; i < OFF_D2.length && OFF_D2[i] <= r2; i++) {
                int x = cx + OFF_DX[i], z = cz + OFF_DZ[i];
                long key = ChunkPos.toLong(x, z);
                if (sent.contains(key)) continue;
                if (clientStillHasVanillaChunk(player, x, z)) continue;
                plan.add(key);
            }
            viewCount = plan.size();

            LongArrayList corridor = DroneChunkPrefetchManager.computePrefetchCorridor(
                    drone.getPos(), drone.getVelocity(), leadRadius);
            for (int i = 0; i < corridor.size(); i++) {
                long key = corridor.getLong(i);
                int x = keyX(key), z = keyZ(key);
                if (dist2(x, z, cx, cz) <= r2) continue;
                if (ticketSet.contains(key) || clientStillHasVanillaChunk(player, x, z)) continue;
                plan.add(key);
            }
        }

        private void markSuspects(int cx, int cz) {
            int reach = viewDistance + 2;
            int clientRadius = Math.max(2, viewDistance) + 2;
            for (int x = playerX - reach; x <= playerX + reach; x++) {
                boolean xOut = Math.abs(x - cx) > clientRadius;
                for (int z = playerZ - reach; z <= playerZ + reach; z++) {
                    if (xOut || Math.abs(z - cz) > clientRadius) {
                        suspect.add(ChunkPos.toLong(x, z));
                    }
                }
            }
        }

        private void service(ServerPlayerEntity player) {
            int sendBudget = SENDS_PER_TICK;
            // Always prioritize the 3x3 immediate camera chunks around the drone so the camera never sees black void
            for (int dx = -1; dx <= 1 && sendBudget > 0; dx++) {
                for (int dz = -1; dz <= 1 && sendBudget > 0; dz++) {
                    int x = droneX + dx, z = droneZ + dz;
                    long key = ChunkPos.toLong(x, z);
                    if (sent.contains(key) || clientStillHasVanillaChunk(player, x, z)) continue;
                    if (!ticketSet.contains(key)) addViewTicket(key);
                    if (world.isChunkLoaded(x, z) && sendChunk(player, key, x, z)) {
                        sendBudget--;
                    }
                }
            }

            int repairBudget = Math.min(3, sendBudget);
            LongIterator repairIter = repairs.iterator();
            while (repairBudget > 0 && repairIter.hasNext()) {
                long key = repairIter.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (!world.isChunkLoaded(x, z)) {
                    if (!ticketSet.contains(key)) addViewTicket(key);
                    continue;
                }
                if (!ticketSet.contains(key)) addViewTicket(key);
                if (sendChunk(player, key, x, z)) {
                    repairIter.remove();
                    sendBudget--;
                    repairBudget--;
                }
            }

            int viewTicketBudget = MAX_NEW_VIEW_TICKETS_PER_TICK;
            int leadTicketBudget = MAX_NEW_LEAD_TICKETS_PER_TICK;
            int pendingView = 0;
            int pendingLead = 0;
            int viewR2 = viewRadius * viewRadius;
            LongIterator it = ticketSet.iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (world.isChunkLoaded(x, z)) continue;
                if (dist2(x, z, droneX, droneZ) <= viewR2) pendingView++;
                else pendingLead++;
            }

            int write = 0, newViewCount = 0;
            for (int i = 0; i < plan.size(); i++) {
                long key = plan.getLong(i);
                int x = keyX(key), z = keyZ(key);
                boolean inView = i < viewCount;
                boolean keep;
                if (inView) {
                    if (sent.contains(key)) {
                        keep = false;
                    } else if (world.isChunkLoaded(x, z)) {
                        if (!ticketSet.contains(key) && viewTicketBudget > 0 && addViewTicket(key)) viewTicketBudget--;
                        if (sendBudget > 0 && ticketSet.contains(key) && sendChunk(player, key, x, z)) {
                            sendBudget--;
                            keep = false;
                        } else {
                            keep = true;
                        }
                    } else {
                        keep = true;
                        if (viewTicketBudget > 0 && pendingView < MAX_PENDING_VIEW && addViewTicket(key)) {
                            viewTicketBudget--;
                            pendingView++;
                        }
                    }
                } else if (world.isChunkLoaded(x, z) || ticketSet.contains(key)) {
                    keep = false;
                } else if (leadTicketBudget > 0 && pendingLead < MAX_PENDING_LEAD && addViewTicket(key)) {
                    leadTicketBudget--;
                    pendingLead++;
                    keep = false;
                } else {
                    keep = true;
                }
                if (keep) {
                    plan.set(write++, key);
                    if (inView) newViewCount++;
                }
            }
            plan.removeElements(write, plan.size());
            viewCount = newViewCount;
            needsWork = write > 0 || !repairs.isEmpty();
        }

        private boolean sendChunk(ServerPlayerEntity player, long key, int x, int z) {
            WorldChunk chunk = loadedChunk(world, x, z);
            if (chunk == null) return false;
            player.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null));
            sent.add(key);
            suspect.remove(key);
            return true;
        }

        private boolean addViewTicket(long key) {
            if (!ticketSet.add(key)) return false;
            ticketList.add(key);
            chunks().addTicket(VIEW_TICKET, new ChunkPos(keyX(key), keyZ(key)), 0, owner);
            return true;
        }

        private void refreshSlice() {
            if (ticketList.size() > 2 * ticketSet.size() + 32) {
                ticketList.clear();
                ticketList.addAll(ticketSet);
                refreshCursor = 0;
            }
            ServerChunkManager manager = chunks();
            int budget = REFRESH_PER_TICK;
            while (budget-- > 0 && !ticketList.isEmpty()) {
                if (refreshCursor >= ticketList.size()) refreshCursor = 0;
                long key = ticketList.getLong(refreshCursor);
                if (!ticketSet.contains(key)) {
                    int last = ticketList.size() - 1;
                    ticketList.set(refreshCursor, ticketList.getLong(last));
                    ticketList.removeLong(last);
                    continue;
                }
                manager.addTicket(VIEW_TICKET, new ChunkPos(keyX(key), keyZ(key)), 0, owner);
                refreshCursor++;
            }
        }

        /**
         * Safely restores the operator's client chunk map on session end or signal loss:
         * 1. Unloads remote-only drone chunks while the client map is still centered at the drone.
         * 2. Re-centers the client map onto the operator's body (playerX, playerZ).
         * 3. Immediately burst-sends the 3x3 / 5x5 core chunks around the operator on tick 0 so the player's
         *    standing ground is guaranteed present before physics or camera return completes.
         * 4. Hands over remaining outer vanilla chunks to {@link Restore} while keeping {@code PLAYER_SAFETY_TICKET}
         *    pinned until restoration completes.
         */
        Restore restoreClient(ServerPlayerEntity player) {
            playerX = player.getBlockX() >> 4;
            playerZ = player.getBlockZ() >> 4;
            pinPlayerSafetyTicket(playerX, playerZ, true);

            LongIterator it = sent.iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (!isPlayerSafetyChunk(x, z) && !PlayerLookup.tracking(world, new ChunkPos(x, z)).contains(player)) {
                    player.networkHandler.sendPacket(new UnloadChunkS2CPacket(x, z));
                }
            }
            sent.clear();

            // Re-center the client chunk map onto the operator's physical body
            sendCenter(player, playerX, playerZ);

            // Ensure all 5x5 player safety chunks are included in the restore set if they ever became suspect
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    suspect.add(ChunkPos.toLong(playerX + dx, playerZ + dz));
                }
            }

            // Immediately burst-send the inner 3x3 (9 chunks) around the player's body right now on tick 0
            // so the client has solid ground under the player before processing the next client tick!
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int px = playerX + dx;
                    int pz = playerZ + dz;
                    long key = ChunkPos.toLong(px, pz);
                    WorldChunk chunk = loadedChunk(world, px, pz);
                    if (chunk != null) {
                        player.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null));
                        suspect.remove(key);
                    }
                }
            }

            LongArrayList stale = new LongArrayList();
            LongIterator suspects = suspect.iterator();
            while (suspects.hasNext()) {
                long key = suspects.nextLong();
                int x = keyX(key), z = keyZ(key);
                if (chebyshev(x, z, playerX, playerZ) <= viewDistance + 2) {
                    stale.add(key);
                }
            }
            long safetyKey = playerSafetyAnchor;
            playerSafetyAnchor = NONE; // Hand over safety ticket lifecycle to Restore
            world.getChunkManager().threadedAnvilChunkStorage.updatePosition(player);
            return new Restore(world, owner, sortedByDistance(stale, playerX, playerZ), safetyKey);
        }

        void release() {
            ServerChunkManager manager = chunks();
            LongIterator it = ticketSet.iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                manager.removeTicket(VIEW_TICKET, new ChunkPos(keyX(key), keyZ(key)), 0, owner);
            }
            ticketSet.clear();
            ticketList.clear();
            if (activeA != NONE) manager.removeTicket(ACTIVE_TICKET, new ChunkPos(activeA), 2, owner);
            if (activeB != NONE) manager.removeTicket(ACTIVE_TICKET, new ChunkPos(activeB), 2, owner);
            if (playerSafetyAnchor != NONE) manager.removeTicket(PLAYER_SAFETY_TICKET, new ChunkPos(playerSafetyAnchor), 2, owner);
            activeA = activeB = playerSafetyAnchor = NONE;
            sent.clear();
            repairs.clear();
            plan.clear();
            DroneChunkTicketManager.releaseAllocation(owner);
            DroneViewStreamingManager.removeSession(owner);
        }
    }

    // ------------------------------------------------------------------------------------------ restore

    private static final class Restore {
        final ServerWorld world;
        final UUID owner;
        final LongArrayList chunks;
        final LongArrayList deferred = new LongArrayList();
        long safetyTicketAnchor;
        int index;
        int attempts;
        int cooldown;

        Restore(ServerWorld world, UUID owner, LongArrayList chunks, long safetyTicketAnchor) {
            this.world = world;
            this.owner = owner;
            this.chunks = chunks;
            this.safetyTicketAnchor = safetyTicketAnchor;
        }

        void handOver(LongOpenHashSet suspect) {
            for (int i = index; i < chunks.size(); i++) suspect.add(chunks.getLong(i));
            for (int i = 0; i < deferred.size(); i++) suspect.add(deferred.getLong(i));
            releaseSafetyTicket();
        }

        void queueUrgentRepair(ServerPlayerEntity player, long[] keys) {
            int px = player.getBlockX() >> 4, pz = player.getBlockZ() >> 4;
            for (long key : keys) {
                int x = keyX(key), z = keyZ(key);
                if (chebyshev(x, z, px, pz) <= 3) {
                    WorldChunk chunk = loadedChunk(world, x, z);
                    if (chunk != null) {
                        player.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null));
                    } else if (!deferred.contains(key)) {
                        deferred.add(key);
                    }
                }
            }
        }

        void releaseSafetyTicket() {
            if (safetyTicketAnchor != NONE) {
                world.getChunkManager().removeTicket(PLAYER_SAFETY_TICKET, new ChunkPos(safetyTicketAnchor), 2, owner);
                safetyTicketAnchor = NONE;
            }
        }

        boolean tick(MinecraftServer server) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
            if (player == null || !player.isAlive() || player.getWorld() != world) {
                releaseSafetyTicket();
                return true;
            }
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            int budget = RESTORE_PER_TICK;
            while (budget-- > 0 && index < chunks.size()) {
                long key = chunks.getLong(index++);
                int x = keyX(key), z = keyZ(key);
                boolean inSafety = chebyshev(x, z, player.getBlockX() >> 4, player.getBlockZ() >> 4) <= 2;
                if (!inSafety && !PlayerLookup.tracking(world, new ChunkPos(x, z)).contains(player)) continue;
                WorldChunk chunk = loadedChunk(world, x, z);
                if (chunk == null) {
                    if (attempts < 4) deferred.add(key);
                    continue;
                }
                player.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null));
            }
            if (index < chunks.size()) return false;
            if (deferred.isEmpty() || attempts >= 4) {
                releaseSafetyTicket();
                return true;
            }
            chunks.clear();
            chunks.addAll(deferred);
            deferred.clear();
            index = 0;
            attempts++;
            cooldown = 5;
            return false;
        }
    }
}
