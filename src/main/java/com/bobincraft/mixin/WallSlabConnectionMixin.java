package com.bobincraft.mixin;

import net.minecraft.block.*;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
public class WallSlabConnectionMixin {
    @Inject(method="shouldConnectTo",at=@At("RETURN"),cancellable=true)
    private void bobinConnectSlab(BlockState neighbor,boolean solid,Direction direction,CallbackInfoReturnable<Boolean> result){
        if(neighbor.getBlock() instanceof SlabBlock||neighbor.getBlock() instanceof com.bobincraft.warzone.CompositeSlabBlock)result.setReturnValue(true);
    }
}
