package com.bobincraft.vehicle;

import com.bobincraft.drone.BlastTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import java.util.*;

/** Cover-limited HE craters. Exposed surface and wall faces within blast radius are broken while intact second walls shield behind them. */
public final class ArmorBlast {
    private ArmorBlast() { }
    public static boolean breakable(BlockState state,net.minecraft.world.World world,BlockPos p,double distance){
        if(state.isAir()||state.hasBlockEntity()||!state.getFluidState().isEmpty())return false;
        float hardness=state.getHardness(world,p);float resistance=state.getBlock().getBlastResistance();
        return hardness>=0&&hardness<=5&&resistance<24&&distance<(resistance>6?2.05:3.25);
    }
    public static void crater(ServerWorld world,Entity source,ServerPlayerEntity owner,Vec3d center,boolean water){
        if(!world.getGameRules().getBoolean(BlastTerrain.TERRAIN_DAMAGE))return;
        for(BlockPos p:collect(world,source,owner,center,water))world.breakBlock(p,false,source);
    }
    private static boolean visibleFace(net.minecraft.world.World world,Entity source,Vec3d center,Box b){
        Vec3d c=b.getCenter();
        Vec3d hit=b.raycast(center,c).orElse(c);
        Vec3d step=center.subtract(hit);
        if(step.lengthSquared()>1e-8){
            Vec3d end=hit.add(step.normalize().multiply(.025));
            if(world.raycast(new RaycastContext(center,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,source)).getType()==HitResult.Type.MISS)return true;
        }
        // Check exposed face centers facing the blast origin so grazing ground impacts break the full surface crater
        // without tunneling through buried side faces or behind an intact wall.
        List<Vec3d> faces=new ArrayList<>(3);
        if(center.y>=b.maxY-.08)faces.add(new Vec3d((b.minX+b.maxX)*.5,b.maxY+.025,(b.minZ+b.maxZ)*.5));
        if(center.y<=b.minY+.08)faces.add(new Vec3d((b.minX+b.maxX)*.5,b.minY-.025,(b.minZ+b.maxZ)*.5));
        if(center.x>=b.maxX-.08)faces.add(new Vec3d(b.maxX+.025,(b.minY+b.maxY)*.5,(b.minZ+b.maxZ)*.5));
        if(center.x<=b.minX+.08)faces.add(new Vec3d(b.minX-.025,(b.minY+b.maxY)*.5,(b.minZ+b.maxZ)*.5));
        if(center.z>=b.maxZ-.08)faces.add(new Vec3d((b.minX+b.maxX)*.5,(b.minY+b.maxY)*.5,b.maxZ+.025));
        if(center.z<=b.minZ+.08)faces.add(new Vec3d((b.minX+b.maxX)*.5,(b.minY+b.maxY)*.5,b.minZ-.025));
        for(Vec3d end:faces){
            if(world.raycast(new RaycastContext(center,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,source)).getType()==HitResult.Type.MISS)return true;
        }
        return false;
    }
    static List<BlockPos> collect(net.minecraft.world.World world,Entity source,net.minecraft.entity.player.PlayerEntity owner,Vec3d center,boolean water){
        List<BlockPos> hits=new ArrayList<>();Set<BlockPos> hitSet=new HashSet<>();
        BlockPos base=BlockPos.ofFloored(center);double radius=water?1.65:3.25;
        // Pass 1: all exposed blocks with direct line-of-sight from the blast center to an exposed face.
        for(BlockPos cursor:BlockPos.iterate(base.add(-3,-3,-3),base.add(3,3,3))){
            BlockPos p=cursor.toImmutable();if(!world.isInBuildLimit(p)||!world.isChunkLoaded(p)||!world.getWorldBorder().contains(p)||owner!=null&&!world.canPlayerModifyAt(owner,p))continue;
            double distance=Vec3d.ofCenter(p).distanceTo(center);if(distance>radius||!breakable(world.getBlockState(p),world,p,distance))continue;
            var shape=world.getBlockState(p).getCollisionShape(world,p);
            if(shape.isEmpty()||visibleFace(world,source,center,shape.getBoundingBox().offset(p))){
                hits.add(p);hitSet.add(p);
            }
        }
        // Pass 2: deepen the central ground crater by 1 subsoil layer directly under broken top-surface soil blocks.
        if(!water){
            for(BlockPos top:List.copyOf(hits)){
                if(center.y<top.getY()+.85||world.getBlockState(top).getBlock().getBlastResistance()>3.5f)continue;
                double horiz=Math.hypot(top.getX()+.5-center.x,top.getZ()+.5-center.z);
                if(horiz>1.25)continue;
                BlockPos sub=top.down();
                if(hitSet.contains(sub)||!world.isInBuildLimit(sub)||!world.isChunkLoaded(sub)||!world.getWorldBorder().contains(sub)||owner!=null&&!world.canPlayerModifyAt(owner,sub))continue;
                double dist=Vec3d.ofCenter(sub).distanceTo(center);
                if(dist<=2.15&&breakable(world.getBlockState(sub),world,sub,dist)){hits.add(sub);hitSet.add(sub);}
            }
        }
        hits.sort(Comparator.comparingDouble(p->Vec3d.ofCenter(p).squaredDistanceTo(center)));
        return List.copyOf(hits.subList(0,Math.min(48,hits.size())));
    }
}
