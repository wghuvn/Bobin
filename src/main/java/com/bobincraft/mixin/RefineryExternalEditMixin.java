package com.bobincraft.mixin;

import com.bobincraft.destruction.RefineryTransitions;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protects externally changed tank cells, including same-material replacements via remove/place. */
@Mixin(WorldChunk.class)
public abstract class RefineryExternalEditMixin {
    @Inject(method="setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",at=@At("RETURN"))
    private void bobin$observe(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir) {
        if(cir.getReturnValue()!=null && ((WorldChunk)(Object)this).getWorld() instanceof ServerWorld world) {
            com.bobincraft.destruction.debris.StructureDebrisManager.blockChanged(world,pos);
            if(!com.bobincraft.destruction.debris.StructureDebrisManager.isSourceWrite())RefineryTransitions.external(world,pos);
        }
    }
}
