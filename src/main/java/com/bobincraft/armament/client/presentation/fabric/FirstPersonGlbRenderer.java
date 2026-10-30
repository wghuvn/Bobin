package com.bobincraft.armament.client.presentation.fabric;

import com.bobincraft.armament.client.mixin.GameRendererFovAccess;
import com.bobincraft.armament.client.presentation.FirstPersonWeaponPresenter;
import com.bobincraft.armament.client.render.fabric.FabricGlbRenderer;
import com.bobincraft.armament.client.render.fabric.GlbRenderContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.util.Objects;

/** Fabric/Yarn 1.20.1 render adapter. Samples poses; never advances the action state machine. */
public final class FirstPersonGlbRenderer {
    private final FabricGlbRenderer backend;
    private final VanillaPlayerArmRenderer arms = new VanillaPlayerArmRenderer();
    private final double[] handView = new double[16], projection = new double[16], cameraToWorld = new double[16];
    private final float[] transfer = new float[16];
    private final Matrix4f viewProjection = new Matrix4f(), cameraMatrix = new Matrix4f(), normalMatrix4 = new Matrix4f();
    private final Matrix3f normalMatrix = new Matrix3f();
    private String result = "not_rendered";
    private double fov;

    public FirstPersonGlbRenderer(FabricGlbRenderer backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    /** True only after an actual GLB draw; the mixin can then cancel the vanilla hand pass. */
    public boolean render(MinecraftClient client, long visualTick, float tickDelta, MatrixStack matrices,
                          VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light,
                          FirstPersonWeaponPresenter presenter, FirstPersonResources.Bundle bundle) {
        RenderSystem.assertOnRenderThread();
        if (!backend.hasShader() || Tessellator.getInstance().getBuffer().isBuilding()) {
            result = "shader_or_buffer_unavailable";
            return false;
        }
        if (!arms.available(client, player)) {
            result = "vanilla_player_renderer_unavailable";
            return false;
        }
        var camera = client.gameRenderer.getCamera();
        // false selects vanilla's hand projection, without world zoom/sprint FOV effects.
        fov = ((GameRendererFovAccess) client.gameRenderer).bobin$armamentFov(camera, tickDelta, false);
        matrix(client.gameRenderer.getBasicProjectionMatrix(fov), projection);
        matrix(matrices.peek().getPositionMatrix(), handView);
        cameraMatrix.identity().rotation(camera.getRotation());
        matrix(cameraMatrix, cameraToWorld);
        if (!presenter.sample(visualTick, tickDelta, handView, projection, cameraToWorld)) {
            result = presenter.rejection();
            return false;
        }
        presenter.copyProjection(transfer);
        viewProjection.set(transfer);
        presenter.copyNormals(transfer);
        normalMatrix4.set(transfer);
        normalMatrix.set(normalMatrix4);

        // Flush the caller's pending immediate vertices before touching the shared GLB buffer.
        consumers.draw();
        backend.render(bundle.cpu().model(), presenter.pose(), new GlbRenderContext(viewProjection, light,
                new Vec3d(presenter.cameraInModel(0), presenter.cameraInModel(1), presenter.cameraInModel(2)),
                bundle.gpu(), normalMatrix));
        arms.render(client, player, matrices, consumers, light, presenter, bundle.arms());
        consumers.draw();
        result = "rendered";
        return true;
    }

    public String result() { return result; }
    public double fov() { return fov; }

    private void matrix(Matrix4f source, double[] destination) {
        source.get(transfer);
        for (int i = 0; i < 16; i++) destination[i] = transfer[i];
    }
}
