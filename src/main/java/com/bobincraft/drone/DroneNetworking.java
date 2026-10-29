package com.bobincraft.drone;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fixed-size input-only packets. Position, collision, damage, payload and ownership stay on the server. */
public final class DroneNetworking {
    public static final Identifier CONTROL_ID = new Identifier("bobin_craft", "drone_control");
    public static final Identifier SESSION_ID = new Identifier("bobin_craft", "drone_session");
    public static final Identifier MISSING_CHUNKS_ID = new Identifier("bobin_craft", "drone_missing_chunks");
    public static final int ARM = 1, DROP = 2, EXIT = 4, LEVEL = 8, CAMERA = 16;
    public static final int UNLINK = 32;
    private static final UUID NO_UUID = new UUID(0, 0);
    private static final Map<UUID, DroneEntity> OWNED = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("bobin_craft.drone");

    public enum EndReason {
        CLIENT_EXIT, CLIENT_UNLINK, OWNER_INVALID, OWNER_MOVED, OPERATOR_HURT,
        SIGNAL_LOST, INPUT_TIMEOUT, BATTERY_EMPTY, SESSION_MISSING, WORLD_BOUNDARY,
        ENTITY_REMOVED, DISCONNECTED
    }

    private DroneNetworking() { }

    private static final class Session {
        final DroneEntity drone;
        final Vec3d anchor;
        int sequence = -1;
        int actions;
        long inputTick = Long.MIN_VALUE;
        Session(DroneEntity drone, Vec3d anchor) { this.drone = drone; this.anchor = anchor; }
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CONTROL_ID, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 29) return;
            int id = buf.readInt(), sequence = buf.readInt();
            DroneFlight.Input input = new DroneFlight.Input(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
            float lookPitch = buf.readFloat();
            int flags = buf.readUnsignedByte();
            if (!input.valid() || !Float.isFinite(lookPitch) || Math.abs(lookPitch) > 80 || (flags & ~63) != 0 || sequence < 0) return;
            server.execute(() -> receive(player, id, sequence, input, lookPitch, flags));
        });
        // Client-side chunk-map may evict terrain even after the server sent it.
        // Only accept at most eight coordinates; all permission/radius checks run on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(MISSING_CHUNKS_ID, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() < 1) return;
            int count = buf.readUnsignedByte();
            if (count < 1 || count > 8 || buf.readableBytes() != count * Long.BYTES) return;
            long[] keys = new long[count];
            for (int i = 0; i < count; i++) keys[i] = buf.readLong();
            server.execute(() -> DroneChunkStreamer.repairMissing(player, keys));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DroneEntity drone = OWNED.remove(handler.player.getUuid());
            Session session = SESSIONS.remove(handler.player.getUuid());
            if (session != null) logEnd(handler.player, session.drone, EndReason.DISCONNECTED);
            if (drone != null) { drone.releaseControl(); drone.discard(); }
            DroneChunkStreamer.disconnect(handler.player);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof DroneEntity drone) removed(drone);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof DroneEntity drone)) return;
            drone.releaseControl();
            if (!drone.isLinked()) return;
            UUID owner = drone.getOwnerUuid();
            if (owner == null) return;
            DroneEntity previous = OWNED.get(owner);
            if (previous != null && previous != drone && !previous.isRemoved()) drone.discard();
            else OWNED.put(owner, drone);
        });
        ServerTickEvents.END_SERVER_TICK.register(DroneChunkStreamer::tickAll);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DroneChunkStreamer.clearAll();
            OWNED.clear(); SESSIONS.clear();
        });
    }

    private static void receive(ServerPlayerEntity player, int id, int sequence, DroneFlight.Input input, float lookPitch, int flags) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.drone.getId() != id || !DroneFlight.fresh(sequence, session.sequence)) return;
        DroneEntity drone = session.drone;
        if (!validOperator(player, drone)) { end(player, drone, EndReason.OWNER_INVALID); return; }
        if (player.getPos().squaredDistanceTo(session.anchor) > 4) { end(player, drone, EndReason.OWNER_MOVED); return; }
        session.sequence = sequence;
        EndReason actionEnd = actionEndReason(flags);
        if (actionEnd == EndReason.CLIENT_UNLINK) { unlink(player, drone); return; }
        if (actionEnd != null) { end(player, drone, actionEnd); return; }
        long tick = player.getWorld().getTime();
        // Preserve the newest sticks when TCP coalesces samples; apply each action at most once per tick.
        if (session.inputTick != tick) { session.inputTick = tick; session.actions = 0; }
        int actions = flags & ~session.actions;
        session.actions |= actions;
        drone.acceptInput(input, actions, lookPitch);
    }

    public static boolean validOperator(ServerPlayerEntity player, DroneEntity drone) {
        return player.isAlive() && !player.isSpectator() && !player.hasVehicle() && !drone.isRemoved() && drone.isLinked()
                && player.getWorld() == drone.getWorld() && player.getUuid().equals(drone.getOwnerUuid());
    }

    public static DroneEntity owned(ServerPlayerEntity player) {
        DroneEntity drone = OWNED.get(player.getUuid());
        if (drone != null && !drone.isLinked()) { OWNED.remove(player.getUuid()); return null; }
        if (drone != null && !drone.isRemoved() && drone.getWorld() == player.getWorld()) return drone;
        if (drone != null) { drone.releaseControl(); drone.discard(); }
        OWNED.remove(player.getUuid());
        return null;
    }

    public static void begin(ServerPlayerEntity player, DroneEntity drone) {
        if (!validOperator(player, drone) || drone.getBatteryFraction() <= 0 || !ServerPlayNetworking.canSend(player, SESSION_ID)) return;
        DroneEntity previous = OWNED.get(player.getUuid());
        if (previous != null && previous != drone && !previous.isRemoved()) return;
        OWNED.put(player.getUuid(), drone);
        SESSIONS.put(player.getUuid(), new Session(drone, player.getPos()));
        drone.releaseControl();
        drone.startControl();
        DroneChunkStreamer.begin(player, drone);
        LOGGER.info("Drone control started: owner={}, drone={}, type={}", player.getGameProfile().getName(), drone.getId(), drone.getDroneType());
        send(player, drone);
    }

    public static boolean controls(ServerPlayerEntity player, DroneEntity drone) {
        Session session = SESSIONS.get(player.getUuid());
        return session != null && session.drone == drone;
    }

    public static boolean hasActiveSession(ServerPlayerEntity player) {
        return player != null && SESSIONS.containsKey(player.getUuid());
    }

    public static net.minecraft.util.math.Vec3d viewOrigin(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        return session != null && validOperator(player, session.drone) ? session.drone.getPos() : player.getPos();
    }

    public static void end(ServerPlayerEntity player, DroneEntity drone) {
        end(player, drone, EndReason.CLIENT_EXIT);
    }

    public static void end(ServerPlayerEntity player, DroneEntity drone, EndReason reason) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.drone != drone) return;
        SESSIONS.remove(player.getUuid());
        logEnd(player, drone, reason);
        drone.releaseControl();
        send(player, null);
        DroneChunkStreamer.reset(player);
    }

    private static void unlink(ServerPlayerEntity player, DroneEntity drone) {
        SESSIONS.remove(player.getUuid());
        if (OWNED.get(player.getUuid()) == drone) OWNED.remove(player.getUuid());
        logEnd(player, drone, EndReason.CLIENT_UNLINK);
        drone.unlinkAndLand();
        send(player, null);
        DroneChunkStreamer.reset(player);
    }

    public static void removed(DroneEntity drone) {
        drone.releaseControl();
        UUID owner = drone.getOwnerUuid();
        if (owner == null) return;
        if (OWNED.get(owner) == drone) OWNED.remove(owner);
        Session session = SESSIONS.get(owner);
        if (session != null && session.drone == drone) {
            SESSIONS.remove(owner);
            if (drone.getWorld().getServer() != null) {
                ServerPlayerEntity player = drone.getWorld().getServer().getPlayerManager().getPlayer(owner);
                if (player != null) { logEnd(player, drone, EndReason.ENTITY_REMOVED); send(player, null); DroneChunkStreamer.reset(player); }
            }
        }
    }

    public static EndReason actionEndReason(int flags) {
        if ((flags & UNLINK) != 0) return EndReason.CLIENT_UNLINK;
        return (flags & EXIT) != 0 ? EndReason.CLIENT_EXIT : null;
    }

    public static EndReason controlEndReason(boolean sessionPresent, boolean operatorValid, boolean operatorHurt,
                                              float signal, float battery, long tick, long inputTick) {
        if (!sessionPresent) return EndReason.SESSION_MISSING;
        if (!operatorValid) return EndReason.OWNER_INVALID;
        if (operatorHurt) return EndReason.OPERATOR_HURT;
        if (signal <= 0) return EndReason.SIGNAL_LOST;
        if (DroneFlight.inputTimedOut(tick, inputTick)) return EndReason.INPUT_TIMEOUT;
        return battery <= 0 ? EndReason.BATTERY_EMPTY : null;
    }

    private static void logEnd(ServerPlayerEntity player, DroneEntity drone, EndReason reason) {
        LOGGER.info("Drone control ended: owner={}, drone={}, type={}, reason={}, armed={}, battery={}, signal={}, ownerHealth={}, range={}",
                player.getGameProfile().getName(), drone.getId(), drone.getDroneType(), reason, drone.isArmed(),
                drone.getBatteryFraction(), drone.getSignalStrength(), player.getHealth(), drone.distanceTo(player));
    }

    private static void send(ServerPlayerEntity player, DroneEntity drone) {
        if (!ServerPlayNetworking.canSend(player, SESSION_ID)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(drone == null ? -1 : drone.getId());
        buf.writeUuid(drone == null ? NO_UUID : drone.getUuid());
        ServerPlayNetworking.send(player, SESSION_ID, buf);
    }
}
