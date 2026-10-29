package com.bobincraft.raid;
import net.minecraft.world.World;
/** Local readiness for finite encounter posts; a remote district edge cannot hold a sentry hostage. */
public final class EncounterActivation {
    private EncounterActivation() { }
    public static boolean ready(World world,int x,int z,int radius){
        for(int cx=(x-3)>>4;cx<=(x+3)>>4;cx++)for(int cz=(z-3)>>4;cz<=(z+3)>>4;cz++)if(!world.isChunkLoaded(cx,cz))return false;
        return world.getPlayers().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&Math.pow(p.getX()-x,2)+Math.pow(p.getZ()-z,2)<=radius*radius);
    }
}
