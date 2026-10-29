package com.bobincraft.raid.guard;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-dimension, finite resident roster. An issued slot is never inferred dead from an unloaded entity.
 * UUID reservation happens before spawn; an interrupted spawn can lose one resident, never duplicate one.
 */
public final class CombatGuardRoster extends PersistentState {
    public enum Status { ISSUED, DEAD }
    public record Entry(UUID uuid, Status status) {}
    private final Map<Long, Entry> entries = new HashMap<>();
    private final boolean residential;
    private NbtCompound unsupported;

    public CombatGuardRoster() { this(false); }
    public CombatGuardRoster(boolean residential) { this.residential = residential; }

    public static CombatGuardRoster get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(CombatGuardRoster::read, CombatGuardRoster::new,
                "bobin_combat_garrison");
    }
    /** A new plan may reuse site numbers without adopting a saved military roster or its UUIDs. */
    public static CombatGuardRoster getResidential(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(nbt -> read(nbt, true),
                () -> new CombatGuardRoster(true), "bobin_forest_residents");
    }

    public static CombatGuardRoster forResident(ServerWorld world, boolean residential) {
        return residential ? getResidential(world) : get(world);
    }
    public boolean supported() { return unsupported == null; }
    private static long key(int site, int slot) { return ((long) site << 32) | (slot & 0xffffffffL); }
    public Entry entry(int site, int slot) { return entries.get(key(site, slot)); }
    public UUID reserve(long seed, int site, int slot) {
        if (!supported() || entries.containsKey(key(site, slot))) return null;
        UUID uuid = UUID.nameUUIDFromBytes(((residential ? "bobin:forest_residents:v1:" : "bobin:garrison:v1:")
                + seed + ":" + site + ":" + slot)
                .getBytes(StandardCharsets.UTF_8));
        entries.put(key(site, slot), new Entry(uuid, Status.ISSUED));
        markDirty();
        return uuid;
    }
    /** Only rollback a synchronous, explicitly rejected insertion; never infer this from unloading. */
    public void rejected(int site, int slot, UUID uuid) {
        Entry row=entry(site,slot);
        if(supported() && row!=null && row.status()==Status.ISSUED && row.uuid().equals(uuid)) {
            entries.remove(key(site,slot)); markDirty();
        }
    }
    public void died(int site, int slot, UUID uuid) {
        Entry entry = entry(site, slot);
        if (entry != null && entry.uuid().equals(uuid) && entry.status() != Status.DEAD) {
            entries.put(key(site, slot), new Entry(uuid, Status.DEAD));
            markDirty();
        }
    }
    /** Adopt a loaded resident before ticking spawns; this also protects saves restored from backups. */
    public void observe(int site, int slot, UUID uuid) {
        if (!supported() || entries.containsKey(key(site, slot))) return;
        entries.put(key(site, slot), new Entry(uuid, Status.ISSUED));
        markDirty();
    }
    public static CombatGuardRoster read(NbtCompound nbt) {
        return read(nbt, false);
    }

    public static CombatGuardRoster readResidential(NbtCompound nbt) { return read(nbt, true); }

    private static CombatGuardRoster read(NbtCompound nbt, boolean residential) {
        CombatGuardRoster state = new CombatGuardRoster(residential);
        if (nbt.getInt("Version") > 1) { state.unsupported = nbt.copy(); return state; }
        NbtList list = nbt.getList("Residents", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound row = list.getCompound(i);
            if (!row.containsUuid("UUID") || !row.contains("Site", NbtElement.INT_TYPE)
                    || !row.contains("Slot", NbtElement.INT_TYPE)) continue;
            Status status;
            try { status = Status.valueOf(row.getString("Status")); }
            catch (IllegalArgumentException ignored) { status = Status.ISSUED; }
            state.entries.putIfAbsent(key(row.getInt("Site"), row.getInt("Slot")), new Entry(row.getUuid("UUID"), status));
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        if (!supported()) return unsupported.copy();
        nbt.putInt("Version", 1);
        NbtList list = new NbtList();
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(item -> {
            NbtCompound row = new NbtCompound();
            row.putInt("Site", (int) (item.getKey() >> 32));
            row.putInt("Slot", (int) (long) item.getKey());
            row.putUuid("UUID", item.getValue().uuid());
            row.putString("Status", item.getValue().status().name());
            list.add(row);
        });
        nbt.put("Residents", list);
        return nbt;
    }
}
