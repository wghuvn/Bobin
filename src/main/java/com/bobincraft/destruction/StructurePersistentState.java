package com.bobincraft.destruction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Yarn Minecraft 1.20.1 compatible PersistentState for saving all destructible structure HP,
 * damage stages, destroyed logical components, transition progress, and active fires (Section 11).
 * Marks already-damaged structures in {@link DestructionScenarioDirector} upon load so scenarios
 * never replay when chunks or the world reload.
 */
public final class StructurePersistentState extends PersistentState {
    public static final int VERSION = 2;
    public static final String STATE_KEY = "bobin_craft_structures";

    private final Map<String, NbtCompound> savedStructures = new LinkedHashMap<>();
    private NbtCompound unsupportedData;

    public static StructurePersistentState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                StructurePersistentState::read,
                StructurePersistentState::new,
                STATE_KEY
        );
    }

    public static StructurePersistentState get(MinecraftServer server) {
        ServerWorld warzone = server.getWorld(com.bobincraft.warzone.WarzoneDimension.WORLD);
        return get(warzone != null ? warzone : server.getOverworld());
    }

    public static StructurePersistentState read(NbtCompound nbt) {
        StructurePersistentState state = new StructurePersistentState();
        if (nbt == null) return state;
        int version = nbt.getInt("Version");
        if (version > VERSION) {
            state.unsupportedData = nbt.copy();
            return state;
        }
        NbtList list = nbt.getList("Structures", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            String id = entry.getString("Id");
            if (id != null && !id.isEmpty()) {
                state.savedStructures.put(id, entry.copy());
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (unsupportedData != null) return unsupportedData.copy();
        nbt.putInt("Version", VERSION);
        NbtList list = new NbtList();
        for (NbtCompound entry : savedStructures.values()) {
            list.add(entry.copy());
        }
        nbt.put("Structures", list);
        return nbt;
    }

    public boolean supportsTransitions() {return unsupportedData==null;}

    public synchronized void captureFromRegistry() {
        if (unsupportedData != null) return;
        for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
            if (s.isModified()) {
                NbtCompound next=s.toCompactNbt();
                NbtCompound previous=savedStructures.get(s.structureId());
                if(previous!=null && !previous.contains("ComponentHealthKnown"))next.put("Legacy277",previous.copy());
                else if(previous!=null && previous.contains("Legacy277"))next.put("Legacy277",previous.getCompound("Legacy277").copy());
                savedStructures.put(s.structureId(),next);
            } else {
                savedStructures.remove(s.structureId());
            }
        }
        markDirty();
    }

    public synchronized void recordStructure(DestructibleStructure s) {
        if (unsupportedData != null || s == null) return;
        if (s.isModified()) {
            NbtCompound next=s.toCompactNbt();
                NbtCompound previous=savedStructures.get(s.structureId());
                if(previous!=null && !previous.contains("ComponentHealthKnown"))next.put("Legacy277",previous.copy());
                else if(previous!=null && previous.contains("Legacy277"))next.put("Legacy277",previous.getCompound("Legacy277").copy());
                savedStructures.put(s.structureId(),next);
        } else {
            savedStructures.remove(s.structureId());
        }
        DestructibleStructureRegistry.updateSnapshot(s);
        markDirty();
    }

    public synchronized void applyToRegistry(long currentWorldTick) {
        if (unsupportedData != null) return;
        for (DestructibleStructure s : DestructibleStructureRegistry.all()) {
            NbtCompound saved = savedStructures.get(s.structureId());
            if (saved != null) {
                s.fromCompactNbt(saved);
                // Simulate elapsed offline burn time if world advanced while unloaded
                if (s.isBurning() && currentWorldTick > s.lastUpdateTick() && s.lastUpdateTick() > 0) {
                    long elapsed = currentWorldTick - s.lastUpdateTick();
                    int nextBurn = Math.max(0, s.remainingBurnTime() - (int) Math.min(Integer.MAX_VALUE, elapsed));
                    s.setFireState(nextBurn > 0, s.fireIntensity(), nextBurn, currentWorldTick);
                }
                DestructionScenarioDirector.markLoadedStateWithoutReplay(s);
            } else {
                s.reset(currentWorldTick);
            }
            DestructibleStructureRegistry.updateSnapshot(s);
        }
    }

    public synchronized void clearAll(long currentWorldTick) {
        savedStructures.clear();
        DestructibleStructureRegistry.resetAll(currentWorldTick);
        markDirty();
    }

    public synchronized Map<String, NbtCompound> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(savedStructures));
    }
}
