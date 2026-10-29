package com.bobincraft.weapon.grom7;

import com.bobincraft.BobinCraft;
import com.bobincraft.drone.DroneSignal;
import com.bobincraft.raid.HubSafety;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;

/**
 * Authoritative manager for the GROM-7 remote charge binding system (max 3 bound charges per remote,
 * persistent in Remote NBT across item switches & world reloads), LMB charge binding with LED blink &
 * acoustic confirmation, manual unbinding/replacement, obstacle/distance-delayed radio detonation,
 * and the 6-stage misfire animation when signal is too weak.
 */
public final class Grom7Manager {
    public static final Identifier ANIMATION_PACKET = new Identifier(BobinCraft.MOD_ID, "grom7_anim");
    public static final Identifier HUD_SYNC_PACKET = new Identifier(BobinCraft.MOD_ID, "grom7_hud");
    public static final Identifier REMOTE_ACTION_C2S = new Identifier(BobinCraft.MOD_ID, "grom7_remote_action");

    public static final byte REMOTE_ACTION_LMB_BIND = 1;
    public static final byte REMOTE_ACTION_UNBIND_SELECTED = 2;

    /** Maximum number of GROM-7 charges that can be bound to a single remote. */
    public static final int MAX_BOUND_CHARGES = 3;
    public static final double MAX_BIND_REACH = 5.5;
    public static final String NBT_BOUND_LIST = "BoundCharges";
    public static final String NBT_SELECTED_SLOT = "SelectedSlot";

    public enum SignalLevel {
        HIGH("высокий", 2, true),
        MEDIUM("средний", 8, true),
        WEAK("слабый", 18, true),
        LOST("нет связи", -1, false);

        public final String hudText;
        public final int activationDelayTicks;
        public final boolean canActivate;

        SignalLevel(String hudText, int activationDelayTicks, boolean canActivate) {
            this.hudText = hudText;
            this.activationDelayTicks = activationDelayTicks;
            this.canActivate = canActivate;
        }
    }

    public record SignalEvaluation(
            float rawStrength,
            double distance,
            double obstruction,
            SignalLevel level,
            int dynamicDelayTicks
    ) {
        public SignalEvaluation(float rawStrength, double distance, double obstruction, SignalLevel level) {
            this(rawStrength, distance, obstruction, level, computeDelayTicks(distance, obstruction, level));
        }

        public static int computeDelayTicks(double distance, double obstruction, SignalLevel level) {
            if (!level.canActivate) return -1;
            int distDelay = (int) Math.round(2.0 + Math.max(0.0, distance) * 0.22 + Math.max(0.0, obstruction) * 3.2);
            return Math.max(level.activationDelayTicks, Math.min(60, distDelay));
        }
    }

    public record ChargeSummary(
            UUID chargeId,
            int chargeNumber,
            Grom7State state,
            Vec3d position,
            Grom7ChargeEntity entityRef
    ) {}

    private record PendingAction(
            ServerPlayerEntity player,
            Hand hand,
            ItemStack stack,
            Grom7Timing.Mode mode,
            long startTick,
            RegistryKey<World> worldKey,
            Grom7Placement.PlacementResult attachPlacement
    ) {}

    /** All live charges in the world indexed by chargeId and by ownerUuid. */
    private static final Map<UUID, ChargeSummary> ALL_LIVE_CHARGES = new HashMap<>();
    private static final Map<UUID, List<ChargeSummary>> PLAYER_CHARGES = new HashMap<>();
    /** Ordered list of bound chargeIds (max 3) per player, synchronized with remote ItemStack NBT. */
    private static final Map<UUID, List<UUID>> PLAYER_BOUND_IDS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> DESTROYED_CHARGE_IDS = new HashMap<>();
    private static final Map<UUID, Integer> SELECTED_CHARGE_INDEX = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_CHARGE_NUMBER = new HashMap<>();
    private static final Map<UUID, PendingAction> PENDING_ACTIONS = new HashMap<>();

    private Grom7Manager() {}

    public static synchronized void clearAll() {
        ALL_LIVE_CHARGES.clear();
        PLAYER_CHARGES.clear();
        PLAYER_BOUND_IDS.clear();
        DESTROYED_CHARGE_IDS.clear();
        SELECTED_CHARGE_INDEX.clear();
        NEXT_CHARGE_NUMBER.clear();
        PENDING_ACTIONS.clear();
    }

    public static synchronized int allocateChargeNumber(UUID ownerUuid) {
        if (ownerUuid == null) return 1;
        int next = NEXT_CHARGE_NUMBER.getOrDefault(ownerUuid, 1);
        NEXT_CHARGE_NUMBER.put(ownerUuid, next + 1);
        return next;
    }

