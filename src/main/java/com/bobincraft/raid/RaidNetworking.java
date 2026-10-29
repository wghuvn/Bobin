package com.bobincraft.raid;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class RaidNetworking {
    public static final Identifier OPEN_MAP = new Identifier("bobin_craft", "raid_map");
    public static final Identifier REQUEST_FIELD_MAP = new Identifier("bobin_craft", "request_field_map");
    public static final Identifier OPEN_FIELD_MAP = new Identifier("bobin_craft", "field_map");
    public static final Identifier DEPLOY = new Identifier("bobin_craft", "raid_deploy");
    public static final Identifier GUNFIRE = new Identifier("bobin_craft", "raid_gunfire");

    private RaidNetworking() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_FIELD_MAP, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 0) return;
            server.execute(() -> RaidManager.openFieldMap(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(GUNFIRE, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != 0) return;
            server.execute(() -> {
                if (!player.isAlive() || player.isSpectator()) return;
                WarzoneEncounterManager.onGunfire(player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DEPLOY, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != Long.BYTES + Integer.BYTES) return;
            long rotation = buf.readLong();
            int id = buf.readInt();
            server.execute(() -> RaidManager.deploy(player, rotation, id));
        });
    }

    public static void sendMap(ServerPlayerEntity player, TacticalMapData data) {
        if (data.fieldMap()) throw new IllegalArgumentException("Field map cannot authorize deployment");
        send(player, data, OPEN_MAP);
    }

    public static void sendFieldMap(ServerPlayerEntity player, TacticalMapData data) {
        if (!data.fieldMap()) throw new IllegalArgumentException("Deployment data cannot be sent as field map");
        send(player, data, OPEN_FIELD_MAP);
    }

    private static void send(ServerPlayerEntity player, TacticalMapData data, Identifier channel) {
        if (!ServerPlayNetworking.canSend(player, channel)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        data.write(buf);
        ServerPlayNetworking.send(player, channel, buf);
    }
}
