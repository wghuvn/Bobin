package com.bobincraft.destruction;

import com.bobincraft.BobinCraft;
import com.bobincraft.warzone.WarzoneDimension;
import com.bobincraft.warzone.WarzoneTerrain;
import com.bobincraft.weapon.grom7.Grom7Content;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Authoritative S2C synchronization, layered acoustic scheduler, and diagnostic command registration
 * for Scripted Structural Destruction (Section 2, Section 4 Level B & Section 11).
 */
public final class StructureNetworkSync {
    public static final Identifier STATE_SYNC_ID = new Identifier(BobinCraft.MOD_ID, "structure_state_sync");
    public static final Identifier TRANSITION_EFFECT_ID = new Identifier(BobinCraft.MOD_ID, "structure_transition_fx");
    public static final Identifier HIT_EFFECT_ID = new Identifier(BobinCraft.MOD_ID, "structure_hit_fx");
    public static final Identifier DEBUG_TOGGLE_ID = new Identifier(BobinCraft.MOD_ID, "structure_debug_toggle");

    public record ClientStructureView(
            String structureId,
            DestructibleStructureType.Category category,
            BlockPos origin,
            int radius,
            int height,
            int orientation,
            int variantIndex,
            float currentHealth,
            float maxHealth,
            StructureDamageState stage,
            boolean isBurning,
            float fireIntensity,
            int remainingBurnTime,
            List<StructuralSupportSolver.BreachOpening> breachOpenings
    ) {
        public ClientStructureView(
                String structureId,
                DestructibleStructureType.Category category,
                BlockPos origin,
                int radius,
                int height,
                int orientation,
                int variantIndex,
                float currentHealth,
                float maxHealth,
                StructureDamageState stage,
                boolean isBurning,
                float fireIntensity,
                int remainingBurnTime
        ) {
            this(structureId, category, origin, radius, height, orientation, variantIndex,
                    currentHealth, maxHealth, stage, isBurning, fireIntensity, remainingBurnTime, List.of());
        }

        public ClientStructureView(
                String structureId,
                DestructibleStructureType.Category category,
                BlockPos origin,
                int radius,
                int height,
                float currentHealth,
                float maxHealth,
                StructureDamageState stage,
                boolean isBurning,
                float fireIntensity,
                int remainingBurnTime
        ) {
            this(structureId, category, origin, radius, height, 0, 0,
                    currentHealth, maxHealth, stage, isBurning, fireIntensity, remainingBurnTime, List.of());
        }
    }

    public record TransitionScenarioPacket(
            long sequenceId,
            String structureId,
            BlockPos origin,
            StructureDamageState oldStage,
            StructureDamageState newStage,
            DestructionScenarioDirector.ScenarioProfile profile,
            String componentId,
            DestructibleStructure.HitZone hitZone,
            Vec3d impactPoint,
            int orientation,
            int variantIndex,
            int durationTicks
    ) {}

    public record StructureHitPacket(
            String structureId,
            BlockPos origin,
            StructureDamageState oldStage,
            StructureDamageState newStage,
            String componentId,
            DestructibleStructure.HitZone hitZone,
            Vec3d impactPoint
    ) {}

    private record SoundLayer(SoundEvent event, SoundCategory category) {}
    private static final ExplosionSoundMixer<SoundLayer> SOUND_MIXER = new ExplosionSoundMixer<>(128);
    private static boolean registered = false;
    private static volatile boolean debugBoundsEnabled = false;

    private StructureNetworkSync() {}

    /**
     * Schedules a delayed sound layer (e.g. structural shear, secondary crumble, settling debris)
     * without blocking or creating entities.
     */
    public static void scheduleLayeredSound(ServerWorld world,
                                            Vec3d pos,
                                            SoundEvent sound,
                                            SoundCategory category,
                                            float volume,
                                            float pitch,
                                            int delayTicks) {
        if (world == null || pos == null || sound == null) return;
        SOUND_MIXER.offer(world.getRegistryKey().getValue().toString(), pos.x, pos.y, pos.z,
                new SoundLayer(sound, category != null ? category : SoundCategory.BLOCKS), volume, pitch, world.getTime(), delayTicks);
    }

