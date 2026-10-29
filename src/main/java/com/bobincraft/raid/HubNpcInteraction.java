package com.bobincraft.raid;

import com.bobincraft.raid.guard.WarzoneGuardEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative camp conversations; trades keep the villager's native merchant handler. */
public final class HubNpcInteraction {
    public static final Identifier OPEN = new Identifier("bobin_craft", "npc_dialogue");
    public static final Identifier CHOICE = new Identifier("bobin_craft", "npc_choice");
    public static final Identifier CLOSE = new Identifier("bobin_craft", "npc_close");
    private static final Map<UUID, Conversation> CONVERSATIONS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_OPEN = new HashMap<>();
    private static final Map<UUID, Long> NEXT_VOICE = new HashMap<>();

    public enum Role {
        SENTRY("", "entity.bobin_craft.hub_guard"),
        QUARTERMASTER("bobin_hub_merchant", "entity.bobin_craft.quartermaster"),
        COOK("bobin_hub_cook", "entity.bobin_craft.camp_cook"),
        MEDIC("bobin_hub_medic", "entity.bobin_craft.field_medic"),
        COMMANDER("bobin_hub_commander", "entity.bobin_craft.tactical_commander");
        public final String tag;
        public final String nameKey;
        Role(String tag, String nameKey) { this.tag = tag; this.nameKey = nameKey; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
        public boolean service() { return this != SENTRY; }
        public Text nameText() { return Text.translatable(nameKey); }
    }

    public enum Topic { GREETING, STATUS, RULES, DUTY }
    public enum Choice { STATUS, RULES, DUTY, TRADE, BACK, GOODBYE }

    private static final class Conversation {
        final HubDialogueSession authorization;
        final Role role;
        int merchantSyncId = -1;
        Conversation(HubDialogueSession authorization, Role role) {
            this.authorization = authorization;
            this.role = role;
        }
    }

    private HubNpcInteraction() { }

    /** Custom names are synchronized entity data; command tags alone are not visible to clients. */
    public static Role roleOf(Entity entity) {
        if (entity instanceof WarzoneGuardEntity guard && guard.isHubGuard()) return Role.SENTRY;
        if (!(entity instanceof VillagerEntity)) return null;
        Role named = roleFromTitle(entity.getCustomName());
        if (named != null) return named;
        for (Role role : Role.values()) {
            if (role.service() && entity.getCommandTags().contains(role.tag)) return role;
        }
        return null;
    }

