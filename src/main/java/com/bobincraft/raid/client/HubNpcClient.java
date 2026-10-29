package com.bobincraft.raid.client;

import com.bobincraft.raid.HubNpcInteraction;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.util.UUID;

/** Native merchant containers are retained; only the camp's presentation is replaced. */
public final class HubNpcClient {
    private HubNpcClient() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HubNpcInteraction.OPEN, (client, handler, buffer, sender) -> {
            UUID entity = buffer.readUuid();
            UUID token = buffer.readUuid();
            int roleId = buffer.readUnsignedByte();
            int topicId = buffer.readUnsignedByte();
            Text name = buffer.readText();
            if (roleId >= HubNpcInteraction.Role.values().length
                    || topicId >= HubNpcInteraction.Topic.values().length) return;
            client.execute(() -> {
                if (client.player == null || client.world == null) return;
                HubNpcInteraction.Role role = HubNpcInteraction.Role.values()[roleId];
                HubNpcInteraction.Topic topic = HubNpcInteraction.Topic.values()[topicId];
                if (client.currentScreen instanceof HubDialogueScreen dialogue && dialogue.sessionToken().equals(token)) {
                    dialogue.setTopic(topic);
                } else {
                    client.setScreen(new HubDialogueScreen(entity, token, role, topic, name));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(HubNpcInteraction.CLOSE, (client, handler, buffer, sender) -> {
            if (buffer.readableBytes() != 16) return;
            UUID token = buffer.readUuid();
            client.execute(() -> {
                if (client.currentScreen instanceof HubDialogueScreen dialogue && dialogue.sessionToken().equals(token)) {
                    client.setScreen(null);
                }
            });
        });
    }

    static void choose(UUID entity, UUID token, HubNpcInteraction.Choice choice) {
        if (!ClientPlayNetworking.canSend(HubNpcInteraction.CHOICE)) return;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(entity);
        buffer.writeUuid(token);
        buffer.writeByte(choice.ordinal());
        ClientPlayNetworking.send(HubNpcInteraction.CHOICE, buffer);
    }
}