    private static void tickScheduledSounds(ServerWorld world) {
        for (var s : SOUND_MIXER.drain(world.getRegistryKey().getValue().toString(), world.getTime())) {
            BlockPos pos = BlockPos.ofFloored(s.x(), s.y(), s.z());
            if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                world.playSound(null, pos, s.sound().event(), s.sound().category(), s.volume(), s.pitch());
            }
        }
    }

    public static void register() {
        if (registered) return;
        registered = true;

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            RefineryTransitions.reset();
            com.bobincraft.destruction.debris.StructureDebrisManager.closeAll();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SOUND_MIXER.clear();
            ServerWorld warzone = server.getWorld(WarzoneDimension.WORLD);
            ServerWorld targetWorld = warzone != null ? warzone : server.getOverworld();
            WarzoneTerrain terrain = StructureDamageManager.resolveTerrain(targetWorld);
            if (terrain != null) {
                DestructibleStructureRegistry.ensureInitialized(terrain);
            }
            StructurePersistentState state = StructurePersistentState.get(targetWorld);
            state.applyToRegistry(targetWorld.getTime());
            StructureFireManager.syncFromRegistry(targetWorld.getTime());
            RefineryTransitions.bind(targetWorld);
            StructureUpdateQueue.recoverInterruptedTransitions(terrain);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SOUND_MIXER.clear();
            ServerWorld warzone = server.getWorld(WarzoneDimension.WORLD);
            ServerWorld targetWorld = warzone != null ? warzone : server.getOverworld();
            RefineryTransitions.flushProvenance(targetWorld);
            StructurePersistentState.get(targetWorld).captureFromRegistry();
            com.bobincraft.destruction.debris.StructureDebrisManager.closeAll();
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(
                (world, chunk) -> RefineryTransitions.chunkLoaded(world, chunk.getPos().x, chunk.getPos().z));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            server.execute(() -> sendFullSnapshotToPlayer(handler.player));
        });

        ServerTickEvents.START_WORLD_TICK.register(world -> com.bobincraft.destruction.debris.StructureDebrisManager.get(world).beginTick());
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            tickScheduledSounds(world);
            if (world.getRegistryKey().equals(WarzoneDimension.WORLD) || world.getServer().getWorld(WarzoneDimension.WORLD) == null) {
                StructureUpdateQueue.tick(world);
                DestructionScenarioDirector.tickServer(world.getTime());
                if (world.getTime() % 6 == 0) {
                    StructureDamageManager.tickDelayedCollapses(world, world.getTime());
                }
                if (world.getTime() % 20 == 0) {
                    StructureFireManager.tickServer(world, world.getTime());
                }
            }
            com.bobincraft.destruction.debris.StructureDebrisManager.get(world).tick();
            com.bobincraft.destruction.debris.DebrisNetworking.tick(world);
        });

        registerCommands();
    }

    public static PacketByteBuf encodeStateBatch(Collection<DestructibleStructure> structures) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(structures.size());
        for (DestructibleStructure s : structures) {
            buf.writeString(s.structureId(), 64);
            buf.writeByte(s.structureType().category().ordinal());
            buf.writeBlockPos(s.originPosition());
            buf.writeVarInt(s.radius());
            buf.writeVarInt(s.height());
            buf.writeByte(s.orientation());
            buf.writeByte(s.destroyedVariantIndex());
            buf.writeFloat(s.currentHealth());
            buf.writeFloat(s.maxHealth());
            buf.writeByte(s.damageStage().templateStage().index);
            buf.writeBoolean(s.isBurning());
            buf.writeFloat(s.fireIntensity());
            buf.writeVarInt(s.remainingBurnTime());
            List<StructuralSupportSolver.BreachOpening> openings = s.breachOpenings();
            buf.writeVarInt(openings.size());
            for (var b : openings) {
                buf.writeInt(b.centerX());
                buf.writeInt(b.centerY());
                buf.writeInt(b.centerZ());
                buf.writeFloat((float) b.normalX());
                buf.writeFloat((float) b.normalY());
                buf.writeFloat((float) b.normalZ());
                buf.writeFloat(b.radius());
                buf.writeVarInt(b.detachedCells());
            }
        }
        return buf;
    }

    public static List<ClientStructureView> decodeStateBatch(PacketByteBuf buf) {
        int count = Math.min(256, Math.max(0, buf.readVarInt()));
        List<ClientStructureView> list = new ArrayList<>(count);
        DestructibleStructureType.Category[] cats = DestructibleStructureType.Category.values();
        for (int i = 0; i < count; i++) {
            String id = buf.readString(64);
            int catOrd = buf.readByte() & 0xFF;
            DestructibleStructureType.Category cat = catOrd < cats.length ? cats[catOrd] : DestructibleStructureType.Category.INDUSTRIAL_GENERIC;
            BlockPos origin = buf.readBlockPos();
            int radius = buf.readVarInt();
            int height = buf.readVarInt();
            int orientation = buf.readByte() & 3;
            int variantIdx = buf.readByte() & 0xFF;
            float hp = buf.readFloat();
            float maxHp = buf.readFloat();
            StructureDamageState stage = StructureDamageState.byIndex(buf.readByte()).templateStage();
            boolean burning = buf.readBoolean();
            float intensity = buf.readFloat();
            int burnTime = buf.readVarInt();
            List<StructuralSupportSolver.BreachOpening> openings = List.of();
            if (buf.isReadable()) {
                int bCount = Math.min(16, Math.max(0, buf.readVarInt()));
                List<StructuralSupportSolver.BreachOpening> parsed = new ArrayList<>(bCount);
                for (int j = 0; j < bCount; j++) {
                    int bx = buf.readInt();
                    int by = buf.readInt();
                    int bz = buf.readInt();
                    float nx = buf.readFloat();
                    float ny = buf.readFloat();
                    float nz = buf.readFloat();
                    float br = buf.readFloat();
                    int cells = buf.readVarInt();
                    parsed.add(new StructuralSupportSolver.BreachOpening(bx, by, bz, nx, ny, nz, br, cells));
                }
                openings = List.copyOf(parsed);
            }
            list.add(new ClientStructureView(id, cat, origin, radius, height, orientation, variantIdx, hp, maxHp, stage, burning, intensity, burnTime, openings));
        }
        return list;
    }

    public static PacketByteBuf encodeTransitionScenario(DestructionScenarioDirector.ScenarioEvent event) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarLong(event.sequenceId());
        buf.writeString(event.structureId(), 64);
        buf.writeBlockPos(event.origin());
        buf.writeByte(event.fromStage().templateStage().index);
        buf.writeByte(event.toStage().templateStage().index);
        buf.writeByte(event.profile().ordinal());
        buf.writeString(event.componentId(), 64);
        buf.writeByte(event.hitZone().ordinal());
        buf.writeDouble(event.impactPoint().x);
        buf.writeDouble(event.impactPoint().y);
        buf.writeDouble(event.impactPoint().z);
        buf.writeByte(event.orientation());
        buf.writeByte(event.variantIndex());
        buf.writeVarInt(event.durationTicks());
        return buf;
    }

    public static TransitionScenarioPacket decodeTransitionScenario(PacketByteBuf buf) {
        long seqId = buf.readVarLong();
        String id = buf.readString(64);
        BlockPos origin = buf.readBlockPos();
        StructureDamageState oldStage = StructureDamageState.byIndex(buf.readByte()).templateStage();
        StructureDamageState newStage = StructureDamageState.byIndex(buf.readByte()).templateStage();
        DestructionScenarioDirector.ScenarioProfile[] profiles = DestructionScenarioDirector.ScenarioProfile.values();
        int profOrd = buf.readByte() & 0xFF;
        DestructionScenarioDirector.ScenarioProfile profile = profOrd < profiles.length
                ? profiles[profOrd]
                : DestructionScenarioDirector.ScenarioProfile.BRIDGE_SPAN_COLLAPSE;
        String compId = buf.readString(64);
        DestructibleStructure.HitZone[] zones = DestructibleStructure.HitZone.values();
        int zoneOrd = buf.readByte() & 0xFF;
        DestructibleStructure.HitZone hitZone = zoneOrd < zones.length ? zones[zoneOrd] : DestructibleStructure.HitZone.HULL;
        Vec3d impact = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int orientation = buf.readByte() & 3;
        int variantIdx = buf.readByte() & 0xFF;
        int duration = buf.readVarInt();
        return new TransitionScenarioPacket(seqId, id, origin, oldStage, newStage, profile, compId, hitZone, impact, orientation, variantIdx, duration);
    }

    public static PacketByteBuf encodeStructureHit(StructureHitPacket pkt) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(pkt.structureId(), 64);
        buf.writeBlockPos(pkt.origin());
        buf.writeByte(pkt.oldStage().templateStage().index);
        buf.writeByte(pkt.newStage().templateStage().index);
        buf.writeString(pkt.componentId() != null ? pkt.componentId() : "", 64);
        buf.writeByte(pkt.hitZone() != null ? pkt.hitZone().ordinal() : 0);
        buf.writeDouble(pkt.impactPoint().x);
        buf.writeDouble(pkt.impactPoint().y);
        buf.writeDouble(pkt.impactPoint().z);
        return buf;
    }

    public static StructureHitPacket decodeStructureHit(PacketByteBuf buf) {
        String id = buf.readString(64);
        BlockPos origin = buf.readBlockPos();
        StructureDamageState oldStage = StructureDamageState.byIndex(buf.readByte()).templateStage();
        StructureDamageState newStage = StructureDamageState.byIndex(buf.readByte()).templateStage();
        String compId = buf.readString(64);
        DestructibleStructure.HitZone[] zones = DestructibleStructure.HitZone.values();
        int zoneOrd = buf.readByte() & 0xFF;
        DestructibleStructure.HitZone hitZone = zoneOrd < zones.length ? zones[zoneOrd] : DestructibleStructure.HitZone.HULL;
        Vec3d impact = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new StructureHitPacket(id, origin, oldStage, newStage, compId, hitZone, impact);
    }

    public static void sendFullSnapshotToPlayer(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, STATE_SYNC_ID)) return;
        Collection<DestructibleStructure> all = DestructibleStructureRegistry.all();
        if (all.isEmpty()) return;
        PacketByteBuf buf = encodeStateBatch(all);
        ServerPlayNetworking.send(player, STATE_SYNC_ID, buf);
    }

    public static void broadcastStructureState(ServerWorld world, DestructibleStructure structure) {
        if (world == null || structure == null || world.getServer() == null) return;
        List<ServerPlayerEntity> players = world.getServer().getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        for (ServerPlayerEntity player : players) {
            if (ServerPlayNetworking.canSend(player, STATE_SYNC_ID)) {
                ServerPlayNetworking.send(player, STATE_SYNC_ID, encodeStateBatch(List.of(structure)));
            }
        }
    }

    public static void broadcastStructureHitEffect(ServerWorld world,
                                                   DestructibleStructure structure,
                                                   StructureDamageState oldStage,
                                                   StructureDamageState newStage,
                                                   String componentId,
                                                   DestructibleStructure.HitZone hitZone,
                                                   Vec3d impactPoint) {
        if (world == null || structure == null) return;
        BlockPos pos = structure.originPosition();
        Vec3d impact = impactPoint != null ? impactPoint : Vec3d.ofCenter(pos);
        if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            scheduleLayeredSound(world, impact,
                    SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.25f, 0.68f, 0);
            scheduleLayeredSound(world, impact,
                    Grom7Content.GROM7_DEBRIS_CRUMBLE_SOUND, SoundCategory.BLOCKS, 0.95f, 0.85f, 0);
        }
        StructureHitPacket pkt = new StructureHitPacket(
                structure.structureId(), pos, oldStage, newStage, componentId, hitZone, impact);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if ((player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 256 * 256
                    || com.bobincraft.drone.DroneNetworking.viewOrigin(player).squaredDistanceTo(Vec3d.ofCenter(pos)) <= 256 * 256)
                    && ServerPlayNetworking.canSend(player, HIT_EFFECT_ID)) {
                ServerPlayNetworking.send(player, HIT_EFFECT_ID, encodeStructureHit(pkt));
            }
        }
    }

    public static void broadcastStageTransition(ServerWorld world,
                                                DestructibleStructure structure,
                                                StructureDamageState oldStage,
                                                StructureDamageState newStage,
                                                DestructionScenarioDirector.ScenarioEvent scenarioEvent) {
        if (world == null || structure == null || scenarioEvent == null) return;
        BlockPos pos = structure.originPosition();
        Vec3d center = Vec3d.ofCenter(pos);
        if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            if (newStage.isTerminalOrDestroying()) {
                // Multi-stage structural collapse audio: initial fracture blast -> girder shear -> heavy collapse impact
                scheduleLayeredSound(world, center, Grom7Content.GROM7_DETONATE_SUB_SOUND, SoundCategory.BLOCKS, 2.35f, 0.62f, 0);
                scheduleLayeredSound(world, center, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.10f, 0.58f, 0);
                scheduleLayeredSound(world, center, Grom7Content.GROM7_DEBRIS_CRUMBLE_SOUND, SoundCategory.BLOCKS, 1.95f, 0.66f, 8);
                scheduleLayeredSound(world, center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.15f, 0.50f, 16);
                scheduleLayeredSound(world, center, Grom7Content.GROM7_DEBRIS_CRUMBLE_SOUND, SoundCategory.BLOCKS, 1.75f, 0.52f, 26);
                scheduleLayeredSound(world, center, SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.BLOCKS, 1.60f, 0.55f, 34);
            } else {
                scheduleLayeredSound(world, center, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.3f, 0.75f, 0);
            }
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if ((player.squaredDistanceTo(center) <= 256 * 256
                    || com.bobincraft.drone.DroneNetworking.viewOrigin(player).squaredDistanceTo(center) <= 256 * 256)
                    && ServerPlayNetworking.canSend(player, TRANSITION_EFFECT_ID)) {
                ServerPlayNetworking.send(player, TRANSITION_EFFECT_ID, encodeTransitionScenario(scenarioEvent));
            }
        }
    }

    public static boolean isDebugBoundsEnabled() {
        return debugBoundsEnabled;
    }

    public static void setDebugBoundsEnabled(boolean enabled) {
        debugBoundsEnabled = enabled;
    }

    private static int debrisDiagnostics(ServerCommandSource source, int offset) {
        var manager = com.bobincraft.destruction.debris.StructureDebrisManager.get(source.getWorld());
        source.sendFeedback(() -> Text.literal(manager.diagnostics()), false);
        for (String row : manager.diagnosticDetails(offset, 8)) {
            source.sendFeedback(() -> Text.literal(row), false);
        }
        return 1;
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("bobin")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("destruction")
                            .then(CommandManager.literal("debris")
                                    .executes(ctx -> debrisDiagnostics((ServerCommandSource) ctx.getSource(), 0))
                                    .then(CommandManager.argument("offset", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8192))
                                            .executes(ctx -> debrisDiagnostics((ServerCommandSource) ctx.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "offset")))))
                            .then(CommandManager.literal("trace")
                                    .executes(ctx -> {
                                        var source = (ServerCommandSource) ctx.getSource();
                                        source.sendFeedback(() -> Text.literal("trace=" + StructureDamageTrace.enabled()), false);
                                        for (String row : StructureDamageTrace.recent(16)) source.sendFeedback(() -> Text.literal(row), false);
                                        return 1;
                                    })
                                    .then(CommandManager.literal("on").executes(ctx -> { StructureDamageTrace.enabled(true); return 1; }))
                                    .then(CommandManager.literal("off").executes(ctx -> { StructureDamageTrace.enabled(false); return 1; })))
                            .then(CommandManager.literal("inspect")
                                    .executes(ctx -> executeInspect(((ServerCommandSource) ctx.getSource()))))
                            .then(CommandManager.literal("list")
                                    .executes(ctx -> executeList(((ServerCommandSource) ctx.getSource()))))
                            .then(CommandManager.literal("damage")
                                    .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(1.0f, 100000.0f))
                                            .executes(ctx -> executeDamage(((ServerCommandSource) ctx.getSource()),
                                                    com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "amount")))))
                            .then(CommandManager.literal("setstage")
                                    .then(CommandManager.argument("stage", com.mojang.brigadier.arguments.StringArgumentType.word())
                                            .executes(ctx -> executeSetStage(((ServerCommandSource) ctx.getSource()),
                                                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "stage")))))
                            .then(CommandManager.literal("reset")
                                    .executes(ctx -> executeReset(((ServerCommandSource) ctx.getSource()))))
                            .then(CommandManager.literal("debug")
                                    .executes(ctx -> executeDebug(((ServerCommandSource) ctx.getSource()))))
                    ));
        });
    }

    public static int executeInspect(ServerCommandSource source) {
        StructureDamageManager.ensureRegistryReady(source.getWorld());
        DestructibleStructure target = selectStructureForSource(source);
        if (target == null) {
            source.sendFeedback(() -> Text.literal("§c[BobinDestruction] Поблизости (в радиусе 96 блоков) не найдено разрушаемых сооружений."), false);
            return 0;
        }
        String info = String.format(Locale.ROOT,
                "§6[BobinDestruction] §fID: §e%s §7(%s) | Компонент: §d%s §7(%s)\n§fHP: §a%.1f / %.1f §7(%.0f%%) | Стадия: §b%s §7(вариант %d) | Пробоин: %d\n§fПожар: %s (интенсивность %.2f, осталось %d тиков) | Очередь блоков: %d",
                target.structureId(),
                target.structureType().id(),
                target.lastHitComponentId(),
                target.lastHitZone().name(),
                target.currentHealth(),
                target.maxHealth(),
                target.healthFraction() * 100.0f,
                target.damageStage().name(),
                target.destroyedVariantIndex(),
                target.breachOpenings().size(),
                target.isBurning() ? "§cГОРИТ§f" : "§7НЕТ§f",
                target.fireIntensity(),
                target.remainingBurnTime(),
                StructureUpdateQueue.totalQueuedBlockChanges()
        );
        source.sendFeedback(() -> Text.literal(info + " | " + RefineryTransitions.status(target.structureId())), false);
        return 1;
    }

    public static int executeList(ServerCommandSource source) {
        StructureDamageManager.ensureRegistryReady(source.getWorld());
        Collection<DestructibleStructure> all = DestructibleStructureRegistry.all();
        StringBuilder sb = new StringBuilder();
        sb.append("§6[BobinDestruction] Зарегистрировано объектов: ").append(all.size())
                .append(" | Активных пожаров: ").append(StructureFireManager.activeFireCount())
                .append(" | В очереди переходов: ").append(StructureUpdateQueue.pendingTransitionCount());
        List<DestructibleStructure> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(DestructibleStructure::structureId));
        for (DestructibleStructure s : sorted) {
            sb.append(String.format(Locale.ROOT, "\n §7- §e%s§7: %.0f/%.0f HP [%s]%s",
                    s.structureId(), s.currentHealth(), s.maxHealth(), s.damageStage().name(),
                    s.isBurning() ? " §c[FIRE]" : ""));
        }
        String msg = sb.toString();
        source.sendFeedback(() -> Text.literal(msg), false);
        return all.size();
    }

    public static int executeDamage(ServerCommandSource source, float amount) {
        StructureDamageManager.ensureRegistryReady(source.getWorld());
        DestructibleStructure target = selectStructureForSource(source);
        if (target == null) {
            source.sendFeedback(() -> Text.literal("§c[BobinDestruction] Не найдена цель для нанесения урона."), false);
            return 0;
        }
        StructureDamageSource dmgSource = new StructureDamageSource(
                UUID.randomUUID(), StructureDamageSource.Kind.ADMIN_COMMAND, null,
                Vec3d.ofCenter(target.originPosition()), 4.0);
        StructureDamageManager.applyDirectStructureDamage(source.getWorld(), target, dmgSource, amount);
        source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "§a[BobinDestruction] Нанесено %.1f урона объекту %s -> %.1f/%.1f HP [%s]",
                amount, target.structureId(), target.currentHealth(), target.maxHealth(), target.damageStage().name())), true);
        return 1;
    }

    public static int executeSetStage(ServerCommandSource source, String stageStr) {
        StructureDamageManager.ensureRegistryReady(source.getWorld());
        StructureDamageState stage = StructureDamageState.byName(stageStr);
        if (stage == null) {
            source.sendFeedback(() -> Text.literal("§c[BobinDestruction] Неизвестная стадия: " + stageStr + " (допустимы: intact, damaged, critical, destroying, destroyed или 0..4)"), false);
            return 0;
        }
        DestructibleStructure target = selectStructureForSource(source);
        if (target == null) {
            source.sendFeedback(() -> Text.literal("§c[BobinDestruction] Не найдена цель для смены стадии."), false);
            return 0;
        }
        StructureDamageManager.forceStage(source.getWorld(), target, stage);
        source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "§a[BobinDestruction] Стадия %s принудительно установлена в %s (HP: %.1f/%.1f)",
                target.structureId(), stage.name(), target.currentHealth(), target.maxHealth())), true);
        return 1;
    }

    public static int executeReset(ServerCommandSource source) {
        StructureDamageManager.ensureRegistryReady(source.getWorld());
        StructureDamageManager.resetAll(source.getWorld());
        source.sendFeedback(() -> Text.literal("§a[BobinDestruction] Все разрушаемые сооружения восстановлены в состояние INTACT."), true);
        return 1;
    }

    public static int executeDebug(ServerCommandSource source) {
        debugBoundsEnabled = !debugBoundsEnabled;
        if (source.getEntity() instanceof ServerPlayerEntity player && ServerPlayNetworking.canSend(player, DEBUG_TOGGLE_ID)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(debugBoundsEnabled);
            ServerPlayNetworking.send(player, DEBUG_TOGGLE_ID, buf);
        }
        source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "§e[BobinDestruction] Диагностическая визуализация областей: %s | Активных пожаров: %d | Очередь изменений: %d блоков (%d переходов)",
                debugBoundsEnabled ? "§aВКЛЮЧЕНА" : "§cВЫКЛЮЧЕНА",
                StructureFireManager.activeFireCount(),
                StructureUpdateQueue.totalQueuedBlockChanges(),
                StructureUpdateQueue.pendingTransitionCount())), false);
        return 1;
    }

    private static DestructibleStructure selectStructureForSource(ServerCommandSource source) {
        Vec3d pos = source.getPosition();
        if (source.getEntity() != null) {
            Vec3d eye = source.getEntity().getEyePos();
            Vec3d look = source.getEntity().getRotationVec(1.0f);
            for (int step = 2; step <= 64; step += 2) {
                Vec3d probe = eye.add(look.multiply(step));
                DestructibleStructure hit = DestructibleStructureRegistry.spatialIndex().findPrimaryAt(probe);
                if (hit != null) return hit;
            }
        }
        return DestructibleStructureRegistry.spatialIndex().findNearest(pos, 96.0);
    }
}
