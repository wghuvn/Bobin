package com.bobincraft.raid.client;

import com.bobincraft.raid.HubNpcInteraction;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/** Only named camp personnel use the original human rig; ordinary villagers retain vanilla rendering. */
public final class HubServiceNpcRenderer extends MobEntityRenderer<VillagerEntity, BipedEntityModel<VillagerEntity>> {
    private static final Map<HubNpcInteraction.Role, Identifier> TEXTURES = new EnumMap<>(HubNpcInteraction.Role.class);
    private final VillagerEntityRenderer vanilla;

    static {
        for (HubNpcInteraction.Role role : HubNpcInteraction.Role.values()) {
            TEXTURES.put(role, new Identifier("bobin_craft", "textures/entity/hub/" + role.key() + ".png"));
        }
    }

    public HubServiceNpcRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(modelData().createModel()), 0.4F);
        vanilla = new VillagerEntityRenderer(context);
    }

    private static TexturedModelData modelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("head", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.NONE);
        root.addChild("hat", ModelPartBuilder.create().uv(32, 0)
                .cuboid(-4, -8, -4, 8, 8, 8, new Dilation(0.3F)), ModelTransform.NONE);
        root.addChild("body", ModelPartBuilder.create().uv(16, 16)
                .cuboid(-4, 0, -2, 8, 12, 4)
                .uv(16, 32).cuboid(-4, 1, -2, 8, 10, 4, new Dilation(0.22F)), ModelTransform.NONE);
        root.addChild("right_arm", ModelPartBuilder.create().uv(40, 16)
                .cuboid(-3, -2, -2, 4, 12, 4)
                .uv(40, 32).cuboid(-3, -2, -2, 4, 4, 4, new Dilation(0.15F)), ModelTransform.pivot(-5, 2, 0));
        root.addChild("left_arm", ModelPartBuilder.create().uv(32, 48)
                .cuboid(-1, -2, -2, 4, 12, 4)
                .uv(48, 48).cuboid(-1, -2, -2, 4, 4, 4, new Dilation(0.15F)), ModelTransform.pivot(5, 2, 0));
        root.addChild("right_leg", ModelPartBuilder.create().uv(0, 16)
                .cuboid(-2, 0, -2, 4, 12, 4)
                .uv(0, 32).cuboid(-2, 7, -2, 4, 5, 4, new Dilation(0.1F)), ModelTransform.pivot(-1.9F, 12, 0));
        root.addChild("left_leg", ModelPartBuilder.create().uv(16, 48)
                .cuboid(-2, 0, -2, 4, 12, 4)
                .uv(0, 48).cuboid(-2, 7, -2, 4, 5, 4, new Dilation(0.1F)), ModelTransform.pivot(1.9F, 12, 0));
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public void render(VillagerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light) {
        if (HubNpcInteraction.roleOf(entity) == null) {
            vanilla.render(entity, yaw, tickDelta, matrices, vertices, light);
        } else {
            super.render(entity, yaw, tickDelta, matrices, vertices, light);
        }
    }

    @Override
    public Identifier getTexture(VillagerEntity entity) {
        HubNpcInteraction.Role role = HubNpcInteraction.roleOf(entity);
        return role == null ? vanilla.getTexture(entity) : TEXTURES.get(role);
    }
}
