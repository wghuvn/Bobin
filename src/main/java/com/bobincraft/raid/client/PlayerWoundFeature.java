package com.bobincraft.raid.client;
import com.bobincraft.combat.HitRegion;
import com.bobincraft.combat.PlayerInjuries;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
/** Player wounds use the same restrained pixel-art caps as guards, in the player's skin pose. */
public final class PlayerWoundFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE=new Identifier("bobin_craft","textures/entity/wound_caps.png");
    public static EntityRenderer<Entity> PLAYER_RENDERER;
    private final ModelPart[] caps=new ModelPart[5];
    public PlayerWoundFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);var root=GuardWoundFeature.modelData().createModel();for(int i=0;i<5;i++)caps[i]=root.getChild("cap"+i);}
    @Override public void render(MatrixStack matrices,VertexConsumerProvider out,int light,AbstractClientPlayerEntity player,float angle,float distance,float delta,float age,float yaw,float pitch){
        if(player.isInvisible())return;int missing=PlayerInjuries.missing(player);if(missing==0)return;
        var m=getContextModel();ModelPart[] limbs={m.leftArm,m.rightArm,m.leftLeg,m.rightLeg};var v=out.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        for(int i=0;i<4;i++)if((missing&(1<<i))!=0){caps[i].copyTransform(limbs[i]);caps[i].visible=true;caps[i].render(matrices,v,light,OverlayTexture.DEFAULT_UV);}
        if((missing&HitRegion.HEAD_SEVERED)!=0){caps[4].copyTransform(m.body);caps[4].visible=true;caps[4].render(matrices,v,light,OverlayTexture.DEFAULT_UV);}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static void register(){net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{if(renderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer player){PLAYER_RENDERER=(EntityRenderer)player;helper.register(new PlayerWoundFeature(player));}});}
}
