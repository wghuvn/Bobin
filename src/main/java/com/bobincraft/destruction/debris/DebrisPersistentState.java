package com.bobincraft.destruction.debris;

import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Separate versioned write-ahead journal. Atomic/fsynced replacement must succeed before a reserved cell can be removed. */
public final class DebrisPersistentState {
    public static final int VERSION=1,MAX_RECORDS=8192,MAX_STORED_CELLS=65536;
    public final Map<UUID,DebrisFragment> fragments=new LinkedHashMap<>();
    public final Map<String,UUID> operations=new LinkedHashMap<>();
    public final Map<String,NbtCompound> structureSnapshots=new LinkedHashMap<>();
    private final Map<UUID,Long> durableRevisions=new HashMap<>();
    private final Map<String,UUID> durableOperations=new HashMap<>();
    private final Map<String,NbtCompound> durableStructures=new HashMap<>();
    private static final int FRAME_MAGIC=0x42444231,MAX_FRAME=32*1024*1024;
    private final Path path;
    private long sequence,validWalLength;
    public long lastFlushNanos,lastFlushBytes;
    private Path wal(){return path.resolveSibling(path.getFileName()+".wal");}
    private NbtCompound opaque;
    public boolean dirty,ioFailed;
    private DebrisPersistentState(Path path){this.path=path;}
    public static DebrisPersistentState open(ServerWorld world) {
        String dimension=world.getRegistryKey().getValue().toString();
        UUID key=UUID.nameUUIDFromBytes(dimension.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path path=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data/bobin_craft_debris/"+key+".dat");
        return load(path,dimension);
    }
    public static DebrisPersistentState load(Path path,String dimension) {
        var state=new DebrisPersistentState(path);
        try {
            if(Files.exists(path))state.read(NbtIo.readCompressed(path.toFile()),dimension);
            if(state.opaque==null)state.replay(dimension);
            if(state.opaque==null)state.validate();
            state.rememberDurable();
        }catch(Exception e){state.ioFailed=true;System.err.println("[Bobin debris] journal read blocked: "+e);}
        return state;
    }
    private void validate()throws IOException {
        Set<Long> tokens=new HashSet<>();Set<String> sources=new HashSet<>();
        for(var f:fragments.values()) {
            UUID expected=UUID.nameUUIDFromBytes((f.dimension+"/"+f.structure+"/"+f.operation+"/"+f.index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!expected.equals(f.id)||f.supportToken!=0&&!tokens.add(f.supportToken))throw new IOException("Debris identity/support-token conflict");
            for(var c:f.cells)if(!sources.add(f.operation+"/"+c.source))throw new IOException("Debris source committed by two fragments");
        }
    }
    public boolean writable(){return opaque==null&&!ioFailed;}
    private void read(NbtCompound n,String dimension) {
        if(n.getInt("Version")!=VERSION){opaque=n.copy();return;}
        sequence=n.getLong("Sequence");
        var snapshots=n.getList("StructureSnapshots",NbtElement.COMPOUND_TYPE);
        if(snapshots.size()>16384)throw new IllegalArgumentException("Structure proof limit");
        for(int i=0;i<snapshots.size();i++){var proof=snapshots.getCompound(i);structureSnapshots.put(proof.getString("Id"),proof.copy());}
        var list=n.getList("Fragments",NbtElement.COMPOUND_TYPE);
        if(list.size()>MAX_RECORDS)throw new IllegalArgumentException("Stored fragment limit");
        int count=0;
        for(int i=0;i<list.size();i++) {
            var f=decode(list.getCompound(i));if(!dimension.equals(f.dimension)||fragments.put(f.id,f)!=null)throw new IllegalArgumentException("Journal identity conflict");
            count+=f.cells.size();if(count>MAX_STORED_CELLS)throw new IllegalArgumentException("Stored cell limit");
        }
        var ops=n.getList("Operations",NbtElement.COMPOUND_TYPE);if(ops.size()>16384)throw new IllegalArgumentException("Operation limit");
        for(int i=0;i<ops.size();i++){var o=ops.getCompound(i);operations.put(o.getString("Key"),o.getUuid("Id"));}
    }
    public NbtCompound encode() {
        if(opaque!=null)return opaque.copy();var n=new NbtCompound();n.putInt("Version",VERSION);n.putLong("Sequence",sequence);
        var proofs=new NbtList();for(var proof:structureSnapshots.values())proofs.add(proof.copy());n.put("StructureSnapshots",proofs);
        var list=new NbtList();for(var f:fragments.values())list.add(encode(f));n.put("Fragments",list);
        var ops=new NbtList();for(var e:operations.entrySet()){var o=new NbtCompound();o.putString("Key",e.getKey());o.putUuid("Id",e.getValue());ops.add(o);}n.put("Operations",ops);return n;
    }
    private void rememberDurable() {
        durableRevisions.clear();for(var f:fragments.values())durableRevisions.put(f.id,f.revision);
        durableOperations.clear();durableOperations.putAll(operations);durableStructures.clear();for(var e:structureSnapshots.entrySet())durableStructures.put(e.getKey(),e.getValue().copy());
    }
    private void replay(String dimension)throws IOException {
        var journal=DebrisJournal.read(wal());validWalLength=journal.validBytes();
        for(var entry:journal.frames()) {
                long serial=entry.sequence();if(serial<=sequence)continue;
                var bytes=entry.payload();
                var n=NbtIo.readCompressed(new ByteArrayInputStream(bytes));
                if(n.getInt("Version")!=VERSION)throw new IOException("Unsupported debris WAL version");
                var upserts=n.getList("Upserts",NbtElement.COMPOUND_TYPE);if(upserts.size()>MAX_RECORDS)throw new IOException("Debris WAL count");
                for(int i=0;i<upserts.size();i++){var f=decode(upserts.getCompound(i));if(!dimension.equals(f.dimension))throw new IOException("Debris WAL dimension");fragments.put(f.id,f);}
                var removed=n.getList("Removed",NbtElement.COMPOUND_TYPE);for(int i=0;i<removed.size();i++)fragments.remove(removed.getCompound(i).getUuid("Id"));
                var ops=n.getList("Operations",NbtElement.COMPOUND_TYPE);for(int i=0;i<ops.size();i++){var op=ops.getCompound(i);operations.put(op.getString("Key"),op.getUuid("Id"));}
                var deletedOps=n.getList("DeletedOps",NbtElement.STRING_TYPE);for(int i=0;i<deletedOps.size();i++)operations.remove(deletedOps.getString(i));
                var proofs=n.getList("Structures",NbtElement.COMPOUND_TYPE);for(int i=0;i<proofs.size();i++){var proof=proofs.getCompound(i);structureSnapshots.put(proof.getString("Id"),proof.copy());}
                var deletedProofs=n.getList("DeletedStructures",NbtElement.STRING_TYPE);for(int i=0;i<deletedProofs.size();i++)structureSnapshots.remove(deletedProofs.getString(i));
                if(fragments.size()>MAX_RECORDS||fragments.values().stream().mapToInt(f->f.cells.size()).sum()>MAX_STORED_CELLS||operations.size()>16384||structureSnapshots.size()>16384)throw new IOException("Debris WAL storage bounds");
                sequence=serial;
        }
    }
    public boolean flush() {
        if(!writable())return false;if(!dirty)return true;long started=System.nanoTime();
        try {
            Files.createDirectories(path.getParent());
            var frame=new NbtCompound();frame.putInt("Version",VERSION);var changed=new NbtList();
            for(var f:fragments.values())if(!Objects.equals(durableRevisions.get(f.id),f.revision))changed.add(encode(f));frame.put("Upserts",changed);
            var removed=new NbtList();for(var id:durableRevisions.keySet())if(!fragments.containsKey(id)){var e=new NbtCompound();e.putUuid("Id",id);removed.add(e);}frame.put("Removed",removed);
            var ops=new NbtList();for(var e:operations.entrySet())if(!Objects.equals(durableOperations.get(e.getKey()),e.getValue())){var op=new NbtCompound();op.putString("Key",e.getKey());op.putUuid("Id",e.getValue());ops.add(op);}frame.put("Operations",ops);
            var deletedOps=new NbtList();for(var k:durableOperations.keySet())if(!operations.containsKey(k))deletedOps.add(NbtString.of(k));frame.put("DeletedOps",deletedOps);
            var proofs=new NbtList();for(var e:structureSnapshots.entrySet())if(!e.getValue().equals(durableStructures.get(e.getKey())))proofs.add(e.getValue().copy());frame.put("Structures",proofs);
            var deletedProofs=new NbtList();for(var k:durableStructures.keySet())if(!structureSnapshots.containsKey(k))deletedProofs.add(NbtString.of(k));frame.put("DeletedStructures",deletedProofs);
            var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(frame,bytes);byte[] payload=bytes.toByteArray();
            validWalLength=DebrisJournal.append(wal(),validWalLength,sequence+1,payload);
            sequence++;rememberDurable();dirty=false;
            lastFlushBytes=payload.length+24;lastFlushNanos=System.nanoTime()-started;
            if(validWalLength>8L*1024*1024)try{checkpoint();}catch(IOException deferred){System.err.println("[Bobin debris] checkpoint deferred; durable WAL retained: "+deferred);}
            return true;
        }catch(IOException e){ioFailed=true;System.err.println("[Bobin debris] detachment disabled: journal write failed: "+e);return false;}
    }
    private void checkpoint()throws IOException {
        var output=new ByteArrayOutputStream();NbtIo.writeCompressed(encode(),output);
        DebrisJournal.atomicSnapshot(path,output.toByteArray());
        // Snapshot Sequence makes a crash between snapshot replacement and WAL truncation idempotent.
        DebrisJournal.truncate(wal());validWalLength=0;
    }
    private static void vector(NbtCompound n,String key,V v){n.putDouble(key+"X",v.x());n.putDouble(key+"Y",v.y());n.putDouble(key+"Z",v.z());}
    private static V vector(NbtCompound n,String key){return new V(n.getDouble(key+"X"),n.getDouble(key+"Y"),n.getDouble(key+"Z"));}
    public static NbtCompound encode(DebrisFragment f) {
        var n=new NbtCompound();n.putUuid("Id",f.id);n.putUuid("Operation",f.operation);n.putString("Dimension",f.dimension);n.putString("Structure",f.structure);n.putInt("Index",f.index);n.putLong("SupportToken",f.supportToken);
        vector(n,"LaunchVelocity",f.launchVelocity);vector(n,"LaunchAngular",f.launchAngular);vector(n,"Source",f.sourcePose);vector(n,"Position",f.position);vector(n,"Velocity",f.velocity);vector(n,"Angular",f.angularVelocity);
        n.putDouble("Qx",f.rotation.x());n.putDouble("Qy",f.rotation.y());n.putDouble("Qz",f.rotation.z());n.putDouble("Qw",f.rotation.w());
        n.putLong("LastSimulationTick",f.lastSimulationTick);n.putLong("SuccessfulSteps",f.successfulSteps);n.putInt("RetryCount",f.retryCount);
        n.putString("PauseReason",f.pauseReason);n.putString("Phase",f.phase.name());n.putString("Resume",f.resume.name());n.putLong("Revision",f.revision);n.putLong("GeometryRevision",f.geometryRevision);n.putInt("Stable",f.stableTicks);n.putInt("Age",f.age);
        n.putLongArray("Supports",f.supports.stream().mapToLong(Long::longValue).toArray());n.putLongArray("Dependencies",f.dependencies.stream().mapToLong(Long::longValue).toArray());
        var cells=new NbtList();for(var c:f.cells){var e=new NbtCompound();e.putLong("Source",c.source);vector(e,"Local",c.local);e.putString("State",c.state);e.putString("Commit",c.commit.name());e.putBoolean("ProtectedSource",c.protectedSource);
            var boxes=new NbtList();for(var b:c.shapes){var a=new NbtCompound();a.putDouble("x0",b.x0());a.putDouble("y0",b.y0());a.putDouble("z0",b.z0());a.putDouble("x1",b.x1());a.putDouble("y1",b.y1());a.putDouble("z1",b.z1());boxes.add(a);}e.put("Shapes",boxes);cells.add(e);}n.put("Cells",cells);return n;
    }
    public static DebrisFragment decode(NbtCompound n) {
        var raw=n.getList("Cells",NbtElement.COMPOUND_TYPE);if(raw.isEmpty()||raw.size()>MAX_CELLS)throw new IllegalArgumentException("Geometry cell count");
        List<Cell> cells=new ArrayList<>();Set<Long> sources=new HashSet<>();
        for(int i=0;i<raw.size();i++){var e=raw.getCompound(i);var c=new Cell(e.getLong("Source"),vector(e,"Local"),e.getString("State"));
            if(c.local.length()>8||!sources.add(c.source))throw new IllegalArgumentException("Invalid local geometry");
            c.protectedSource=e.getBoolean("ProtectedSource");c.commit=Commit.valueOf(e.getString("Commit"));c.durable=c.commit==Commit.ARMED;
            var boxes=e.getList("Shapes",NbtElement.COMPOUND_TYPE);if(boxes.size()>MAX_SHAPES_PER_CELL)throw new IllegalArgumentException("Subshape count");
            var shapes=new ArrayList<B>();for(int j=0;j<boxes.size();j++){var a=boxes.getCompound(j);var b=new B(a.getDouble("x0"),a.getDouble("y0"),a.getDouble("z0"),a.getDouble("x1"),a.getDouble("y1"),a.getDouble("z1"));
                if(Math.max(new V(b.x0(),b.y0(),b.z0()).length(),new V(b.x1(),b.y1(),b.z1()).length())>12)throw new IllegalArgumentException("Subshape extent");shapes.add(b);}c.shapes=List.copyOf(shapes);if((c.commit==Commit.REMOVED||c.commit==Commit.ARMED)&&c.shapes.isEmpty())throw new IllegalArgumentException("Committed geometry without shape");cells.add(c);}
        var f=new DebrisFragment(n.getUuid("Id"),n.getUuid("Operation"),n.getString("Dimension"),n.getString("Structure"),n.getInt("Index"),cells,vector(n,"Source"));
        f.supportToken=n.getLong("SupportToken");if(f.supportToken!=0&&(f.supportToken<Long.MIN_VALUE||f.supportToken>Long.MIN_VALUE+0x1ffffffffL))throw new IllegalArgumentException("Support token");f.position=vector(n,"Position");if(Math.abs(f.position.x())>30000000||Math.abs(f.position.z())>30000000||Math.abs(f.position.y())>4096)throw new IllegalArgumentException("World pose");
        f.launchVelocity=vector(n,"LaunchVelocity").limit(1.5);f.launchAngular=vector(n,"LaunchAngular").limit(.12);
        f.velocity=vector(n,"Velocity").limit(1.5);f.angularVelocity=vector(n,"Angular").limit(.12);
        f.rotation=new Q(n.getDouble("Qx"),n.getDouble("Qy"),n.getDouble("Qz"),n.getDouble("Qw")).normalized();
        f.lastSimulationTick=n.contains("LastSimulationTick")?n.getLong("LastSimulationTick"):-1;f.successfulSteps=Math.max(0,n.getLong("SuccessfulSteps"));f.retryCount=Math.max(0,n.getInt("RetryCount"));
        f.pauseReason=n.getString("PauseReason");if(f.pauseReason.length()>64)throw new IllegalArgumentException("Pause reason");f.phase=Phase.valueOf(n.getString("Phase"));f.resume=Phase.valueOf(n.getString("Resume"));if(f.resume==Phase.PAUSED_UNLOADED)throw new IllegalArgumentException("Recursive resume phase");f.revision=Math.max(1,n.getLong("Revision"));f.geometryRevision=Math.max(1,n.getLong("GeometryRevision"));
        f.stableTicks=Math.max(0,Math.min(12,n.getInt("Stable")));f.age=Math.max(0,n.getInt("Age"));
        long[] support=n.getLongArray("Supports"),deps=n.getLongArray("Dependencies");if(support.length>64||deps.length>64)throw new IllegalArgumentException("Contact/dependency limit");
        for(long p:support)f.supports.add(p);for(long p:deps)f.dependencies.add(p);return f;
    }
}
