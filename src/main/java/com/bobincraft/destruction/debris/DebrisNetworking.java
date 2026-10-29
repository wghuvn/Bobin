package com.bobincraft.destruction.debris;

import com.bobincraft.drone.DroneNetworking;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import java.util.*;

/** Paged current-state snapshots and revisioned visibility tombstones, including the existing remote drone observer. */
public final class DebrisNetworking {
    public static final Identifier CHANNEL=new Identifier("bobin_craft","structure_debris_v1");
    public static final int PROTOCOL=1,MAX_PACKET_BYTES=24576,MAX_BYTES_PER_PLAYER_TICK=32768,MAX_MESSAGES_PER_TICK=16;
    private static final Map<UUID,Stream> STREAMS=new HashMap<>();
    private static final class Stream {
        final UUID epoch=UUID.randomUUID();final String dimension;
        final Map<UUID,Long> known=new LinkedHashMap<>();final ArrayDeque<UUID> visibility=new ArrayDeque<>();
        final Map<UUID,Long> sentAt=new HashMap<>();
        int cursor,motionCursor;long sequence;boolean reset=true;
        Stream(String dimension){this.dimension=dimension;}
    }
    private DebrisNetworking(){}
    public static void tick(ServerWorld world) {
        var manager=StructureDebrisManager.get(world);String dimension=world.getRegistryKey().getValue().toString();
        var all=new ArrayList<>(manager.state.fragments.values());
        for(var player:world.getPlayers()) {
            if(!ServerPlayNetworking.canSend(player,CHANNEL))continue;
            Stream stream=STREAMS.compute(player.getUuid(),(k,old)->old==null||!old.dimension.equals(dimension)?new Stream(dimension):old);
            int bytes=0,messages=0;
            if(stream.reset){var b=PacketByteBufs.create();b.writeVarInt(PROTOCOL);b.writeString(dimension,128);b.writeUuid(stream.epoch);b.writeLong(++stream.sequence);b.writeByte(0);
                bytes+=b.readableBytes();ServerPlayNetworking.send(player,CHANNEL,b);stream.reset=false;messages++;}
            var observer=DroneNetworking.viewOrigin(player);
            int checks=Math.min(128,stream.visibility.size());
            while(checks-->0&&messages<MAX_MESSAGES_PER_TICK) {
                UUID id=stream.visibility.remove();var f=manager.state.fragments.get(id);
                if(f==null||!f.visible()||!f.reconciled||observer.squaredDistanceTo(f.position.x(),f.position.y(),f.position.z())>224*224) {
                    var b=PacketByteBufs.create();header(b,stream,2);b.writeUuid(id);bytes+=b.readableBytes();ServerPlayNetworking.send(player,CHANNEL,b);stream.known.remove(id);stream.sentAt.remove(id);messages++;
                } else stream.visibility.add(id);
            }
            var moving=all.stream().filter(f->f.visible()&&f.reconciled&&f.phase!=DebrisFragment.Phase.SLEEPING).toList();
            for(int pass=0;pass<2;pass++) {
                boolean motion=(world.getTime()%5==0)?pass==1:pass==0;
                var candidates=motion?moving:all;int scan=Math.min(motion?32:128,candidates.size());
                while(scan-->0&&messages<MAX_MESSAGES_PER_TICK&&bytes<MAX_BYTES_PER_PLAYER_TICK) {
                    if(candidates.isEmpty())break;
                    int cursor=motion?stream.motionCursor:stream.cursor;var f=candidates.get(Math.floorMod(cursor,candidates.size()));
                    if(motion)stream.motionCursor++;else stream.cursor++;
                    if(!f.visible()||!f.reconciled||observer.squaredDistanceTo(f.position.x(),f.position.y(),f.position.z())>208*208)continue;
                    Long seen=stream.known.get(f.id),last=stream.sentAt.get(f.id);
                    if(seen!=null&&(seen==f.revision||last!=null&&world.getTime()>=last&&world.getTime()-last<4))continue;
                    var b=PacketByteBufs.create();header(b,stream,1);b.writeNbt(DebrisPersistentState.encode(f));int size=b.readableBytes();
                    if(size>MAX_PACKET_BYTES){b.release();continue;}
                    if(bytes+size>MAX_BYTES_PER_PLAYER_TICK){b.release();if(motion)stream.motionCursor--;else stream.cursor--;break;}
                    ServerPlayNetworking.send(player,CHANNEL,b);bytes+=size;messages++;stream.sentAt.put(f.id,world.getTime());
                    if(stream.known.put(f.id,f.revision)==null)stream.visibility.add(f.id);
                }
            }

        }
        Set<UUID> connected=new HashSet<>();for(var p:world.getServer().getPlayerManager().getPlayerList())connected.add(p.getUuid());STREAMS.keySet().removeIf(id->!connected.contains(id));
    }
    private static void header(net.minecraft.network.PacketByteBuf b,Stream s,int type){b.writeVarInt(PROTOCOL);b.writeString(s.dimension,128);b.writeUuid(s.epoch);b.writeLong(++s.sequence);b.writeByte(type);}
    public static void clear(){STREAMS.clear();}
}