    public static synchronized void registerOrUpdateCharge(Grom7ChargeEntity entity) {
        if (entity == null || entity.getOwnerUuid() == null) return;
        UUID owner = entity.getOwnerUuid();
        UUID cid = entity.getChargeId();
        List<ChargeSummary> list = PLAYER_CHARGES.computeIfAbsent(owner, k -> new ArrayList<>());
        list.removeIf(c -> c.chargeId().equals(cid) || c.state() == Grom7State.DESTROYED
                || (c.entityRef() != null && c.entityRef().isRemoved()));
        if (entity.getChargeState() != Grom7State.DESTROYED && !entity.isRemoved()) {
            ChargeSummary summary = new ChargeSummary(
                    cid,
                    entity.getChargeNumber(),
                    entity.getChargeState(),
                    entity.getPos(),
                    entity
            );
            ALL_LIVE_CHARGES.put(cid, summary);
            list.add(summary);
            list.sort(Comparator.comparingInt(ChargeSummary::chargeNumber));

            // Keep entity's bound status synced with player's bound list
            List<UUID> bound = PLAYER_BOUND_IDS.get(owner);
            if (bound != null && bound.contains(cid) && !entity.isBoundToRemote()) {
                entity.setBoundToRemote(true);
            }
        }
    }

    public static synchronized void unregisterCharge(Grom7ChargeEntity entity) {
        if (entity == null) return;
        UUID cid = entity.getChargeId();
        ALL_LIVE_CHARGES.remove(cid);
        UUID owner = entity.getOwnerUuid();
        if (owner != null) {
            DESTROYED_CHARGE_IDS.computeIfAbsent(owner, k -> new HashSet<>()).add(cid);
            List<UUID> bound = PLAYER_BOUND_IDS.get(owner);
            if (bound != null) {
                bound.remove(cid);
            }
            List<ChargeSummary> list = PLAYER_CHARGES.get(owner);
            if (list != null) {
                list.removeIf(c -> c.chargeId().equals(cid));
                if (list.isEmpty()) {
                    PLAYER_CHARGES.remove(owner);
                    NEXT_CHARGE_NUMBER.remove(owner);
                }
            }
            clampSelectedIndex(owner);
        }
    }

    private static void clampSelectedIndex(UUID owner) {
        List<ChargeSummary> bound = getBoundCharges(owner);
        if (bound.isEmpty()) {
            SELECTED_CHARGE_INDEX.remove(owner);
        } else {
            int idx = SELECTED_CHARGE_INDEX.getOrDefault(owner, 0);
            if (idx >= bound.size()) {
                SELECTED_CHARGE_INDEX.put(owner, Math.max(0, bound.size() - 1));
            }
        }
    }

    /**
     * Synchronizes the persistent {@code BoundCharges} NBT list on the player's held {@code grom7_remote}
     * with the server's active charges, automatically removing any detonated or destroyed charges and
     * restoring bound IDs after hotbar switches or world reloads.
     */
    public static synchronized void syncRemoteStackNbt(ServerPlayerEntity player, ItemStack remoteStack) {
        if (player == null || remoteStack == null || !remoteStack.isOf(Grom7Content.GROM7_REMOTE)) return;
        UUID owner = player.getUuid();
        Set<UUID> destroyed = DESTROYED_CHARGE_IDS.getOrDefault(owner, Collections.emptySet());
        List<UUID> memBound = PLAYER_BOUND_IDS.computeIfAbsent(owner, k -> new ArrayList<>());

        NbtCompound nbt = remoteStack.getNbt();
        if (nbt != null && nbt.contains(NBT_BOUND_LIST, NbtElement.LIST_TYPE)) {
            NbtList nbtList = nbt.getList(NBT_BOUND_LIST, NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < nbtList.size(); i++) {
                NbtCompound entry = nbtList.getCompound(i);
                if (entry.containsUuid("Id")) {
                    UUID cid = entry.getUuid("Id");
                    if (!destroyed.contains(cid) && !memBound.contains(cid) && memBound.size() < MAX_BOUND_CHARGES) {
                        memBound.add(cid);
                    }
                }
            }
            if (nbt.contains(NBT_SELECTED_SLOT, NbtElement.INT_TYPE) && !SELECTED_CHARGE_INDEX.containsKey(owner)) {
                SELECTED_CHARGE_INDEX.put(owner, Math.max(0, nbt.getInt(NBT_SELECTED_SLOT)));
            }
        }

        // Remove any destroyed or missing charges in loaded chunks
        memBound.removeIf(cid -> {
            if (destroyed.contains(cid)) return true;
            ChargeSummary sum = ALL_LIVE_CHARGES.get(cid);
            if (sum != null && (sum.state() == Grom7State.DESTROYED || (sum.entityRef() != null && sum.entityRef().isRemoved()))) {
                return true;
            }
            return false;
        });
        while (memBound.size() > MAX_BOUND_CHARGES) {
            memBound.remove(0);
        }
        clampSelectedIndex(owner);

        // Write cleaned bindings back to remote ItemStack NBT
        NbtCompound outNbt = remoteStack.getOrCreateNbt();
        NbtList outList = new NbtList();
        for (UUID cid : memBound) {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Id", cid);
            ChargeSummary sum = ALL_LIVE_CHARGES.get(cid);
            if (sum != null) {
                entry.putInt("Num", sum.chargeNumber());
                if (sum.entityRef() != null && !sum.entityRef().isBoundToRemote()) {
                    sum.entityRef().setBoundToRemote(true);
                }
            }
            outList.add(entry);
        }
        outNbt.put(NBT_BOUND_LIST, outList);
        outNbt.putInt(NBT_SELECTED_SLOT, SELECTED_CHARGE_INDEX.getOrDefault(owner, 0));
    }

