package com.bobincraft.vehicle;

import com.bobincraft.warzone.*;
import com.bobincraft.raid.guard.ResidentialResidents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Lazy entity insertion on the server thread after every chunk occupied by the hull is already loaded. */
public final class ArmoredPositions {
    private ArmoredPositions() { }
    public static void tick(ServerWorld world){
        if(!world.getRegistryKey().equals(WarzoneDimension.WORLD)||world.getPlayers().isEmpty())return;
        var generator=WarzoneDimension.generatorOf(world);if(generator==null||generator.terrain().generationVersion()<9)return;
        var t=generator.terrain();var roster=ArmorRoster.get(world);int budget=2;
        for(var s:t.zonePlan().sites()){
            var p=OccupationPlan.armor(s);if(p==null||roster.entry(p.id())!=null)continue;
            if(world.getPlayers().stream().noneMatch(player->player.isAlive()&&!player.isSpectator()&&player.squaredDistanceTo(p.x(),player.getY(),p.z())<112*112))continue;
            if(!loaded(world,p.x(),p.z()))continue;
            var vehicle=ArmorContent.VEHICLE.create(world);if(vehicle==null)continue;
            var faction=ResidentialResidents.faction(ResidentialPlan.Group.values()[(s.id()-1)%3]);
            vehicle.refreshPositionAndAngles(p.x()+.5,t.height(p.x(),p.z())+1-(p.buried()?ArmorControls.BURIAL_DEPTH:0),p.z()+.5,p.yaw(),0);
            vehicle.configure(p.buried(),faction.ordinal(),p.id(),p.yaw());
            if(!p.buried()&&!world.isSpaceEmpty(vehicle)){vehicle.discard();continue;}
            UUID id=roster.reserve(t.seed,p.id());if(id==null){vehicle.discard();continue;}vehicle.setUuid(id);if(!world.spawnEntity(vehicle)&&world.getEntity(id)==null)roster.rejected(p.id(),id);
            if(--budget<=0)return;
        }
    }
    private static boolean loaded(ServerWorld w,int x,int z){for(int cx=(x-8)>>4;cx<=(x+8)>>4;cx++)for(int cz=(z-8)>>4;cz<=(z+8)>>4;cz++)if(!w.isChunkLoaded(cx,cz))return false;return true;}
}
