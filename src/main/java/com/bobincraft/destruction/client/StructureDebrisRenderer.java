package com.bobincraft.destruction.client;

import com.bobincraft.destruction.RefineryTransitions;
import com.bobincraft.destruction.debris.*;
import com.bobincraft.combat.client.ImpactDebrisClient;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.BlockPos;
import org.joml.Quaternionf;
import java.util.*;
import static com.bobincraft.destruction.debris.DebrisFragment.*;

/** Read-only interpolated source-block meshes. Client never performs physics, finds a floor or bakes aftermath. */
@Environment(EnvType.CLIENT)
public final class StructureDebrisRenderer {
    public static int maxVisibleFragments=512,maxBlockModelsPerFrame=2048;

    private static final class Mesh {
        final DebrisFragment now;
        final V previous;
        final Q previousRotation;
        final long received;
        final List<BlockState> materials;
        final BlockPos[] sourcePositions;
        final long[] chunkKeys;
        final Quaternionf staticRotation;
        final boolean stationary;
        int cachedLight = -1;
        long lastLightTick = Long.MIN_VALUE;

        Mesh(DebrisFragment now, V previous, Q previousRotation, long received, List<BlockState> materials) {
            this.now = now;
            this.previous = previous;
            this.previousRotation = previousRotation;
            this.received = received;
            this.materials = materials;
            this.stationary = (now.phase == Phase.SLEEPING || now.phase == Phase.PAUSED_UNLOADED)
                    && previous.equals(now.position) && previousRotation.equals(now.rotation);
            this.staticRotation = new Quaternionf(
                    (float) now.rotation.x(), (float) now.rotation.y(), (float) now.rotation.z(), (float) now.rotation.w());
            int n = now.cells.size();
            this.sourcePositions = new BlockPos[n];
            for (int i = 0; i < n; i++) {
                this.sourcePositions[i] = BlockPos.fromLong(now.cells.get(i).source);
            }
            B b = now.bounds();
            if (b == null) {
                this.chunkKeys = new long[0];
            } else {
                int cx0 = (int) Math.floor(b.x0()) >> 4;
                int cx1 = (int) Math.floor(b.x1()) >> 4;
                int cz0 = (int) Math.floor(b.z0()) >> 4;
                int cz1 = (int) Math.floor(b.z1()) >> 4;
                int count = Math.max(0, (cx1 - cx0 + 1) * (cz1 - cz0 + 1));
                this.chunkKeys = new long[count];
                int idx = 0;
                for (int x = cx0; x <= cx1; x++) {
                    for (int z = cz0; z <= cz1; z++) {
                        this.chunkKeys[idx++] = ((long) x & 0xffffffffL) | ((long) z << 32);
                    }
                }
            }
        }
    }