    public static Role roleFromTitle(Text title) {
        if (title == null || !(title.getContent() instanceof TranslatableTextContent content)) return null;
        for (Role role : Role.values()) {
            if (role.service() && content.getKey().equals(role.nameKey)) return role;
        }
        return null;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Refresh synced appearance/audio flags on saved NPCs without replacing
            // their entities, inventories or used trade offers.
            if (RaidManager.isAvailable(server)) HubNpcs.spawn(server.getOverworld());
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Role role = roleOf(entity);
            if (role == null || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            if (player instanceof ServerPlayerEntity serverPlayer) open(serverPlayer, entity);
            return ActionResult.CONSUME;
        });
        ServerPlayNetworking.registerGlobalReceiver(CHOICE, (server, player, handler, buffer, sender) -> {
            if (buffer.readableBytes() != 33) return;
            UUID target = buffer.readUuid();
            UUID token = buffer.readUuid();
            int choice = buffer.readUnsignedByte();
            if (choice >= Choice.values().length) return;
            server.execute(() -> choose(player, target, token, Choice.values()[choice]));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CONVERSATIONS.remove(handler.player.getUuid());
            NEXT_OPEN.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CONVERSATIONS.clear();
            NEXT_OPEN.clear();
            NEXT_VOICE.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = server.getTicks();
            NEXT_OPEN.entrySet().removeIf(entry -> now >= entry.getValue());
            NEXT_VOICE.entrySet().removeIf(entry -> now >= entry.getValue());
            var iterator = CONVERSATIONS.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                Conversation conversation = entry.getValue();
                Entity entity = player == null ? null : player.getServerWorld().getEntity(conversation.authorization.entity());
                if (player != null && conversation.merchantSyncId >= 0
                        && player.currentScreenHandler.syncId != conversation.merchantSyncId) {
                    iterator.remove();
                    continue;
                }
                if (player == null || !eligible(player, entity)
                        || conversation.merchantSyncId < 0 && conversation.authorization.expired(now)
                        || roleOf(entity) != conversation.role) {
                    if (player != null) {
                        if (conversation.merchantSyncId >= 0) player.closeHandledScreen();
                        sendClose(player, conversation.authorization.token());
                    }
                    iterator.remove();
                }
            }
        });
    }

    /** Also used by Bobin camera interaction, which deliberately bypasses vanilla item interaction. */
    public static boolean open(ServerPlayerEntity player, Entity entity) {
        Role role = entity == null ? null : roleOf(entity);
        if (role == null || !eligible(player, entity)) return false;
        long now = player.getServer().getTicks();
        if (now < NEXT_OPEN.getOrDefault(player.getUuid(), Long.MIN_VALUE)) return true;
        if (!ServerPlayNetworking.canSend(player, OPEN)) return false;
        if (entity instanceof VillagerEntity villager && villager.getCustomer() != null
                && villager.getCustomer() != player) return true;
        NEXT_OPEN.put(player.getUuid(), now + 10);
        player.closeHandledScreen();
        Conversation conversation = new Conversation(new HubDialogueSession(UUID.randomUUID(), entity.getUuid(),
                player.getWorld().getRegistryKey().getValue().toString(), role.key(), now), role);
        CONVERSATIONS.put(player.getUuid(), conversation);
        entity.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        sound(entity, role, "greeting");
        send(player, entity, conversation, Topic.GREETING);
        return true;
    }

    private static boolean eligible(ServerPlayerEntity player, Entity entity) {
        return player.isAlive() && !player.isSpectator() && entity != null && entity.isAlive()
                && player.getWorld() == entity.getWorld() && HubSafety.isHub(player.getServerWorld())
                && player.squaredDistanceTo(entity) <= HubDialogueSession.MAX_DISTANCE_SQUARED;
    }

    private static void choose(ServerPlayerEntity player, UUID target, UUID token, Choice choice) {
        Conversation conversation = CONVERSATIONS.get(player.getUuid());
        if (conversation == null || conversation.merchantSyncId >= 0) return;
        Entity entity = player.getServerWorld().getEntity(target);
        if (!eligible(player, entity) || roleOf(entity) != conversation.role) return;
        long now = player.getServer().getTicks();
        if (!conversation.authorization.accept(token, target, player.getWorld().getRegistryKey().getValue().toString(),
                conversation.role.key(), player.squaredDistanceTo(entity), now)) return;
        if (choice == Choice.GOODBYE) {
            sound(entity, conversation.role, "goodbye");
            CONVERSATIONS.remove(player.getUuid());
            sendClose(player, token);
            return;
        }
        if (choice == Choice.TRADE) {
            if (!(entity instanceof VillagerEntity villager) || !conversation.role.service()
                    || villager.getCustomer() != null && villager.getCustomer() != player) return;
            villager.setCustomer(player);
            villager.sendOffers(player, Text.translatable(conversation.role.nameKey), 0);
            conversation.merchantSyncId = player.currentScreenHandler.syncId;
            if (conversation.merchantSyncId == player.playerScreenHandler.syncId) {
                villager.setCustomer(null);
                CONVERSATIONS.remove(player.getUuid());
                return;
            }
            sound(entity, conversation.role, "accept");
            return;
        }
        Topic topic = switch (choice) {
            case STATUS -> Topic.STATUS;
            case RULES -> Topic.RULES;
            case DUTY -> Topic.DUTY;
            default -> Topic.GREETING;
        };
        sound(entity, conversation.role, "reply");
        send(player, entity, conversation, topic);
    }

    private static void sound(Entity entity, Role role, String cue) {
        if (entity.getWorld() instanceof ServerWorld world) {
            if (!reserveVoice(entity)) return;
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    HubAudio.soundForRole(role, cue), SoundCategory.NEUTRAL, 0.85f, 1.0f);
        }
    }

    public static boolean reserveVoice(Entity entity) {
        if (!(entity.getWorld() instanceof ServerWorld world)) return false;
        long now = world.getServer().getTicks();
        if (now < NEXT_VOICE.getOrDefault(entity.getUuid(), Long.MIN_VALUE)) return false;
        // Spoken radio cues last up to seven seconds. Topic navigation stays immediate,
        // while one voice channel per NPC prevents layered speech in multiplayer.
        NEXT_VOICE.put(entity.getUuid(), now + 140);
        return true;
    }

    private static void send(ServerPlayerEntity player, Entity entity, Conversation conversation, Topic topic) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(entity.getUuid());
        buffer.writeUuid(conversation.authorization.token());
        buffer.writeByte(conversation.role.ordinal());
        buffer.writeByte(topic.ordinal());
        buffer.writeText(entity.getDisplayName());
        ServerPlayNetworking.send(player, OPEN, buffer);
    }

    private static void sendClose(ServerPlayerEntity player, UUID token) {
        if (!ServerPlayNetworking.canSend(player, CLOSE)) return;
        ServerPlayNetworking.send(player, CLOSE, PacketByteBufs.create().writeUuid(token));
    }
}
