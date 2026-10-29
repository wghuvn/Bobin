package com.bobincraft.drone;

import java.util.UUID;

import com.bobincraft.raid.HubSafety;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class DroneExplosion {
    private DroneExplosion() { }

    public static void explode(ServerWorld world, Entity source, ServerPlayerEntity owner, DroneBlastType type) {
        if (HubSafety.isHub(world)) return;
        Vec3d center = source.getPos().add(0, 0.10, 0);
        explodeAt(world, source, owner, type, center);
    }

    public static void explodeAt(ServerWorld world, Entity source, ServerPlayerEntity owner, DroneBlastType type, Vec3d center) {
        if (HubSafety.isHub(world)) return;
        boolean water = world.getFluidState(BlockPos.ofFloored(center)).isIn(FluidTags.WATER);
        var surface = world.raycast(new RaycastContext(center, center.add(0, -2.5, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, source));
        BlockState material = surface.getType() == HitResult.Type.BLOCK ? world.getBlockState(surface.getBlockPos()) : null;
        // Trace against the original world. Only loaded, visible glass/soil is subsequently edited.
        var terrain = BlastTerrain.collect(world, source, owner, center, type, water);
        var fragments = DroneShrapnel.trace(world, source, center, type, water);
        var shock=type==DroneBlastType.ARMOR_HIGH_EXPLOSIVE?com.bobincraft.vehicle.ArmorShockwave.collect(world,source,owner,center,water):null;
        DronePressure.apply(world, source, owner, center, type,shock);
        DroneShrapnel.apply(world, source, owner, fragments);
        // Apply ArmorShockwave terrain breach BEFORE BlastTerrain scorches the surface so original BlockStates spawn as 3D debris.
        if(shock!=null){
            if(shock.breach().isEmpty()&&material!=null&&!material.isAir()&&!water){
                com.bobincraft.combat.ImpactDebris.send(world,material,surface.getPos().add(0,.25,0),new Vec3d(0,1,0),2.35f,null);
            }
            com.bobincraft.vehicle.ArmorShockwave.applyTerrain(world,source,shock);
        }
        terrain.apply(world, source);
        if (!(source instanceof com.bobincraft.weapon.heavy.RpgProjectileEntity)) {
            UUID eventId = source != null ? source.getUuid() : UUID.randomUUID();
            UUID ownerId = owner != null ? owner.getUuid() : null;
            var structSource = new com.bobincraft.destruction.StructureDamageSource(
                    eventId,
                    com.bobincraft.destruction.StructureDamageSource.fromDroneBlast(source, ownerId, type, center).kind(),
                    ownerId,
                    center,
                    Math.max(3.5, type.pressureRadius())
            );
            com.bobincraft.destruction.StructureDamageManager.applyDamage(
                    world, center, structSource,
                    com.bobincraft.destruction.StructureDamageSource.baseStructuralDamage(type)
            );
        }
        DroneExplosionEffects.play(world, source, owner, center, type, water, material, surface.getPos());
    }
}