    private static final Map<UUID,Mesh> MESHES=new HashMap<>();
    private static final DebrisRevisionWindow WINDOW=new DebrisRevisionWindow();
    private static final net.minecraft.client.render.VertexConsumerProvider.Immediate WORLD_BUFFERS =
            net.minecraft.client.render.VertexConsumerProvider.immediate(new net.minecraft.client.render.BufferBuilder(262144));
    private StructureDebrisRenderer(){}
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientPlayNetworking.registerGlobalReceiver(DebrisNetworking.CHANNEL,(client,handler,buf,sender)->{
            if(buf.readableBytes()>DebrisNetworking.MAX_PACKET_BYTES)return;
            try {
                int version=buf.readVarInt();if(version!=DebrisNetworking.PROTOCOL)return;
                String dim=buf.readString(128);UUID session=buf.readUuid();long seq=buf.readLong();int type=buf.readUnsignedByte();
                DebrisFragment fragment=type==1?DebrisPersistentState.decode(Objects.requireNonNull(buf.readNbt())):null;
                UUID removed=type==2?buf.readUuid():null;
                if(type<0||type>2||buf.isReadable()||fragment!=null&&!fragment.dimension.equals(dim))return;
                client.execute(()->{try{apply(dim,session,seq,type,fragment,removed);}catch(IllegalArgumentException invalid){/* unresolved registry state: no invented substitute */}});
            }catch(RuntimeException invalid){/* bounded malformed cosmetic payload cannot mutate world state */}
        });
        WorldRenderEvents.LAST.register(context->{
            if(MESHES.isEmpty())return;
            var client=MinecraftClient.getInstance();var world=client.world;
            if(world==null||context.matrixStack()==null||!world.getRegistryKey().getValue().toString().equals(WINDOW.dimension()))return;
            var camera=context.camera().getPos();var matrices=context.matrixStack();var consumers=WORLD_BUFFERS;
            long worldTime=world.getTime();
            Collection<Mesh> candidates=MESHES.values();
            if(candidates.size()>maxVisibleFragments){
                var sorted=new ArrayList<Mesh>(candidates.size());
                for(var m:candidates)if(camera.squaredDistanceTo(m.now.position.x(),m.now.position.y(),m.now.position.z())<=192*192)sorted.add(m);
                sorted.sort(Comparator.comparingDouble(m->camera.squaredDistanceTo(m.now.position.x(),m.now.position.y(),m.now.position.z())));
                candidates=sorted;
            }
            int models=0,fragments=0;
            var blockRenderManager=client.getBlockRenderManager();
            var q0=new Quaternionf();var q1=new Quaternionf();
            for(var mesh:candidates){
                var f=mesh.now;
                if(fragments>=maxVisibleFragments||models+mesh.materials.size()>maxBlockModelsPerFrame)break;
                if(camera.squaredDistanceTo(f.position.x(),f.position.y(),f.position.z())>192*192)continue;
                if(mesh.chunkKeys.length==0)continue;
                boolean loaded=true;
                for(int k=0;k<mesh.chunkKeys.length;k++){
                    long ck=mesh.chunkKeys[k];
                    if(!world.isChunkLoaded((int)ck,(int)(ck>>32))){loaded=false;break;}
                }
                if(!loaded)continue;

                double px,py,pz;
                Quaternionf rot;
                if(mesh.stationary){
                    px=f.position.x();py=f.position.y();pz=f.position.z();
                    rot=mesh.staticRotation;
                } else {
                    double blend=f.phase==Phase.PAUSED_UNLOADED?1:Math.min(1,Math.max(0,(worldTime-mesh.received+context.tickDelta())/4.0));
                    if(blend>=0.999){
                        px=f.position.x();py=f.position.y();pz=f.position.z();
                        rot=mesh.staticRotation;
                    } else {
                        px=mesh.previous.x()*(1-blend)+f.position.x()*blend;
                        py=mesh.previous.y()*(1-blend)+f.position.y()*blend;
                        pz=mesh.previous.z()*(1-blend)+f.position.z()*blend;
                        q0.set((float)mesh.previousRotation.x(),(float)mesh.previousRotation.y(),(float)mesh.previousRotation.z(),(float)mesh.previousRotation.w());
                        q1.set((float)f.rotation.x(),(float)f.rotation.y(),(float)f.rotation.z(),(float)f.rotation.w());
                        q0.slerp(q1,(float)blend);
                        rot=q0;
                    }
                }

                long lightInterval = mesh.stationary ? 20L : 2L;
                if(mesh.cachedLight == -1 || worldTime - mesh.lastLightTick >= lightInterval || worldTime < mesh.lastLightTick) {
                    mesh.cachedLight = ImpactDebrisClient.sampleFragmentLight(world, px, py, pz, mesh.cachedLight);
                    mesh.lastLightTick = worldTime;
                }
                int light = mesh.cachedLight;

                matrices.push();matrices.translate(px-camera.x,py-camera.y,pz-camera.z);matrices.multiply(rot);
                boolean checkUnmoved = f.age < 2 && f.position.equals(f.sourcePose);
                for(int i=0,n=f.cells.size();i<n;i++){
                    var c=f.cells.get(i);if(c.commit!=Commit.REMOVED)continue;
                    if(checkUnmoved&&world.getBlockState(mesh.sourcePositions[i]).equals(mesh.materials.get(i)))continue;
                    matrices.push();matrices.translate(c.local.x(),c.local.y(),c.local.z());
                    blockRenderManager.renderBlockAsEntity(mesh.materials.get(i),matrices,consumers,light,OverlayTexture.DEFAULT_UV);
                    matrices.pop();models++;
                }
                matrices.pop();fragments++;
            }
            if(models>0)consumers.draw();
        });
    }
    private static void apply(String dim,UUID session,long seq,int type,DebrisFragment f,UUID removed) {
        if(type==0){if(WINDOW.reset(dim,session,seq))MESHES.clear();return;}
        if(!WINDOW.accept(dim,session,seq,type==2?removed:f.id,type==2?0:f.revision,type==2))return;
        if(type==2){MESHES.remove(removed);return;}
        Mesh old=MESHES.get(f.id);if(old!=null&&f.revision<old.now.revision)return;
        if(old==null&&MESHES.size()>=DebrisPersistentState.MAX_RECORDS)return;
        boolean sameGeo = old != null && f.geometryRevision == old.now.geometryRevision;
        List<BlockState> states;
        if (sameGeo) {
            states = old.materials;
        } else {
            var decoded = new ArrayList<BlockState>(f.cells.size());
            for (var c : f.cells) decoded.add(RefineryTransitions.decode(c.state));
            states = List.copyOf(decoded);
        }
        long now=MinecraftClient.getInstance().world==null?0:MinecraftClient.getInstance().world.getTime();
        Mesh nextMesh = new Mesh(f,sameGeo?old.now.position:f.position,sameGeo?old.now.rotation:f.rotation,now,states);
        if (old != null) {
            nextMesh.cachedLight = old.cachedLight;
            nextMesh.lastLightTick = old.lastLightTick;
        }
        MESHES.put(f.id, nextMesh);
    }
    public static void clear(){MESHES.clear();WINDOW.clear();}
}
