package com.bobincraft.combat;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.PersistentState;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;

/** Per-dimension, bounded FIFO. New marks replace the oldest; no ticking decal entities are created. */
public final class CombatMarksState extends PersistentState {
    public static final int CAPACITY = 4096;
    private final ArrayDeque<SurfaceMark> marks = new ArrayDeque<>();
    private NbtCompound unsupported;
    private long revision;
    public List<SurfaceMark> marks() { return List.copyOf(marks); }
    public long revision() { return revision; }
    public boolean add(SurfaceMark mark) {
        if (unsupported != null || mark == null || !mark.valid()) return false;
        if (marks.size() == CAPACITY) marks.removeFirst();
        marks.addLast(mark); changed(); return true;
    }
    public void prune(Predicate<SurfaceMark> invalid) {
        if (unsupported == null && marks.removeIf(invalid)) changed();
    }
    private void changed() { revision++; markDirty(); }
    public static CombatMarksState read(NbtCompound nbt) {
        CombatMarksState result = new CombatMarksState();
        if (nbt.getInt("Version") > 1) { result.unsupported = nbt.copy(); return result; }
        NbtList list = nbt.getList("Marks", NbtElement.COMPOUND_TYPE);
        for (int i = Math.max(0, list.size() - CAPACITY); i < list.size(); i++) {
            SurfaceMark mark = SurfaceMark.read(list.getCompound(i));
            if (mark != null) result.marks.add(mark);
        }
        return result;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt) {
        if (unsupported != null) return unsupported.copy();
        nbt.putInt("Version", 1);
        NbtList list = new NbtList();
        for (SurfaceMark mark : marks) list.add(mark.write());
        nbt.put("Marks", list); return nbt;
    }
}
