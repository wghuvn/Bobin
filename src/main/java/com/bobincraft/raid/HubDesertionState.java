package com.bobincraft.raid;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A small separate ledger keeps punishment timers independent from raid plan migrations. */
final class HubDesertionState extends PersistentState {
    final Map<UUID, HubDesertionPolicy> players = new HashMap<>();

    static HubDesertionState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(
                HubDesertionState::read, HubDesertionState::new, "bobin_craft_deserters");
    }

    static HubDesertionState read(NbtCompound nbt) {
        HubDesertionState state = new HubDesertionState();
        NbtList players = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < players.size(); i++) {
            NbtCompound player = players.getCompound(i);
            if (player.containsUuid("UUID") && (player.getLong("Deadline") > 0 || player.getBoolean("Struck")))
                state.players.put(player.getUuid("UUID"), new HubDesertionPolicy(
                        player.getLong("Deadline"), player.getBoolean("Struck")));
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList players = new NbtList();
        this.players.forEach((uuid, policy) -> {
            NbtCompound player = new NbtCompound();
            player.putUuid("UUID", uuid);
            player.putLong("Deadline", policy.deadline());
            player.putBoolean("Struck", policy.struck());
            players.add(player);
        });
        nbt.put("Players", players);
        return nbt;
    }
}