    /**
     * Binds a specific placed charge to the player's remote (up to {@link #MAX_BOUND_CHARGES} = 3).
     * If already bound and {@code allowToggleUnbind} is true, manually unbinds it.
     * If 3 charges are already bound, replaces the currently selected slot.
     */
    public static synchronized boolean bindChargeToRemote(
            ServerPlayerEntity player, ItemStack remoteStack, Grom7ChargeEntity targetCharge, boolean allowToggleUnbind) {
        if (player == null || targetCharge == null || !targetCharge.isAlive()
                || targetCharge.getChargeState() == Grom7State.DESTROYED) {
            return false;
        }
        registerOrUpdateCharge(targetCharge);
        if (remoteStack != null && remoteStack.isOf(Grom7Content.GROM7_REMOTE)) {
            syncRemoteStackNbt(player, remoteStack);
        }

        UUID owner = player.getUuid();
        UUID cid = targetCharge.getChargeId();
        List<UUID> bound = PLAYER_BOUND_IDS.computeIfAbsent(owner, k -> new ArrayList<>());

        if (bound.contains(cid)) {
            if (allowToggleUnbind) {
                bound.remove(cid);
                targetCharge.setBoundToRemote(false);
                clampSelectedIndex(owner);
                if (remoteStack != null && remoteStack.isOf(Grom7Content.GROM7_REMOTE)) {
                    syncRemoteStackNbt(player, remoteStack);
                }
                player.getServerWorld().playSound(
                        null, player.getBlockPos(), Grom7Content.GROM7_CLICK_SOUND, SoundCategory.PLAYERS, 0.65f, 0.85f);
                player.sendMessage(Text.literal("§e[ГРОМ-7] Заряд #" + String.format("%02d", targetCharge.getChargeNumber()) + " отвязан (" + bound.size() + "/" + MAX_BOUND_CHARGES + ")"), true);
                syncHudToPlayer(player);
                return true;
            }
            // Select this already-bound charge and blink confirmation
            SELECTED_CHARGE_INDEX.put(owner, bound.indexOf(cid));
            targetCharge.triggerBindConfirmationBlink();
            playBindConfirmationSound(player.getServerWorld(), player, targetCharge);
            if (remoteStack != null && remoteStack.isOf(Grom7Content.GROM7_REMOTE)) {
                syncRemoteStackNbt(player, remoteStack);
            }
            syncHudToPlayer(player);
            return true;
        }

        // Binding a new charge (max 3; if full, replace currently selected slot)
        if (bound.size() >= MAX_BOUND_CHARGES) {
            int replaceIdx = Math.floorMod(SELECTED_CHARGE_INDEX.getOrDefault(owner, 0), MAX_BOUND_CHARGES);
            UUID evictedId = bound.get(replaceIdx);
            ChargeSummary evicted = ALL_LIVE_CHARGES.get(evictedId);
            if (evicted != null && evicted.entityRef() != null) {
                evicted.entityRef().setBoundToRemote(false);
            }
            bound.set(replaceIdx, cid);
            SELECTED_CHARGE_INDEX.put(owner, replaceIdx);
        } else {
            bound.add(cid);
            SELECTED_CHARGE_INDEX.put(owner, bound.size() - 1);
        }

        targetCharge.setOwnerUuid(owner);
        targetCharge.triggerBindConfirmationBlink();
        registerOrUpdateCharge(targetCharge);
        if (remoteStack != null && remoteStack.isOf(Grom7Content.GROM7_REMOTE)) {
            syncRemoteStackNbt(player, remoteStack);
        }
        playBindConfirmationSound(player.getServerWorld(), player, targetCharge);
        broadcastAnimation(player, Hand.MAIN_HAND, Grom7Timing.Mode.REMOTE_BIND, player.getServerWorld().getTime());
        player.sendMessage(Text.literal("§a[ГРОМ-7] Заряд #" + String.format("%02d", targetCharge.getChargeNumber()) + " привязан (" + bound.size() + "/" + MAX_BOUND_CHARGES + ")"), true);
        syncHudToPlayer(player);
        return true;
    }

