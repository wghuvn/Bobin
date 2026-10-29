package com.bobincraft.vehicle;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A reserved emplacement never respawns merely because its entity chunk is unloaded. */
public final class ArmorRoster extends PersistentState {
    public record Entry(UUID uuid,boolean destroyed) { }
    private final Map<Integer,Entry> entries=new TreeMap<>();
    private NbtCompound future;
    public static ArmorRoster get(ServerWorld world){return world.getPersistentStateManager().getOrCreate(ArmorRoster::read,ArmorRoster::new,"bobin_armored_positions");}
    public Entry entry(int site){return entries.get(site);}
    public UUID reserve(long seed,int site){if(future!=null||entries.containsKey(site))return null;UUID id=UUID.nameUUIDFromBytes(("bobin:armor:v1:"+seed+":"+site).getBytes(StandardCharsets.UTF_8));observe(site,id);return id;}
    public void observe(int site,UUID id){if(future==null&&!entries.containsKey(site)){entries.put(site,new Entry(id,false));markDirty();}}
    public void rejected(int site,UUID id){var row=entries.get(site);if(future==null&&row!=null&&!row.destroyed&&row.uuid.equals(id)){entries.remove(site);markDirty();}}
    public void destroyed(int site,UUID id){var entry=entries.get(site);if(future==null&&entry!=null&&entry.uuid.equals(id)){entries.put(site,new Entry(id,true));markDirty();}}
    public static ArmorRoster read(NbtCompound nbt){var state=new ArmorRoster();if(nbt.getInt("Version")>1){state.future=nbt.copy();return state;}
        var rows=nbt.getList("Vehicles",NbtElement.COMPOUND_TYPE);for(int i=0;i<Math.min(512,rows.size());i++){var row=rows.getCompound(i);if(row.containsUuid("UUID"))state.entries.putIfAbsent(row.getInt("Site"),new Entry(row.getUuid("UUID"),row.getBoolean("Destroyed")));}return state;}
    @Override public NbtCompound writeNbt(NbtCompound nbt){if(future!=null)return future.copy();nbt.putInt("Version",1);var rows=new NbtList();entries.forEach((site,entry)->{var row=new NbtCompound();row.putInt("Site",site);row.putUuid("UUID",entry.uuid);row.putBoolean("Destroyed",entry.destroyed);rows.add(row);});nbt.put("Vehicles",rows);return nbt;}
}
