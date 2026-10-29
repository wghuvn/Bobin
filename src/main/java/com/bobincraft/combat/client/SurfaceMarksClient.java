package com.bobincraft.combat.client;

import com.bobincraft.combat.CombatMarks;
import com.bobincraft.combat.SurfaceMark;
import com.bobincraft.combat.SurfaceMarkGeometry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Depth-tested pixel decals; they do not alter blocks, collide or require one entity per stain. */
public final class SurfaceMarksClient {
    private static final RenderLayer BLOOD = RenderLayer.getEntityTranslucent(new Identifier("bobin_craft", "textures/decal/blood.png"));
    private static final RenderLayer FRAGMENT = RenderLayer.getEntityTranslucent(new Identifier("bobin_craft", "textures/decal/shrapnel.png"));
    private static List<SurfaceMark> marks = List.of();
    private static ClientWorld world;
    private SurfaceMarksClient() { }
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { marks = List.of(); world = null; });
        ClientPlayNetworking.registerGlobalReceiver(CombatMarks.SYNC, (client, handler, buffer, sender) -> {
            try {
                Identifier dimension = buffer.readIdentifier();
                int count = buffer.readVarInt();
                if (count < 0 || count > SurfaceMark.MAX_VISIBLE) return;
                var received = new ArrayList<SurfaceMark>(count);
                for (int i = 0; i < count; i++) {
                    SurfaceMark mark = SurfaceMark.read(buffer);
                    if (mark != null) received.add(mark);
                }
                if (buffer.isReadable()) return;
                client.execute(() -> {
                    if (client.world != null && client.world.getRegistryKey().getValue().equals(dimension)) {
                        marks = List.copyOf(received); world = client.world;
                    }
                });
            } catch (RuntimeException ignored) { /* Ignore a malformed cosmetic snapshot. */ }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(SurfaceMarksClient::render);
    }
    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (world != client.world) { marks = List.of(); world = client.world; }
        if (world == null || marks.isEmpty()) return;
        var matrices = context.matrixStack();
        var origin = context.camera().getPos();
        matrices.push(); matrices.translate(-origin.x, -origin.y, -origin.z);
        var entry = matrices.peek();
        var vertices = client.getBufferBuilders().getEntityVertexConsumers();
        for (SurfaceMark mark : marks) {
            if (mark.position().squaredDistanceTo(origin) > 128 * 128 || !world.isChunkLoaded(mark.block())) continue;
            var block = world.getBlockState(mark.block());
            if (!mark.matches(Registries.BLOCK.getId(block.getBlock()).toString())
                    || !mark.supportedBy(block.getCollisionShape(world, mark.block()).getBoundingBoxes())) continue;
            var buffer = vertices.getBuffer(mark.kind() == SurfaceMark.BLOOD ? BLOOD : FRAGMENT);
            int light = WorldRenderer.getLightmapCoordinates(world, mark.block().offset(mark.face()));
            var n = mark.face().getVector();
            for (var vertex : SurfaceMarkGeometry.quad(mark)) {
                var p = vertex.point();
                buffer.vertex(entry.getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
                        .color(255, 255, 255, 230).texture(vertex.u(), vertex.v()).overlay(OverlayTexture.DEFAULT_UV)
                        .light(light).normal(entry.getNormalMatrix(), n.getX(), n.getY(), n.getZ()).next();
            }
        }
        vertices.draw(BLOOD); vertices.draw(FRAGMENT);
        matrices.pop();
    }
}
