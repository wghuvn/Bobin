package com.bobincraft.raid;

import com.bobincraft.warzone.WarzoneDimension;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative hub boundary countdown, targeted strike and non-destructive spectacle. */
public final class HubDesertion {
    public static final RegistryKey<DamageType> STRIKE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier("bobin_craft", "deserter_strike"));
    public static final float STRIKE_DAMAGE = 1_000.0f;
    private static final String VISUAL_TAG = "bobin_deserter_bomb";
    private static final Map<UUID, Warning> WARNINGS = new HashMap<>();
    private static final List<Shockwave> SHOCKWAVES = new ArrayList<>();
    private static Item bombItem;

    private HubDesertion() { }

    public static boolean isStrike(DamageSource source) { return source.isOf(STRIKE); }

    public static void register() {
        bombItem = Registry.register(Registries.ITEM, new Identifier("bobin_craft", "deserter_bomb"),
                new Item(new Item.Settings().maxCount(1)));
        ServerTickEvents.END_SERVER_TICK.register(HubDesertion::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                CommandManager.literal("bobin").then(CommandManager.literal("hub")
                        .then(CommandManager.literal("deserter").then(CommandManager.literal("status")
                                .executes(context -> status(context.getSource())))))));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeVisuals(handler.player.getUuid()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && RaidManager.isAvailable(player.getServer()))
                cancel(player, false);
        });
        // The display is an effect, never a persistent object after a crash or unloaded chunk.
        // We add the tag only after spawnEntity, so this does not discard a newly created bomb.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity && entity.getCommandTags().contains(VISUAL_TAG)) entity.discard();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Warning warning : WARNINGS.values()) warning.remove();
            WARNINGS.clear();
            SHOCKWAVES.clear();
        });
    }

    /** Called only after a successful server-validated map teleport, or a completed hub return. */
    public static void authorizedTravel(ServerPlayerEntity player) { cancel(player, false); }

    private static void tick(MinecraftServer server) {
        tickShockwaves();
        if (!RaidManager.isAvailable(server)) return;
        RaidPersistentState raids = RaidPersistentState.get(server);
        if (raids.hubDesign != HubLayout.DESIGN_VERSION) return;
        HubDesertionState state = HubDesertionState.get(server);
        long now = System.currentTimeMillis();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            RaidPersistentState.PlayerRaid raid = raids.player(uuid);
            boolean inside = player.getServerWorld() == server.getOverworld()
                    && HubDesertionPolicy.inside(player.getX(), player.getY(), player.getZ(),
                    HubLayout.HUB_RADIUS, HubLayout.FLOOR_Y);
            boolean exempt = !player.isAlive() || player.isCreative() || player.isSpectator();
            if (!HubDesertionPolicy.shouldWarn(exempt, inside, raid.active,
                    WarzoneDimension.isWarzone(player.getServerWorld()))) {
                cancel(player, inside && player.isAlive());
                continue;
            }
            HubDesertionPolicy policy = state.players.computeIfAbsent(uuid, ignored -> new HubDesertionPolicy());
            if (policy.begin(now)) {
                // Saving once at the boundary prevents restarting or reconnecting from renewing the ten seconds.
                state.markDirty();
                server.getOverworld().getPersistentStateManager().save();
                player.sendMessage(Text.translatable("raid.bobin_craft.deserter_warning").formatted(Formatting.RED), false);
            }
            if (policy.struck()) { removeVisuals(uuid); continue; }
            Warning warning = WARNINGS.computeIfAbsent(uuid, ignored -> new Warning(player));
            warning.bar.setName(Text.translatable("raid.bobin_craft.deserter", policy.seconds(now)));
            warning.bar.setPercent((float) policy.remaining(now) / HubDesertionPolicy.COUNTDOWN_MILLIS);
            if (policy.seconds(now) != warning.lastSecond) {
                // The warning is a spoken radio line; replaying it every second
                // would layer ten voices over the countdown and bomb whistle.
                if (warning.lastSecond < 0)
                    player.playSound(HubAudio.DESERTER_WARNING, SoundCategory.PLAYERS, .8f, 1.0f);
                warning.lastSecond = policy.seconds(now);
            }
            if (policy.remaining(now) <= HubDesertionPolicy.BOMB_FALL_MILLIS)
                warning.fall(player, policy.remaining(now));
            if (policy.strike(now)) {
                // Commit before causing damage; the death callback removes the completed ledger entry.
                state.markDirty();
                server.getOverworld().getPersistentStateManager().save();
                removeVisuals(uuid);
                strike(player);
            }
        }
    }

    /** Read-only diagnostics explain every runtime gate without editing a save or resetting a timer. */
    private static int status(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        MinecraftServer server = source.getServer();
        RaidPersistentState raids = RaidPersistentState.get(server);
        boolean inside = player.getServerWorld() == server.getOverworld()
                && HubDesertionPolicy.inside(player.getX(), player.getY(), player.getZ(), HubLayout.HUB_RADIUS, HubLayout.FLOOR_Y);
        boolean exempt = !player.isAlive() || player.isCreative() || player.isSpectator();
        RaidPersistentState.PlayerRaid raid = raids.player(player.getUuid());
        boolean authorizedMap = raid.active && WarzoneDimension.isWarzone(player.getServerWorld());
        HubDesertionPolicy countdown = HubDesertionState.get(server).players.get(player.getUuid());
        String timer = countdown == null ? "none" : countdown.struck() ? "struck" : countdown.seconds(System.currentTimeMillis()) + "s";
        source.sendFeedback(() -> Text.literal("Deserter: available=" + RaidManager.isAvailable(server)
                + ", design=" + raids.hubDesign + ", inside=" + inside + ", exempt=" + exempt
                + ", authorizedMap=" + authorizedMap + ", raidInitialized=" + raid.initialized
                + ", timer=" + timer), false);
        return 1;
    }

    private static void cancel(ServerPlayerEntity player, boolean notify) {
        HubDesertionState state = HubDesertionState.get(player.getServer());
        HubDesertionPolicy policy = state.players.remove(player.getUuid());
        if (policy != null) {
            state.markDirty();
            if (notify && policy.active())
                player.sendMessage(Text.translatable("raid.bobin_craft.deserter_cancelled").formatted(Formatting.GREEN), true);
        }
        removeVisuals(player.getUuid());
    }

    private static void removeVisuals(UUID uuid) {
        Warning warning = WARNINGS.remove(uuid);
        if (warning != null) warning.remove();
    }

    private static void strike(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d position = player.getPos().add(0, .8, 0);
        world.playSound(null, position.x, position.y, position.z, HubAudio.DESERTER_EXPLOSION,
                SoundCategory.HOSTILE, 4.5f, .9f);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 4, 1.2, .5, 1.2, 0);
        world.spawnParticles(ParticleTypes.FLASH, position.x, position.y, position.z, 3, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 180, 2.3, 1.7, 2.3, .16);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, position.x, position.y + .5, position.z, 130, 2.5, 2.1, 2.5, .11);
        world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, position.x, position.y, position.z, 45, .7, 1.4, .7, .08);
        SHOCKWAVES.add(new Shockwave(world, position));
        DamageSource damage = new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(STRIKE), position);
        // Deliberately no createExplosion: only the deserter receives damage; buildings and NPCs stay intact.
        player.damage(damage, STRIKE_DAMAGE);
    }

    private static void tickShockwaves() {
        for (Iterator<Shockwave> iterator = SHOCKWAVES.iterator(); iterator.hasNext();) {
            Shockwave wave = iterator.next();
            if (++wave.age > 20) { iterator.remove(); continue; }
            if (wave.age % 2 != 0) continue;
            double radius = wave.age * .55;
            for (int i = 0; i < 48; i++) {
                double angle = Math.PI * 2 * i / 48;
                wave.world.spawnParticles(ParticleTypes.CLOUD,
                        wave.position.x + Math.cos(angle) * radius, wave.position.y - .4,
                        wave.position.z + Math.sin(angle) * radius, 1, 0, .1, 0, .02);
            }
        }
    }

    private static final class Warning {
        final ServerBossBar bar = new ServerBossBar(Text.translatable("raid.bobin_craft.deserter", 10),
                BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        DisplayEntity.ItemDisplayEntity bomb;
        int lastSecond = -1;

        Warning(ServerPlayerEntity player) { bar.addPlayer(player); }

        void fall(ServerPlayerEntity player, long remainingMillis) {
            ServerWorld world = player.getServerWorld();
            if (bomb == null || bomb.isRemoved() || bomb.getWorld() != world) {
                if (bomb != null) bomb.discard();
                bomb = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
                NbtCompound nbt = new NbtCompound();
                nbt.put("item", new ItemStack(bombItem).writeNbt(new NbtCompound()));
                nbt.putString("item_display", "fixed");
                nbt.putFloat("view_range", 2.5f);
                nbt.putFloat("width", 3);
                nbt.putFloat("height", 4);
                nbt.putBoolean("Invulnerable", true);
                nbt.putBoolean("NoGravity", true);
                bomb.readNbt(nbt);
                // Rotation/scale are authored in the native item model's fixed display transform.
                bomb.setPosition(player.getX(), player.getY() + 25, player.getZ());
                world.spawnEntity(bomb);
                bomb.addCommandTag(VISUAL_TAG);
                player.playSound(HubAudio.BOMB_WHISTLE, SoundCategory.HOSTILE, 1.5f, 1);
            }
            double fraction = (double) remainingMillis / HubDesertionPolicy.BOMB_FALL_MILLIS;
            // The native model extends 1.35 blocks below its origin; keep the tip above the feet.
            double height = 1.35 + 25 * fraction * fraction;
            bomb.setPosition(player.getX(), player.getY() + height, player.getZ());
            if (world.getTime() % 2 == 0) {
                world.spawnParticles(ParticleTypes.SMOKE, bomb.getX(), bomb.getY() + 1, bomb.getZ(), 5, .13, .2, .13, .02);
                world.spawnParticles(ParticleTypes.FLAME, bomb.getX(), bomb.getY() + 1.3, bomb.getZ(), 3, .1, .12, .1, .015);
            }
        }

        void remove() {
            bar.clearPlayers();
            if (bomb != null) bomb.discard();
        }
    }

    private static final class Shockwave {
        final ServerWorld world;
        final Vec3d position;
        int age;
        Shockwave(ServerWorld world, Vec3d position) { this.world = world; this.position = position; }
    }
}