    private static void playBindConfirmationSound(ServerWorld world, ServerPlayerEntity player, Grom7ChargeEntity charge) {
        world.playSound(null, player.getBlockPos(), Grom7Content.GROM7_CLICK_SOUND, SoundCategory.PLAYERS, 0.70f, 1.35f);
        world.playSound(null, charge.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.65f, 1.85f);
    }

    /**
     * Raycasts from the player's eyes up to {@link #MAX_BIND_REACH} to find the targeted placed {@link Grom7ChargeEntity}.
     */
    public static Grom7ChargeEntity findLookedAtCharge(World world, PlayerEntity player) {
        if (world == null || player == null) return null;
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d end = eye.add(look.multiply(MAX_BIND_REACH));
        var blockHit = world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        double maxDistSq = blockHit.getType() == HitResult.Type.MISS
                ? MAX_BIND_REACH * MAX_BIND_REACH
                : eye.squaredDistanceTo(blockHit.getPos()) + 0.35;

        Grom7ChargeEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Box search = new Box(eye, end).expand(0.6);
        for (Grom7ChargeEntity candidate : world.getEntitiesByClass(
                Grom7ChargeEntity.class, search, c -> c.isAlive() && c.getChargeState() != Grom7State.DESTROYED)) {
            double distSq = eye.squaredDistanceTo(candidate.getPos());
            if (distSq > maxDistSq) continue;
            Box hitBox = candidate.getBoundingBox().expand(0.22);
            var hitOpt = hitBox.raycast(eye, end);
            if (hitOpt.isPresent()) {
                double d = eye.squaredDistanceTo(hitOpt.get());
                if (d < bestScore) {
                    bestScore = d;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * Handles LMB click while holding {@code grom7_remote}: binds (or with Shift unbinds) the looked-at charge.
     */
    public static synchronized boolean handleRemoteLeftClick(ServerPlayerEntity player) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(Grom7Content.GROM7_REMOTE)) {
            stack = player.getOffHandStack();
            if (!stack.isOf(Grom7Content.GROM7_REMOTE)) return false;
        }
        Grom7ChargeEntity target = findLookedAtCharge(player.getServerWorld(), player);
        if (target == null) return false;
        return bindChargeToRemote(player, stack, target, player.isSneaking());
    }

    /**
     * Manually unbinds the currently selected charge from the player's remote (triggered via {@code R} key).
     */
    public static synchronized boolean unbindSelectedCharge(ServerPlayerEntity player) {
        if (player == null) return false;
        ItemStack stack = player.getMainHandStack().isOf(Grom7Content.GROM7_REMOTE)
                ? player.getMainHandStack()
                : (player.getOffHandStack().isOf(Grom7Content.GROM7_REMOTE) ? player.getOffHandStack() : null);
        if (stack != null) {
            syncRemoteStackNbt(player, stack);
        }
        UUID owner = player.getUuid();
        List<UUID> bound = PLAYER_BOUND_IDS.get(owner);
        if (bound == null || bound.isEmpty()) return false;
        int idx = Math.floorMod(SELECTED_CHARGE_INDEX.getOrDefault(owner, 0), bound.size());
        UUID removedId = bound.remove(idx);
        ChargeSummary sum = ALL_LIVE_CHARGES.get(removedId);
        if (sum != null && sum.entityRef() != null) {
            sum.entityRef().setBoundToRemote(false);
        }
        clampSelectedIndex(owner);
        if (stack != null) {
            syncRemoteStackNbt(player, stack);
        }
        player.getServerWorld().playSound(
                null, player.getBlockPos(), Grom7Content.GROM7_CLICK_SOUND, SoundCategory.PLAYERS, 0.65f, 0.82f);
        player.sendMessage(Text.literal("§e[ГРОМ-7] Слот освобождён (" + bound.size() + "/" + MAX_BOUND_CHARGES + ")"), true);
        syncHudToPlayer(player);
        return true;
    }

    public static synchronized List<ChargeSummary> getBoundCharges(UUID ownerUuid) {
        if (ownerUuid == null) return List.of();
        List<UUID> boundIds = PLAYER_BOUND_IDS.get(ownerUuid);
        if (boundIds != null && !boundIds.isEmpty()) {
            List<ChargeSummary> result = new ArrayList<>(boundIds.size());
            Iterator<UUID> it = boundIds.iterator();
            while (it.hasNext()) {
                UUID cid = it.next();
                ChargeSummary sum = ALL_LIVE_CHARGES.get(cid);
                if (sum == null) continue;
                if (sum.state() == Grom7State.DESTROYED || (sum.entityRef() != null && sum.entityRef().isRemoved())) {
                    it.remove();
                    continue;
                }
                result.add(sum);
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    public static synchronized List<ChargeSummary> getActiveCharges(UUID ownerUuid) {
        if (ownerUuid == null) return List.of();
        List<ChargeSummary> bound = getBoundCharges(ownerUuid);
        if (!bound.isEmpty()) return bound;
        List<ChargeSummary> list = PLAYER_CHARGES.get(ownerUuid);
        if (list == null || list.isEmpty()) return List.of();
        list.removeIf(c -> c.state() == Grom7State.DESTROYED || (c.entityRef() != null && c.entityRef().isRemoved()));
        return List.copyOf(list.subList(0, Math.min(MAX_BOUND_CHARGES, list.size())));
    }

    public static synchronized Optional<ChargeSummary> getSelectedCharge(UUID ownerUuid) {
        List<ChargeSummary> active = getActiveCharges(ownerUuid);
        if (active.isEmpty()) return Optional.empty();
        int idx = Math.floorMod(SELECTED_CHARGE_INDEX.getOrDefault(ownerUuid, 0), active.size());
        SELECTED_CHARGE_INDEX.put(ownerUuid, idx);
        return Optional.of(active.get(idx));
    }

    public static synchronized Optional<ChargeSummary> cycleSelectedCharge(UUID ownerUuid) {
        List<ChargeSummary> active = getActiveCharges(ownerUuid);
        if (active.isEmpty()) return Optional.empty();
        int nextIdx = (SELECTED_CHARGE_INDEX.getOrDefault(ownerUuid, 0) + 1) % active.size();
        SELECTED_CHARGE_INDEX.put(ownerUuid, nextIdx);
        return Optional.of(active.get(nextIdx));
    }

    /**
     * Evaluates radio link quality between operator and charge, accounting for distance and
     * heavy attenuation through walls, concrete, and metal structures.
     */
    public static SignalEvaluation evaluateSignal(ServerWorld world, Vec3d operatorEyePos, Vec3d chargePos) {
        if (operatorEyePos == null || chargePos == null) {
            return new SignalEvaluation(0.0f, Double.POSITIVE_INFINITY, 12.0, SignalLevel.LOST);
        }
        double distance = operatorEyePos.distanceTo(chargePos);
        double obstruction = world != null ? evaluateObstruction(world, operatorEyePos, chargePos) : 0.0;
        return evaluateSignalMetrics(distance, Grom7Content.maxRemoteRange, obstruction);
    }

    public static double evaluateObstruction(ServerWorld world, Vec3d from, Vec3d to) {
        int samples = Math.min(512, Math.max(1, (int) Math.ceil(from.distanceTo(to) * 1.5)));
        double loss = 0.0;
        BlockPos previous = null;
        for (int i = 1; i <= samples; i++) {
            BlockPos pos = BlockPos.ofFloored(from.lerp(to, i / (double) samples));
            if (pos.equals(previous)) continue;
            previous = pos;
            if (!world.isInBuildLimit(pos)) return 12.0;
            if (!world.isChunkLoaded(pos)) continue;
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.isOf(Blocks.WATER)) {
                loss += 0.10;
            } else if (state.isIn(BlockTags.LEAVES)) {
                loss += 0.025;
            } else if (state.getSoundGroup() == BlockSoundGroup.GLASS) {
                loss += 0.04;
            } else if (state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.LOGS)) {
                loss += 0.10;
            } else if (!state.getCollisionShape(world, pos).isEmpty()) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
                if (state.getSoundGroup() == BlockSoundGroup.METAL
                        || blockId.contains("metal")
                        || blockId.contains("container")
                        || blockId.contains("iron")
                        || blockId.contains("busbar")) {
                    // Metal structures strongly attenuate radio waves
                    loss += 0.42;
                } else if (blockId.contains("concrete")
                        || blockId.contains("bunker")
                        || blockId.contains("cinderblock")
                        || blockId.contains("brick")) {
                    // Reinforced concrete and bunker walls attenuate radio waves
                    loss += 0.28;
                } else {
                    loss += 0.18;
                }
            }
            if (loss >= 12.0) return 12.0;
        }
        return loss;
    }

    public static SignalEvaluation evaluateSignalMetrics(double distance, double maxRange, double obstruction) {
        float strength = DroneSignal.strength(distance, maxRange, obstruction);
        SignalLevel level;
        if (strength >= 0.65f) {
            level = SignalLevel.HIGH;
        } else if (strength >= 0.30f) {
            level = SignalLevel.MEDIUM;
        } else if (strength >= 0.04f) {
            level = SignalLevel.WEAK;
        } else {
            level = SignalLevel.LOST;
        }
        return new SignalEvaluation(strength, distance, obstruction, level);
    }

    /**
     * Attempts to activate the player's currently selected bound charge via radio signal.
     * On weak/lost signal, returns {@code false} and leaves the charge bound so the player can retry.
     */
    public static synchronized boolean tryActivateSelectedCharge(ServerWorld world, UUID ownerUuid, Vec3d operatorEyePos) {
        Optional<ChargeSummary> selectedOpt = getSelectedCharge(ownerUuid);
        if (selectedOpt.isEmpty()) {
            return false;
        }
        ChargeSummary selected = selectedOpt.get();
        SignalEvaluation sig = evaluateSignal(world, operatorEyePos, selected.position());
        if (!sig.level().canActivate) {
            // Signal lost / blocked -> do NOT detonate and do NOT unbind; caller triggers 6-stage REMOTE_FAIL misfire animation
            return false;
        }
        if (selected.entityRef() != null) {
            return selected.entityRef().triggerRemoteActivation(sig.dynamicDelayTicks());
        }
        return true;
    }

    public static boolean canUse(PlayerEntity player, Hand hand) {
        return player != null && !player.isRemoved() && player.isAlive()
                && !player.isSpectator() && !player.hasVehicle()
                && player.getWorld() != null && !(player.getWorld() instanceof ServerWorld sw && HubSafety.isHub(sw))
                && com.bobincraft.combat.PlayerInjuries.usableHand(player, hand);
    }

    public static boolean beginThrow(ServerPlayerEntity player, Hand hand) {
        if (!canUse(player, hand) || PENDING_ACTIONS.containsKey(player.getUuid())) return false;
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Grom7Content.GROM7_CHARGE)) return false;

        long now = player.getServerWorld().getTime();
        PendingAction action = new PendingAction(
                player, hand, stack, Grom7Timing.Mode.THROW, now, player.getWorld().getRegistryKey(), null);
        PENDING_ACTIONS.put(player.getUuid(), action);
        broadcastAnimation(player, hand, Grom7Timing.Mode.THROW, now);
        player.getItemCooldownManager().set(Grom7Content.GROM7_CHARGE, Grom7Timing.THROW_END_TICKS);
        return true;
    }

