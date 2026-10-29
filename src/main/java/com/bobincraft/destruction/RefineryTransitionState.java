package com.bobincraft.destruction;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.util.*;

/** Separate journal leaves original 2.7.7 records intact; unknown future versions are read-only. */
public final class RefineryTransitionState extends PersistentState {
    public static final String KEY="bobin_craft_refinery_transitions";
    public final Map<String,TransitionLedger> ledgers=new LinkedHashMap<>();
    private final Map<String,NbtCompound> opaqueEntries=new LinkedHashMap<>();
    public boolean frozen(String id) {return opaqueEntries.containsKey(id);}
    private NbtCompound unsupported;
    public boolean writable() {return unsupported==null;}
    public static RefineryTransitionState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(RefineryTransitionState::read,RefineryTransitionState::new,KEY);
    }
    public static RefineryTransitionState read(NbtCompound n) {
        var state=new RefineryTransitionState();
        if(n.getInt("Version")!=1) {state.unsupported=n.copy();return state;}
        var list=n.getList("Structures",NbtElement.COMPOUND_TYPE);
        for(int i=0;i<list.size();i++) {
            var entry=list.getCompound(i);
            if(entry.getInt("Revision")!=StructureGeometrySnapshot.REVISION){state.opaqueEntries.put(entry.getString("Id"),entry.copy());continue;}
            var l=new TransitionLedger();
            l.operation=entry.containsUuid("Operation")?entry.getUuid("Operation"):UUID.randomUUID();
            l.revision=entry.getInt("Revision");l.variant=entry.getInt("Variant");l.stage=entry.getInt("Stage");
            var cells=entry.getList("Cells",NbtElement.COMPOUND_TYPE);
            for(int j=0;j<cells.size();j++) {
                var c=cells.getCompound(j);long pos=c.getLong("Pos");
                if(c.contains("Owned")) l.owned.put(pos,c.getString("Owned"));
                if(c.contains("Target")) l.pending.put(pos,c.getString("Target"));
                if(c.getBoolean("Protected"))l.protectedCells.add(pos);
                if(c.getBoolean("Conflict"))l.conflicts.add(pos);
            }
            state.ledgers.put(entry.getString("Id"),l);
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound n) {
        if(unsupported!=null)return unsupported.copy();
        n.putInt("Version",1);var list=new NbtList();
        for(var raw:opaqueEntries.values())list.add(raw.copy());
        for(var e:ledgers.entrySet()) {
            var l=e.getValue();var entry=new NbtCompound();entry.putString("Id",e.getKey());
            entry.putUuid("Operation",l.operation);entry.putInt("Revision",l.revision);entry.putInt("Variant",l.variant);entry.putInt("Stage",l.stage);
            Set<Long> positions=new TreeSet<>(l.owned.keySet());positions.addAll(l.pending.keySet());positions.addAll(l.protectedCells);positions.addAll(l.conflicts);
            var cells=new NbtList();
            for(long pos:positions){var c=new NbtCompound();c.putLong("Pos",pos);
                if(l.owned.containsKey(pos))c.putString("Owned",l.owned.get(pos));
                if(l.pending.containsKey(pos))c.putString("Target",l.pending.get(pos));
                if(l.protectedCells.contains(pos))c.putBoolean("Protected",true);
                if(l.conflicts.contains(pos))c.putBoolean("Conflict",true);cells.add(c);}
            entry.put("Cells",cells);list.add(entry);
        }
        n.put("Structures",list);return n;
    }
}
