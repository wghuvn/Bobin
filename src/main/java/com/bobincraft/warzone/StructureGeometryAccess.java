package com.bobincraft.warzone;

/** Read-only access to the actual final cached site blueprint. Does not rebuild industrial geometry. */
public final class StructureGeometryAccess {
    private StructureGeometryAccess() {}
    public static Blueprint refinery(WarzoneTerrain terrain, int x, int z) {
        if (terrain == null || !terrain.isAuthoredZone()) return null;
        for (var site : terrain.zonePlan().sites()) if (site.theme()==ZonePlan.Theme.REFINERY
                && Math.abs(x-site.x())<=site.radius() && Math.abs(z-site.z())<=site.radius())
            return terrain.residentialStructures().siteBlueprint(site);
        return null;
    }
    public static Blueprint substation(WarzoneTerrain terrain,int x,int z) {
        if(terrain==null||!terrain.isAuthoredZone())return null;
        for(var site:terrain.zonePlan().sites())if(site.theme()==ZonePlan.Theme.SUBSTATION
                &&Math.abs(x-site.x())<=site.radius()&&Math.abs(z-site.z())<=site.radius())
            return terrain.residentialStructures().siteBlueprint(site);
        return null;
    }

}
