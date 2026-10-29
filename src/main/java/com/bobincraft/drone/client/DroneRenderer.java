package com.bobincraft.drone.client;

import com.bobincraft.BobinCraft;
import com.bobincraft.drone.DroneBlastType;
import com.bobincraft.drone.DroneEntity;
import com.bobincraft.drone.DroneType;
import com.bobincraft.drone.DroneVariant;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Optimized single-pass renderer for stretched-X FPV quadcopters, heavy-lift multirotors,
 * and the 2x-scaled FP-1 long-range fixed-wing aircraft with dynamic ground shadows and distance LOD.
 */
public final class DroneRenderer extends EntityRenderer<DroneEntity> {
    static final Identifier TEXTURE = new Identifier(BobinCraft.MOD_ID, "textures/entity/drone.png");
    static final double MAX_SHADOW_DROP = 8.0;
    private static final Set<Direction> SHADOW_FACE = EnumSet.of(Direction.DOWN);
    private final EnumMap<DroneVariant, Parts> variants = new EnumMap<>(DroneVariant.class);
    private final ShadowParts fpvShadow;
    private final ShadowParts bomberShadow;
    private final ShadowParts fixedWingShadow;

    public DroneRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 0.0f;
        shadowOpacity = 0.0f;
        for (DroneVariant variant : DroneVariant.values()) {
            variants.put(variant, new Parts(modelData(variant).createModel()));
        }
        fpvShadow = new ShadowParts(shadowModelData(false).createModel());
        bomberShadow = new ShadowParts(shadowModelData(true).createModel());
        fixedWingShadow = new ShadowParts(fixedWingShadowModelData().createModel());
    }

    static TexturedModelData modelData(boolean bomber) {
        return modelData(bomber ? DroneVariant.BOMBER : DroneVariant.FPV);
    }

    public static TexturedModelData modelData(DroneVariant variant) {
        if (variant.type == DroneType.FIXED_WING) {
            return fixedWingModelData();
        }
        boolean bomber = variant.type.isCarrier();
        boolean heavy = variant.type == DroneType.HEAVY;
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        float motorX = heavy ? 7.4f : bomber ? 6.4f : 3.85f;
        float motorZ = heavy ? 8.2f : bomber ? 7.4f : 5.45f;
        float propRadius = heavy ? 4.8f : bomber ? 4.3f : 3.35f;
        float armWidth = heavy ? 1.5f : bomber ? 1.3f : 1.1f;

        ModelPartBuilder frameBuilder = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.8f, 0.5f, -6.5f, 3.6f, 0.5f, 13.0f)
                .uv(0, 0).cuboid(-1.5f, -0.95f, -5.7f, 3.0f, 0.42f, 11.2f)
                .uv(0, 0).cuboid(-1.3f, -0.53f, -2.7f, 2.6f, 1.03f, 5.4f)
                .uv(0, 0).cuboid(-1.55f, -1.1f, -7.0f, 0.4f, 2.0f, 1.8f)
                .uv(0, 0).cuboid(1.15f, -1.1f, -7.0f, 0.4f, 2.0f, 1.8f)
                .uv(0, 0).cuboid(-1.4f, -0.9f, 4.5f, 0.4f, 1.4f, 0.85f)
                .uv(0, 0).cuboid(1.0f, -0.9f, 4.5f, 0.4f, 1.4f, 0.85f);

        if (bomber) {
            frameBuilder
                    .uv(0, 0).cuboid(-4.6f, 1.6f, -5.2f, 0.6f, 2.6f, 10.4f)
                    .uv(0, 0).cuboid(4.0f, 1.6f, -5.2f, 0.6f, 2.6f, 10.4f);
        } else {
            for (int i = 0; i < 4; i++) {
                float sx = (i % 2 == 0 ? -1.0f : 1.0f) * motorX;
                float sz = (i < 2 ? -1.0f : 1.0f) * motorZ;
                frameBuilder.uv(0, 0).cuboid(sx - 0.6f, 1.1f, sz - 0.6f, 1.2f, 1.05f, 1.2f);
            }
            frameBuilder
                    .uv(0, 0).cuboid(-1.35f, 0.85f, -2.8f, 2.7f, 2.25f, 0.65f)
                    .uv(0, 0).cuboid(-1.35f, 0.85f, 1.7f, 2.7f, 2.25f, 0.65f);
        }
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1.0f : 1.0f) * motorX;
            float z = (i < 2 ? -1.0f : 1.0f) * motorZ;
            frameBuilder.uv(0, 0).cuboid(x - 1.05f, 0.35f, z - 1.05f, 2.1f, 0.55f, 2.1f);
        }
        ModelPartData frame = root.addChild("frame", frameBuilder, ModelTransform.NONE);

        for (int i = 0; i < 4; i++) {
            float sx = i % 2 == 0 ? -1.0f : 1.0f;
            float sz = i < 2 ? -1.0f : 1.0f;
            float dx = sx * motorX - sx * 1.0f;
            float dz = sz * motorZ - sz * 1.7f;
            float yaw = (float) Math.atan2(dx, dz);
            float length = (float) Math.hypot(dx, dz) + 0.15f;
            frame.addChild("arm" + i, ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-armWidth * 0.5f, 0.45f, -0.2f, armWidth, 0.6f, length),
                    ModelTransform.of(sx * 1.0f, 0.0f, sz * 1.7f, 0.0f, yaw, 0.0f));
        }

        float antennaHeight = bomber ? 4.4f : 2.4f;
        frame.addChild("antenna", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-0.3f, -antennaHeight, -0.3f, 0.6f, antennaHeight, 0.6f)
                .uv(0, 0).cuboid(-0.55f, -antennaHeight - 0.55f, -0.55f, 1.1f, 0.65f, 1.1f),
                ModelTransform.of(0.0f, -0.85f, 5.2f, -0.48f, 0.0f, 0.0f));

        root.addChild("battery", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.3f, -2.3f, -3.8f, 2.6f, 1.35f, 7.6f)
                .uv(0, 0).cuboid(-1.45f, -2.4f, -2.3f, 2.9f, 1.5f, 0.8f)
                .uv(0, 0).cuboid(-1.45f, -2.4f, 1.5f, 2.9f, 1.5f, 0.8f), ModelTransform.NONE);

        root.addChild("camera", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.15f, -0.8f, -7.2f, 2.3f, 1.9f, 1.85f)
                .uv(0, 0).cuboid(-0.9f, -0.5f, -7.7f, 1.8f, 1.35f, 0.6f), ModelTransform.NONE);
        root.addChild("lens", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-0.7f, -0.3f, -7.95f, 1.4f, 1.0f, 0.32f), ModelTransform.NONE);

        if (variant.blast == DroneBlastType.KAMIKAZE_FRAGMENTATION) {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.4f, 1, -4.5f, 2.8f, 2, 7)
                    .uv(0, 0).cuboid(-1.6f, 1.25f, -3.5f, 3.2f, 1.5f, .5f)
                    .uv(0, 0).cuboid(-1.6f, 1.25f, -1.5f, 3.2f, 1.5f, .5f), ModelTransform.NONE);
        } else if (variant.blast == DroneBlastType.KAMIKAZE_BREACH) {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.3f, 1, -6.8f, 2.6f, 2, 9)
                    .uv(0, 0).cuboid(-.8f, 1.4f, -8.8f, 1.6f, 1.2f, 2)
                    .uv(0, 0).cuboid(-.4f, 1.65f, -9.4f, .8f, .7f, .6f), ModelTransform.NONE);
        } else if (variant.blast == DroneBlastType.BOMB_FRAGMENTATION) {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.5f, 1.3f, -2.8f, 3, 2.7f, 5.6f)
                    .uv(0, 0).cuboid(-1.6f, 1.8f, -1.8f, 3.2f, 1.7f, .5f), ModelTransform.NONE);
        } else if (variant.blast == DroneBlastType.HEAVY_STRUCTURAL_BOMB) {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.7f, 1.2f, -4.5f, 3.4f, 3.1f, 9.0f)
                    .uv(0, 0).cuboid(-0.9f, 1.8f, -5.4f, 1.8f, 1.8f, 0.9f), ModelTransform.NONE);
        } else if (variant.blast == DroneBlastType.BOMB_HIGH_EXPLOSIVE) {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.4f, 1.3f, -4, 2.8f, 2.7f, 8)
                    .uv(0, 0).cuboid(-.7f, 1.9f, -4.8f, 1.4f, 1.5f, .8f), ModelTransform.NONE);
        } else if (bomber) {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.2f, 1.3f, -3.8f, 2.4f, 2.7f, 7.6f)
                    .uv(0, 0).cuboid(-0.75f, 1.8f, -4.6f, 1.5f, 1.6f, 0.8f), ModelTransform.NONE);
        } else {
            root.addChild("payload", ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-1.2f, 1.0f, -5.0f, 2.4f, 2.0f, 9.4f)
                    .uv(0, 0).cuboid(-0.9f, 1.25f, -6.9f, 1.8f, 1.5f, 1.9f)
                    .uv(0, 0).cuboid(-0.3f, 1.7f, -9.0f, 0.6f, 0.6f, 2.1f)
                    .uv(0, 0).cuboid(-0.48f, 1.55f, -9.4f, 0.96f, 0.85f, 0.42f), ModelTransform.NONE);
        }

        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1.0f : 1.0f) * motorX;
            float z = (i < 2 ? -1.0f : 1.0f) * motorZ;
            root.addChild("motor" + i, ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-0.9f, -1.45f, -0.9f, 1.8f, 1.5f, 1.8f)
                    .uv(0, 0).cuboid(-0.4f, -1.9f, -0.4f, 0.8f, 0.45f, 0.8f),
                    ModelTransform.pivot(x, 0.7f, z));
            root.addChild("propeller" + i, ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-propRadius, -0.12f, -0.38f, propRadius * 2.0f, 0.24f, 0.76f)
                    .uv(0, 0).cuboid(-0.38f, -0.12f, -propRadius, 0.76f, 0.24f, propRadius * 2.0f)
                    .uv(0, 0).cuboid(-0.6f, -0.2f, -0.6f, 1.2f, 0.35f, 1.2f),
                    ModelTransform.pivot(x, -0.95f, z));
        }
        return TexturedModelData.of(data, 32, 32);
    }

    /**
     * Low-poly fixed-wing long-range UAV model inspired by the Ukrainian FP-1 (Sections 3.1 & 3.2):
     * Rendered at 2x scale (DroneType.FIXED_WING.renderScale = 2.96f).
     * Total: 20 cuboids = 240 triangles (well below the 1,500-triangle budget).
     */
    private static TexturedModelData fixedWingModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        // 1. Fuselage, nose cone, twin tail booms, horizontal tailplane, and twin vertical fins ("frame")
        ModelPartBuilder fuselage = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.7f, -1.2f, -8.5f, 3.4f, 2.4f, 14.5f)
                .uv(0, 0).cuboid(-1.35f, -0.85f, -11.2f, 2.7f, 1.8f, 2.7f)
                .uv(0, 0).cuboid(-0.18f, -0.15f, -12.6f, 0.36f, 0.36f, 1.4f)
                .uv(0, 0).cuboid(-1.25f, -0.95f, 6.0f, 2.5f, 1.9f, 1.8f)
                .uv(0, 0).cuboid(-4.85f, -1.15f, 0.8f, 0.7f, 0.7f, 11.6f)
                .uv(0, 0).cuboid(4.15f, -1.15f, 0.8f, 0.7f, 0.7f, 11.6f)
                .uv(0, 0).cuboid(-5.2f, -1.35f, 10.4f, 10.4f, 0.45f, 2.2f)
                .uv(0, 0).cuboid(-4.75f, -4.1f, 10.2f, 0.5f, 3.2f, 2.3f)
                .uv(0, 0).cuboid(4.25f, -4.1f, 10.2f, 0.5f, 3.2f, 2.3f)
                .uv(0, 0).cuboid(-0.2f, -3.1f, 1.8f, 0.4f, 1.9f, 0.65f);
        root.addChild("frame", fuselage, ModelTransform.NONE);

        // 2. Large straight high main wing & wingtip fences ("battery" slot — matte olive-gray wing coat)
        root.addChild("battery", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-15.0f, -1.65f, -3.2f, 30.0f, 0.55f, 4.4f)
                .uv(0, 0).cuboid(-2.2f, -1.9f, -3.6f, 4.4f, 0.45f, 5.1f)
                .uv(0, 0).cuboid(-15.35f, -2.05f, -3.4f, 0.45f, 1.3f, 4.8f)
                .uv(0, 0).cuboid(14.9f, -2.05f, -3.4f, 0.45f, 1.3f, 4.8f), ModelTransform.NONE);

        // 3. Nose optical turret & cyan glass lens ("camera" & "lens")
        root.addChild("camera", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-0.95f, 0.15f, -11.4f, 1.9f, 1.35f, 1.6f), ModelTransform.NONE);
        root.addChild("lens", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-0.6f, 0.35f, -11.75f, 1.2f, 0.85f, 0.35f), ModelTransform.NONE);

        // 4. Conformal belly warhead fairing & fixed launch skids ("payload")
        root.addChild("payload", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.4f, 1.15f, -5.6f, 2.8f, 1.05f, 9.4f)
                .uv(0, 0).cuboid(-1.15f, 2.1f, -4.2f, 0.4f, 0.9f, 6.8f)
                .uv(0, 0).cuboid(0.75f, 2.1f, -4.2f, 0.4f, 0.9f, 6.8f), ModelTransform.NONE);

        // 5. Rear pusher engine housing ("motor0") & 2-blade pusher propeller ("propeller0")
        root.addChild("motor0", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-0.8f, -0.8f, -0.6f, 1.6f, 1.6f, 1.3f),
                ModelTransform.pivot(0.0f, -0.05f, 7.9f));
        root.addChild("propeller0", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-4.2f, -0.42f, -0.16f, 8.4f, 0.84f, 0.32f)
                .uv(0, 0).cuboid(-0.42f, -4.2f, -0.16f, 0.84f, 8.4f, 0.32f)
                .uv(0, 0).cuboid(-0.5f, -0.5f, -0.32f, 1.0f, 1.0f, 0.64f),
                ModelTransform.pivot(0.0f, -0.05f, 8.75f));

        for (int i = 1; i < 4; i++) {
            root.addChild("motor" + i, ModelPartBuilder.create(), ModelTransform.NONE);
            root.addChild("propeller" + i, ModelPartBuilder.create(), ModelTransform.NONE);
        }
        return TexturedModelData.of(data, 32, 32);
    }

    static TexturedModelData shadowModelData(boolean bomber) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        float motorX = bomber ? 6.4f : 3.85f;
        float motorZ = bomber ? 7.4f : 5.45f;
        float propRadius = bomber ? 4.3f : 3.35f;
        float armWidth = bomber ? 1.4f : 1.2f;

        ModelPartBuilder frameBuilder = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.9f, 0.0f, bomber ? -7.6f : -9.2f, 3.8f, 0.01f, bomber ? 14.4f : 15.6f, SHADOW_FACE);
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1.0f : 1.0f) * motorX;
            float z = (i < 2 ? -1.0f : 1.0f) * motorZ;
            frameBuilder.uv(0, 0).cuboid(x - 1.1f, 0.08f, z - 1.1f, 2.2f, 0.01f, 2.2f, SHADOW_FACE);
        }
        ModelPartData frame = root.addChild("frame", frameBuilder, ModelTransform.NONE);
        for (int i = 0; i < 4; i++) {
            float sx = i % 2 == 0 ? -1.0f : 1.0f;
            float sz = i < 2 ? -1.0f : 1.0f;
            float dx = sx * motorX - sx * 1.0f;
            float dz = sz * motorZ - sz * 1.7f;
            float yaw = (float) Math.atan2(dx, dz);
            float length = (float) Math.hypot(dx, dz) + 0.25f;
            frame.addChild("arm" + i, ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-armWidth * 0.5f, 0.04f, -0.2f, armWidth, 0.01f, length, SHADOW_FACE),
                    ModelTransform.of(sx * 1.0f, 0.0f, sz * 1.7f, 0.0f, yaw, 0.0f));
        }
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1.0f : 1.0f) * motorX;
            float z = (i < 2 ? -1.0f : 1.0f) * motorZ;
            root.addChild("propeller" + i, ModelPartBuilder.create()
                    .uv(0, 0).cuboid(-propRadius, 0.0f, -0.45f, propRadius * 2.0f, 0.01f, 0.9f, SHADOW_FACE)
                    .uv(0, 0).cuboid(-0.45f, 0.02f, -propRadius, 0.9f, 0.01f, propRadius * 2.0f, SHADOW_FACE),
                    ModelTransform.pivot(x, 0.12f, z));
        }
        return TexturedModelData.of(data, 32, 32);
    }

    private static TexturedModelData fixedWingShadowModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartBuilder frameBuilder = ModelPartBuilder.create()
                .uv(0, 0).cuboid(-1.8f, 0.0f, -11.2f, 3.6f, 0.01f, 19.0f, SHADOW_FACE)
                .uv(0, 0).cuboid(-15.0f, 0.02f, -3.2f, 30.0f, 0.01f, 4.4f, SHADOW_FACE)
                .uv(0, 0).cuboid(-4.85f, 0.03f, 0.8f, 0.75f, 0.01f, 11.6f, SHADOW_FACE)
                .uv(0, 0).cuboid(4.1f, 0.03f, 0.8f, 0.75f, 0.01f, 11.6f, SHADOW_FACE)
                .uv(0, 0).cuboid(-5.2f, 0.04f, 10.4f, 10.4f, 0.01f, 2.2f, SHADOW_FACE);
        root.addChild("frame", frameBuilder, ModelTransform.NONE);
        for (int i = 0; i < 4; i++) {
            root.addChild("propeller" + i, ModelPartBuilder.create(), ModelTransform.NONE);
        }
        return TexturedModelData.of(data, 32, 32);
    }

    static float shadowAlpha(double drop, boolean bomber) {
        if (!Double.isFinite(drop) || drop < 0.0 || drop > MAX_SHADOW_DROP) return 0.0f;
        float t = (float) (drop / MAX_SHADOW_DROP);
        float base = bomber ? 0.44f : 0.40f;
        return base * (1.0f - t) * (1.0f - t);
    }

    static float shadowScale(float baseScale, double drop) {
        if (!Double.isFinite(drop) || drop < 0.0) return baseScale;
        float t = MathHelper.clamp((float) (drop / MAX_SHADOW_DROP), 0.0f, 1.0f);
        return baseScale * (0.97f - 0.14f * t);
    }

    static double shadowDrop(DroneEntity entity, float tickDelta) {
        World world = entity.getWorld();
        if (world == null) return Double.NaN;
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        int startY = MathHelper.floor(y + 0.08);
        int minY = Math.max(world.getBottomY(), MathHelper.floor(y - MAX_SHADOW_DROP));
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int by = startY; by >= minY; by--) {
            pos.set(bx, by, bz);
            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) continue;
            double localTop = shape.getEndingCoord(Direction.Axis.Y, x - bx, z - bz);
            if (!(localTop > 0.0)) localTop = shape.getMax(Direction.Axis.Y);
            double surfaceY = by + localTop;
            if (surfaceY <= y + 0.08) {
                double drop = Math.max(0.0, y - surfaceY);
                return drop <= MAX_SHADOW_DROP ? drop : Double.NaN;
            }
        }
        return Double.NaN;
    }

    @Override public void render(DroneEntity entity, float entityYaw, float tickDelta, MatrixStack matrices,
                                 VertexConsumerProvider vertices, int light) {
        DroneType type = entity.getDroneType();
        boolean bomber = type.isCarrier();
        boolean fixedWing = type.isFixedWing();
        if ((bomber || fixedWing) && DroneClient.isViewing(entity)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        double camDistSq = mc != null && mc.gameRenderer != null && mc.gameRenderer.getCamera() != null
                ? mc.gameRenderer.getCamera().getPos().squaredDistanceTo(entity.getPos()) : 0.0;
        DroneVariant variant = entity.getVariant();
        Parts parts = variants.get(variant);
        ShadowParts shadowParts = fixedWing ? fixedWingShadow : (bomber ? this.bomberShadow : fpvShadow);
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        float pitch = MathHelper.lerpAngleDegrees(tickDelta, entity.prevPitch, entity.getPitch());
        float bank = DroneClient.isViewing(entity) ? DroneClient.cameraRoll(tickDelta) : entity.getBankAngle();
        float scale = type.renderScale;
        float spin = entity.isArmed() || entity.isAutoLanding()
                ? (entity.age + tickDelta) * (0.8f + entity.getThrottle() * 2.3f) : 0.3f;

        double drop = shadowDrop(entity, tickDelta);
        float alpha = shadowAlpha(drop, bomber || fixedWing);
        if (alpha > 0.01f) {
            float sScale = shadowScale(scale, drop);
            matrices.push();
            matrices.translate(0.0, -drop + 0.006, 0.0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrices.scale(sScale, -0.008f * scale, -sScale);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-bank));
            VertexConsumer shadowBuffer = vertices.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));
            shadowParts.frame.render(matrices, shadowBuffer, light, OverlayTexture.DEFAULT_UV, 0.04f, 0.05f, 0.06f, alpha);
            if (!fixedWing) {
                float rotorAlpha = alpha * (entity.isArmed() || entity.isAutoLanding() ? 0.50f : 0.72f);
                for (int i = 0; i < 4; i++) {
                    shadowParts.propellers[i].yaw = spin * (i % 2 == 0 ? 1 : -1) + i;
                    shadowParts.propellers[i].render(matrices, shadowBuffer, light, OverlayTexture.DEFAULT_UV, 0.04f, 0.05f, 0.06f, rotorAlpha);
                }
            }
            matrices.pop();
        }

        matrices.push();
        matrices.translate(0, bomber ? scale * 0.25 : type.height * 0.5, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-bank));
        matrices.scale(scale, -scale, -scale);
        VertexConsumer buffer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));

        if (fixedWing) {
            parts.frame.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.43f, 0.46f, 0.41f, 1);
            parts.battery.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.36f, 0.39f, 0.34f, 1);
            parts.camera.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.20f, 0.22f, 0.21f, 1);
            parts.lens.render(matrices, buffer, 0x00f000f0, OverlayTexture.DEFAULT_UV, 0.25f, 0.72f, 0.82f, 1);
            parts.payload.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.30f, 0.33f, 0.29f, 1);
            parts.motors[0].render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.24f, 0.26f, 0.25f, 1);
            if (camDistSq <= 160.0 * 160.0) {
                parts.propellers[0].yaw = 0.0f;
                parts.propellers[0].roll = spin * 1.45f;
                parts.propellers[0].render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.28f, 0.30f, 0.28f, 1);
            }
        } else {
            parts.frame.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.19f, 0.23f, 0.22f, 1);
            parts.battery.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV,
                    bomber ? 0.34f : 0.25f, bomber ? 0.41f : 0.33f, bomber ? 0.25f : 0.38f, 1);
            parts.camera.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.16f, 0.19f, 0.19f, 1);
            parts.lens.render(matrices, buffer, 0x00f000f0, OverlayTexture.DEFAULT_UV, 0.25f, 0.72f, 0.82f, 1);
            for (int i = 0; i < 4; i++) {
                parts.motors[i].render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.28f, 0.31f, 0.30f, 1);
                if (camDistSq <= 96.0 * 96.0) {
                    parts.propellers[i].yaw = spin * (i % 2 == 0 ? 1 : -1) + i;
                    parts.propellers[i].render(matrices, buffer, light, OverlayTexture.DEFAULT_UV,
                            i < 2 ? 0.72f : 0.26f, i < 2 ? 0.47f : 0.30f, i < 2 ? 0.21f : 0.29f, 1);
                }
            }
            if (bomber) {
                for (int i = variant.payloadCapacity() - entity.getPayloadCount(); i < variant.payloadCapacity(); i++) {
                    matrices.push();
                    matrices.translate((i % 2 == 0 ? -0.16 : 0.16), 0.005, (i / 2) * 0.20);
                    matrices.scale(0.63f, 0.75f, 0.66f);
                    parts.payload.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, variant.red(), variant.green(), variant.blue(), 1);
                    matrices.pop();
                }
            } else {
                parts.payload.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, variant.red(), variant.green(), variant.blue(), 1);
            }
        }
        matrices.pop();
    }

    @Override public Identifier getTexture(DroneEntity entity) { return TEXTURE; }

    private static final class Parts {
        final ModelPart frame, battery, camera, lens, payload;
        final ModelPart[] motors = new ModelPart[4], propellers = new ModelPart[4];
        Parts(ModelPart root) {
            frame = root.getChild("frame"); battery = root.getChild("battery");
            camera = root.getChild("camera"); lens = root.getChild("lens"); payload = root.getChild("payload");
            for (int i = 0; i < 4; i++) {
                motors[i] = root.getChild("motor" + i);
                propellers[i] = root.getChild("propeller" + i);
            }
        }
    }

    private static final class ShadowParts {
        final ModelPart frame;
        final ModelPart[] propellers = new ModelPart[4];
        ShadowParts(ModelPart root) {
            frame = root.getChild("frame");
            for (int i = 0; i < 4; i++) {
                propellers[i] = root.getChild("propeller" + i);
            }
        }
    }
}
