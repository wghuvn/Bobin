package com.bobincraft.destruction;

import com.bobincraft.warzone.*;
import net.minecraft.util.math.BlockPos;

/** Exact reference cells from the final generator blueprint, including explicit hollow-interior air and crude oil fill. */
public final class StructureGeometrySnapshot {
    public static final int REVISION=2;
    private StructureGeometrySnapshot() {}
    public static boolean includes(DestructibleStructure s, int x, int y, int z) {
        var o=s.originPosition(); int a=x-o.getX(), b=z-o.getZ(), h=y-o.getY(), r=s.radius();
        return RefineryGeometryRules.includes(r,s.height(),a,h,b);
    }
    public static Blueprint capture(WarzoneTerrain terrain, DestructibleStructure s) {
        BlockPos o=s.originPosition();int r=s.radius()+3;
        Blueprint source=StructureGeometryAccess.refinery(terrain,o.getX(),o.getZ());
        if(source==null) throw new IllegalStateException("No authoritative refinery blueprint: "+s.structureId());
        Blueprint copy=new Blueprint(s.structureId()+"_reference",o.getX()-r,o.getY(),o.getZ()-r,2*r+1,s.height()+3,2*r+1);
        source.forEachCell((x,y,z,spec,mode)->{
            if(includes(s,x,y,z) && mode==Blueprint.Mode.SET) copy.set(x,y,z,spec,mode);
        });
        return copy;
    }
    public static Blueprint stage(WarzoneTerrain terrain, DestructibleStructure s, StructureDamageState stage) {
        Blueprint bp=capture(terrain,s);
        if(stage==StructureDamageState.INTACT) return bp;
        var o=s.originPosition();int r=s.radius(),h=s.height(),v=s.destroyedVariantIndex()&3;
        // Transform original occupied cells only; crude oil stays inside during DAMAGED/CRITICAL (consumed block-by-block by fire)
        // and is cleared on catastrophic explosion (DESTROYED).
        var cells=new java.util.ArrayList<StructureCollapseManager.BlockChange>();
        bp.forEachCell((x,y,z,spec,mode)->{
            if(spec.isAir() || y==o.getY()) return;
            boolean isOil="bobin_craft:crude_oil".equals(spec.id);
            if(isOil) {
                if(stage.isTerminalOrDestroying()) {
                    cells.add(new StructureCollapseManager.BlockChange(new BlockPos(x,y,z),BpSpec.AIR,spec));
                }
                return;
            }
            int a=x-o.getX(),b=z-o.getZ(),dy=y-o.getY();
            int top=RefineryGeometryRules.survivingTop(r,h,v,a,b,stage.templateStage().index);
            boolean attachment=a*a+b*b>r*r;
            boolean remove=dy>top || (stage.isTerminalOrDestroying() && dy==h);
            if(attachment && dy<=2) remove=false;
            if(remove) cells.add(new StructureCollapseManager.BlockChange(new BlockPos(x,y,z),BpSpec.AIR,spec));
            // Surviving cells retain their authored materials; no generic replacement columns.
        });
        for(var c:cells)bp.set(c.pos().getX(),c.pos().getY(),c.pos().getZ(),c.spec());
        return bp;
    }
}
