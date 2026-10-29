package com.bobincraft.combat;

import net.minecraft.nbt.NbtCompound;

public final class InjuryPersistence {
    private InjuryPersistence() { }

    public static void write(InjuryState state, NbtCompound entity) {
        InjuryState.Snapshot saved = state.snapshot();
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("Severity", saved.severity());
        nbt.putInt("RemainingTicks", saved.remainingTicks());
        nbt.putInt("FaceSeverity", saved.faceSeverity());
        nbt.putInt("MissingLimbs", saved.missingLimbs());
        entity.put("BobinInjuries", nbt);
    }

    public static void read(InjuryState state, NbtCompound entity) {
        NbtCompound nbt = entity.getCompound("BobinInjuries");
        state.restore(new InjuryState.Snapshot(nbt.getInt("Severity"), nbt.getInt("RemainingTicks"),
                nbt.getInt("FaceSeverity"), nbt.getInt("MissingLimbs")));
    }
}