    public static boolean beginAttach(ServerPlayerEntity player, Hand hand) {
        if (!canUse(player, hand) || PENDING_ACTIONS.containsKey(player.getUuid())) {
            return false;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Grom7Content.GROM7_CHARGE)) {
            return false;
        }
        ServerWorld serverWorld = player.getServerWorld();
        Grom7Placement.PlacementResult serverPlacement = Grom7Placement.raycastAndEvaluate(serverWorld, player);
        if (!serverPlacement.valid()) {
            return false;
        }
        return beginAttach(player, hand, serverPlacement);
    }

    public static boolean beginAttach(ServerPlayerEntity player, Hand hand, Grom7Placement.PlacementResult placement) {
        if (!canUse(player, hand) || placement == null || !placement.valid() || PENDING_ACTIONS.containsKey(player.getUuid())) {
            return false;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Grom7Content.GROM7_CHARGE)) return false;

        ServerWorld serverWorld = player.getServerWorld();
        Grom7Placement.PlacementResult verified = Grom7Placement.revalidateServerPlacement(serverWorld, player, placement);
        if (!verified.valid()) {
            return false;
        }

        long now = serverWorld.getTime();
        PendingAction action = new PendingAction(
                player, hand, stack, Grom7Timing.Mode.ATTACH, now, player.getWorld().getRegistryKey(), verified);
        PENDING_ACTIONS.put(player.getUuid(), action);
        broadcastAnimation(player, hand, Grom7Timing.Mode.ATTACH, now);
        player.getItemCooldownManager().set(Grom7Content.GROM7_CHARGE, Grom7Timing.ATTACH_END_TICKS);
        return true;
    }

    public static boolean beginRemoteUse(ServerPlayerEntity player, Hand hand) {
        if (!canUse(player, hand) || PENDING_ACTIONS.containsKey(player.getUuid())) return false;
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Grom7Content.GROM7_REMOTE)) return false;

        syncRemoteStackNbt(player, stack);
        ServerWorld world = player.getServerWorld();
        long now = world.getTime();

        if (player.isSneaking()) {
            Optional<ChargeSummary> next = cycleSelectedCharge(player.getUuid());
            syncRemoteStackNbt(player, stack);
            world.playSound(null, player.getBlockPos(), Grom7Content.GROM7_CLICK_SOUND,
                    SoundCategory.PLAYERS, 0.55f, 1.25f);
            syncHudToPlayer(player);
            player.getItemCooldownManager().set(Grom7Content.GROM7_REMOTE, 6);
            return next.isPresent();
        }

        boolean success = tryActivateSelectedCharge(world, player.getUuid(), player.getEyePos());
        Grom7Timing.Mode animMode = success ? Grom7Timing.Mode.REMOTE_SUCCESS : Grom7Timing.Mode.REMOTE_FAIL;
        PendingAction action = new PendingAction(
                player, hand, stack, animMode, now, world.getRegistryKey(), null);
        PENDING_ACTIONS.put(player.getUuid(), action);
        broadcastAnimation(player, hand, animMode, now);
        player.getItemCooldownManager().set(Grom7Content.GROM7_REMOTE, animMode.durationTicks);
        syncHudToPlayer(player);
        return true;
    }

    private static void broadcastAnimation(ServerPlayerEntity player, Hand hand, Grom7Timing.Mode mode, long startTick) {
        Set<ServerPlayerEntity> viewers = new HashSet<>(PlayerLookup.tracking(player));
        viewers.add(player);
        for (ServerPlayerEntity viewer : viewers) {
            if (ServerPlayNetworking.canSend(viewer, ANIMATION_PACKET)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeUuid(player.getUuid());
                buf.writeBoolean(hand == Hand.MAIN_HAND);
                buf.writeByte(mode.ordinal());
                buf.writeLong(startTick);
                ServerPlayNetworking.send(viewer, ANIMATION_PACKET, buf);
            }
        }
    }

    public static void syncHudToPlayer(ServerPlayerEntity player) {
        if (player == null || !ServerPlayNetworking.canSend(player, HUD_SYNC_PACKET)) return;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        if (main.isOf(Grom7Content.GROM7_REMOTE)) syncRemoteStackNbt(player, main);
        else if (off.isOf(Grom7Content.GROM7_REMOTE)) syncRemoteStackNbt(player, off);

        Optional<ChargeSummary> selOpt = getSelectedCharge(player.getUuid());
        int totalCount = getActiveCharges(player.getUuid()).size();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(totalCount);
        if (selOpt.isPresent()) {
            ChargeSummary sel = selOpt.get();
            SignalEvaluation sig = evaluateSignal(player.getServerWorld(), player.getEyePos(), sel.position());
            buf.writeInt(sel.chargeNumber());
            buf.writeString(sel.state().hudName);
            buf.writeString(sig.level().hudText);
            buf.writeInt((int) Math.round(sig.distance()));
        } else {
            buf.writeInt(0);
            buf.writeString("нет");
            buf.writeString("нет связи");
            buf.writeInt(0);
        }
        ServerPlayNetworking.send(player, HUD_SYNC_PACKET, buf);
    }

    public static void tickServer(net.minecraft.server.MinecraftServer server) {
        Iterator<PendingAction> it = PENDING_ACTIONS.values().iterator();
        while (it.hasNext()) {
            PendingAction pending = it.next();
            ServerPlayerEntity p = pending.player();
            if (p.isDisconnected() || !canUse(p, pending.hand()) || !p.getWorld().getRegistryKey().equals(pending.worldKey())) {
                it.remove();
                continue;
            }
            ServerWorld world = p.getServerWorld();
            long age = world.getTime() - pending.startTick();

            if (pending.mode() == Grom7Timing.Mode.THROW) {
                if (age >= Grom7Timing.THROW_RELEASE_TICKS) {
                    spawnThrownCharge(p, pending.stack());
                    it.remove();
                }
            } else if (pending.mode() == Grom7Timing.Mode.ATTACH) {
                if (age >= Grom7Timing.ATTACH_FIX_TICKS) {
                    spawnAttachedCharge(p, pending.stack(), pending.attachPlacement());
                    it.remove();
                }
            } else if (pending.mode() == Grom7Timing.Mode.REMOTE_SUCCESS) {
                if (age == Grom7Timing.SUCCESS_RAISE_TICKS) {
                    world.playSound(null, p.getBlockPos(), Grom7Content.GROM7_BUTTON_SOUND,
                            SoundCategory.PLAYERS, 0.65f, 1.15f);
                }
                if (age >= Grom7Timing.SUCCESS_END_TICKS) {
                    it.remove();
                }
            } else if (pending.mode() == Grom7Timing.Mode.REMOTE_FAIL) {
                // Synchronized sound effects for the 6-stage misfire sequence:
                // 1 & 2: Thumb presses button -> dry mechanical click (tick 5)
                // 3: Red LED blinks (error beep at tick 9)
                // 5: Device shake (ticks 29, 34) + second button press dry click (tick 42)
                for (int clickTick : Grom7Timing.FAIL_BUTTON_SOUND_TICKS) {
                    if (age == clickTick) {
                        world.playSound(null, p.getBlockPos(), Grom7Content.GROM7_CLICK_SOUND,
                                SoundCategory.PLAYERS, 0.72f, clickTick == 5 ? 0.92f : 1.05f);
                    }
                }
                for (int shakeTick : Grom7Timing.FAIL_SLAP_SOUND_TICKS) {
                    if (age == shakeTick) {
                        world.playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                                SoundCategory.PLAYERS, 0.50f, 1.35f);
                    }
                }
                if (age == Grom7Timing.FAIL_ERROR_BEEP_TICK || age == 44) {
                    world.playSound(null, p.getBlockPos(), Grom7Content.GROM7_ERROR_SOUND,
                            SoundCategory.PLAYERS, 0.65f, 0.78f);
                }
                if (age >= Grom7Timing.FAIL_END_TICKS) {
                    it.remove();
                }
            } else if (pending.mode() == Grom7Timing.Mode.REMOTE_BIND) {
                if (age >= Grom7Timing.Mode.REMOTE_BIND.durationTicks) {
                    it.remove();
                }
            }
        }

        if (server.getTicks() % 5 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getMainHandStack().isOf(Grom7Content.GROM7_REMOTE)
                        || player.getOffHandStack().isOf(Grom7Content.GROM7_REMOTE)) {
                    syncHudToPlayer(player);
                }
            }
        }
    }

    private static void spawnThrownCharge(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Grom7Content.GROM7_CHARGE)) return;
        ServerWorld world = player.getServerWorld();
        Vec3d dir = player.getRotationVec(1.0f);
        Vec3d eye = player.getEyePos();
        Vec3d desired = eye.add(dir.multiply(0.58)).add(0.0, -0.10, 0.0);
        var hit = world.raycast(new RaycastContext(eye, desired, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d start = hit.getType() == HitResult.Type.MISS ? desired : hit.getPos().subtract(dir.multiply(0.12));

        Grom7ChargeEntity charge = Grom7Content.GROM7_CHARGE_ENTITY.create(world);
        if (charge == null) return;
        int chargeNum = allocateChargeNumber(player.getUuid());
        Vec3d vel = dir.multiply(0.68).add(player.getVelocity().multiply(0.25)).add(0.0, 0.08, 0.0);
        charge.configureThrown(player.getUuid(), chargeNum, start, vel, player.getYaw(), player.getPitch());
        if (world.spawnEntity(charge)) {
            registerOrUpdateCharge(charge);
            if (!player.isCreative()) {
                stack.decrement(1);
            }
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.55f, 0.72f);
            syncHudToPlayer(player);
        }
    }

    private static void spawnAttachedCharge(ServerPlayerEntity player, ItemStack stack, Grom7Placement.PlacementResult initialPlacement) {
        if (stack.isEmpty() || !stack.isOf(Grom7Content.GROM7_CHARGE) || initialPlacement == null || !initialPlacement.valid()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        Grom7Placement.PlacementResult livePlacement = Grom7Placement.raycastAndEvaluate(world, player);
        Grom7Placement.PlacementResult finalPlacement = livePlacement.valid()
                ? livePlacement
                : Grom7Placement.revalidateServerPlacement(world, player, initialPlacement);
        if (!finalPlacement.valid()) {
            return;
        }

        Grom7ChargeEntity charge = Grom7Content.GROM7_CHARGE_ENTITY.create(world);
        if (charge == null) return;
        int chargeNum = allocateChargeNumber(player.getUuid());
        charge.configureAttached(player.getUuid(), chargeNum, finalPlacement);
        if (world.spawnEntity(charge)) {
            registerOrUpdateCharge(charge);
            if (!player.isCreative()) {
                stack.decrement(1);
            }
            world.playSound(null, finalPlacement.attachmentBlockPos(), Grom7Content.GROM7_ATTACH_SOUND,
                    SoundCategory.PLAYERS, 0.8f, 1.0f);
            syncHudToPlayer(player);
        }
    }

    public static void notifyBlockChanged(World world, BlockPos pos) {
        if (world == null || world.isClient || pos == null) return;
        Box searchBox = new Box(pos).expand(1.5);
        for (Grom7ChargeEntity charge : world.getEntitiesByClass(Grom7ChargeEntity.class, searchBox, Grom7ChargeEntity::isAlive)) {
            if (charge.getChargeState() == Grom7State.ATTACHED && pos.equals(charge.getAttachmentBlockPos())) {
                charge.verifyOrDetachSupport(world);
            }
        }
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Grom7Manager::tickServer);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> notifyBlockChanged(world, pos));
        ServerPlayNetworking.registerGlobalReceiver(REMOTE_ACTION_C2S, (server, player, handler, buf, responseSender) -> {
            if (buf.readableBytes() < 1) return;
            byte action = buf.readByte();
            server.execute(() -> {
                if (action == REMOTE_ACTION_LMB_BIND) {
                    handleRemoteLeftClick(player);
                } else if (action == REMOTE_ACTION_UNBIND_SELECTED) {
                    unbindSelectedCharge(player);
                }
            });
        });
    }
}
