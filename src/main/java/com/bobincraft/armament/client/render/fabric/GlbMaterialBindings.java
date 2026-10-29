package com.bobincraft.armament.client.render.fabric;

import net.minecraft.util.Identifier;

/** Optional borrowed material inputs. The draw and asset cache never own these textures. */
public interface GlbMaterialBindings {
    boolean visible(int material);
    Identifier texture(int material,Identifier embedded);
}
