package com.bobincraft.mixin;

import net.minecraft.block.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PaneBlock.class)
public class PaneSlabConnectionMixin {
    @Inject(method="connectsTo",at=@At("RETURN"),cancellable=true)
    private void bobinConnectSlab(BlockState neighbor,boolean solid,CallbackInfoReturnable<Boolean> result){
        if(neighbor.getBlock() instanceof SlabBlock||neighbor.getBlock() instanceof com.bobincraft.warzone.CompositeSlabBlock)result.setReturnValue(true);
    }
}
