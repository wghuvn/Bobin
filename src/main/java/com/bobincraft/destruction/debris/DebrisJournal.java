package com.bobincraft.destruction.debris;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Actual durable byte-journal layer, independent of Minecraft's NBT codec. */
public final class DebrisJournal {
    public static final int MAGIC=0x42444231,MAX_FRAME=32*1024*1024;
    public static final long MAX_BYTES=128L*1024*1024;
    public record Frame(long sequence,byte[] payload){}
    public record Read(List<Frame> frames,long validBytes){}
    private DebrisJournal(){}
    public static Read read(Path path)throws IOException {
        if(!Files.exists(path))return new Read(List.of(),0);
        if(Files.size(path)>MAX_BYTES)throw new IOException("Debris WAL size limit");
        var frames=new ArrayList<Frame>();long sequence=0,valid=0;
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            while(true) {
                int magic,length;long crc,serial;
                try{magic=input.readInt();length=input.readInt();crc=input.readLong();serial=input.readLong();}catch(EOFException partialTail){break;}
                if(magic!=MAGIC||length<0||length>MAX_FRAME||serial<=sequence)throw new IOException("Invalid debris WAL header");
                byte[] bytes=input.readNBytes(length);if(bytes.length!=length)break;
                CRC32 checksum=new CRC32();checksum.update(bytes);if(checksum.getValue()!=crc)throw new IOException("Debris WAL checksum mismatch");
                frames.add(new Frame(serial,bytes));sequence=serial;valid+=24L+length;
            }
        }return new Read(List.copyOf(frames),valid);
    }
    public static long append(Path path,long validBytes,long sequence,byte[] payload)throws IOException {
        if(payload.length>MAX_FRAME||validBytes<0||sequence<=0||validBytes+24L+payload.length>MAX_BYTES)throw new IOException("Debris WAL bounds");
        Files.createDirectories(path.getParent());
        if(validBytes>0&&(!Files.exists(path)||Files.size(path)<validBytes))throw new IOException("Debris WAL changed outside writer");
        CRC32 checksum=new CRC32();checksum.update(payload);
        ByteBuffer header=ByteBuffer.allocate(24).putInt(MAGIC).putInt(payload.length).putLong(checksum.getValue()).putLong(sequence);header.flip();
        try(var channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
            channel.truncate(validBytes);channel.position(validBytes);while(header.hasRemaining())channel.write(header);
            ByteBuffer body=ByteBuffer.wrap(payload);while(body.hasRemaining())channel.write(body);channel.force(true);
        }
        forceDirectory(path.getParent());return validBytes+24L+payload.length;
    }
    public static void atomicSnapshot(Path path,byte[] bytes)throws IOException {
        Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        try(var file=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            ByteBuffer body=ByteBuffer.wrap(bytes);while(body.hasRemaining())file.write(body);file.force(true);
        }
        Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);forceDirectory(path.getParent());
    }
    public static void truncate(Path path)throws IOException {try(var file=FileChannel.open(path,StandardOpenOption.WRITE)){file.truncate(0);file.force(true);}}
    private static void forceDirectory(Path path){try(var directory=FileChannel.open(path,StandardOpenOption.READ)){directory.force(true);}catch(IOException|UnsupportedOperationException unsupported){}}
}
