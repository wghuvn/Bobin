package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.client.presentation.PlayerHandAppearance.SkinMaterials;
import com.bobincraft.armament.client.render.fabric.GlbMaterialBindings;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.util.Identifier;

/** Borrows TextureManager's skin identifier each draw. Never uploads, mutates or closes a skin. */
final class PlayerSkinBinding implements GlbMaterialBindings {
    private final SkinMaterials roles;
    private Identifier skin;
    private boolean rightSleeve,leftSleeve;
    PlayerSkinBinding(SkinMaterials roles){this.roles=roles;}
    void update(ClientPlayerEntity player){
        skin=player.getSkinTexture();
        rightSleeve=player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
        leftSleeve=player.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
    }
    @Override public boolean visible(int material){return roles.visible(material,rightSleeve,leftSleeve);}
    @Override public Identifier texture(int material,Identifier embedded){return roles.playerSkin(material)?skin:embedded;}
}
