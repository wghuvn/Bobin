package com.bobincraft.vehicle;

import com.bobincraft.drone.BlastTerrain;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.*;
import java.util.*;

/** Pressure through this impact's exposed breach. Intact walls and trench earth still stop the wave. */
public final class ArmorShockwave {
    private ArmorShockwave() { }
    public record Plan(Vec3d origin,Set<BlockPos> breach,List<BlockPos> windows) { }
    public static boolean glass(BlockState s){return s.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BLOCK,new net.minecraft.util.Identifier("bobin_craft","drone_shatterable_glass")))||s.getBlock() instanceof AbstractGlassBlock||s.getBlock() instanceof StainedGlassPaneBlock||s.isOf(Blocks.GLASS_PANE);}
    public static Plan collect(World w,Entity source,PlayerEntity owner,Vec3d center,boolean water){
        return collect(w,source,owner,center,water,w.getGameRules().getBoolean(BlastTerrain.TERRAIN_DAMAGE));
    }
    static Plan collect(World w,Entity source,PlayerEntity owner,Vec3d center,boolean water,boolean terrainDamage){
        Set<BlockPos> breach=terrainDamage?Set.copyOf(ArmorBlast.collect(w,source,owner,center,water)):Set.of();
        List<BlockPos> windows=new ArrayList<>();
        if(terrainDamage)for(BlockPos cursor:BlockPos.iterate(BlockPos.ofFloored(center).add(-12,-12,-12),BlockPos.ofFloored(center).add(12,12,12))){
            BlockPos p=cursor.toImmutable();if(!w.isInBuildLimit(p)||!w.isChunkLoaded(p)||!w.getWorldBorder().contains(p)||owner!=null&&!w.canPlayerModifyAt(owner,p))continue;
            if(Vec3d.ofCenter(p).squaredDistanceTo(center)>144||!glass(w.getBlockState(p)))continue;
            if(transmission(w,source,center,Vec3d.ofCenter(p),breach)>0)windows.add(p);
        }
        windows.sort(Comparator.comparingDouble(p->Vec3d.ofCenter(p).squaredDistanceTo(center)));
        return new Plan(center,breach,List.copyOf(windows.subList(0,Math.min(96,windows.size()))));
    }
    public static double transmission(World w,Entity source,Vec3d start,Vec3d end,Set<BlockPos> breach){
        // Check every crossed chunk; pressure never loads/generates terrain.
        int count=Math.max(1,(int)Math.ceil(start.distanceTo(end)*2));
        for(int i=0;i<=count;i++)if(!w.isChunkLoaded(BlockPos.ofFloored(start.lerp(end,i/(double)count))))return 0;
        var result=w.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,source){
            @Override public net.minecraft.util.shape.VoxelShape getBlockShape(BlockState s,BlockView world,BlockPos p){return breach.contains(p)||glass(s)?VoxelShapes.empty():super.getBlockShape(s,world,p);}
        });
        if(result.getType()!=HitResult.Type.MISS)return 0;
        // A breached wall absorbs energy; the follow-through is weaker than open-air pressure.
        boolean crossed=breach.stream().anyMatch(p->new Box(p).raycast(start,end).isPresent());return crossed?.68:1;
    }
    public static double exposure(World w,Entity source,Entity target,Plan plan){
        var box=target.getBoundingBox();Vec3d middle=box.getCenter();double total=0;
        for(Vec3d p:List.of(middle,middle.add(0,(box.maxY-box.minY)*.35,0),middle.add(0,-(box.maxY-box.minY)*.35,0),middle.add(.18,0,0),middle.add(-.18,0,0)))
            total+=transmission(w,source,plan.origin,p,plan.breach);
        return total/5;
    }
    public static void applyTerrain(ServerWorld w,Entity source,Plan plan){
        if(!w.getGameRules().getBoolean(BlastTerrain.TERRAIN_DAMAGE))return;
        for(var p:plan.windows)if(glass(w.getBlockState(p)))w.breakBlock(p,false,source);
        int debris=0;
        List<BlockPos> sorted=new ArrayList<>(plan.breach);
        sorted.sort(Comparator.comparingDouble(p->Vec3d.ofCenter(p).squaredDistanceTo(plan.origin)));
        for(var p:sorted){
            var state=w.getBlockState(p);if(state.isAir())continue;
            if(debris++<16){
                Vec3d diff=Vec3d.ofCenter(p).subtract(plan.origin);
                Vec3d dir=diff.lengthSquared()>1e-4?new Vec3d(diff.x,Math.max(.38,diff.y+.55),diff.z).normalize():new Vec3d(0,1,0);
                com.bobincraft.combat.ImpactDebris.send(w,state,Vec3d.ofCenter(p).add(0,.32,0),dir,2.35f,null);
            }
            w.breakBlock(p,false,source);
        }
    }
}
