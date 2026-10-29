package com.bobincraft.mixin;

import com.bobincraft.vehicle.ArmorGeometry;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(EntityView.class)
public interface ArmorCollisionMixin {
    @Inject(method="getEntityCollisions",at=@At("HEAD"),cancellable=true)
    private void bobin_craft$walkableArmor(Entity entity,Box box,CallbackInfoReturnable<List<VoxelShape>> cir){
        var shapes=ArmorGeometry.entityCollisions((EntityView)this,entity,box);
        if(shapes!=null)cir.setReturnValue(shapes);
    }
}
