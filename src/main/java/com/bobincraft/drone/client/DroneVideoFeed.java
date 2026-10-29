package com.bobincraft.drone.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Arrays;

/** Two bounded receiver frames: mild baseline pixelation, with delays and lower detail on weak links. */
public final class DroneVideoFeed {
    private static final Framebuffer[] FRAMES = new Framebuffer[2];
    private static final long[] TIMES = {-1, -1};
    private static final int[] WIDTHS = new int[2], HEIGHTS = new int[2];
    private static int screenWidth, screenHeight, next;
    private static long lastCapture = -1;
    private static final long CLOCK_ORIGIN = System.nanoTime();

    private DroneVideoFeed() { }

    public static long millis() { return (System.nanoTime() - CLOCK_ORIGIN) / 1_000_000; }

    public static void render() {
        if (!DroneClient.isActive() || DroneClient.getDrone() == null) return;
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        if (main.textureWidth <= 0 || main.textureHeight <= 0) return;
        if (FRAMES[0] == null || screenWidth != main.textureWidth || screenHeight != main.textureHeight) {
            reset();
            screenWidth = main.textureWidth; screenHeight = main.textureHeight;
            var capacity = DroneVideoProfile.at(1, 0).dimensions(screenWidth, screenHeight);
            try {
                for (int i = 0; i < FRAMES.length; i++) FRAMES[i] = new SimpleFramebuffer(
                        capacity.width(), capacity.height(), false, MinecraftClient.IS_SYSTEM_MAC);
            } finally { main.beginWrite(true); }
        }
        long now = millis();
        DroneVideoProfile quality = DroneVideoProfile.at(DroneClient.getDrone().getSignalStrength(), now);
        try {
            if (quality.capture(now, lastCapture)) {
                var size = quality.dimensions(screenWidth, screenHeight);
                WIDTHS[next] = size.width();
                HEIGHTS[next] = size.height();
                blit(main, FRAMES[next], screenWidth, screenHeight, WIDTHS[next], HEIGHTS[next], GL11.GL_NEAREST);
                TIMES[next] = lastCapture = now;
                next = (next + 1) % FRAMES.length;
            }
            int selected = DroneVideoProfile.selectFrame(TIMES, now - quality.latencyMillis());
            if (selected >= 0) blit(FRAMES[selected], main, WIDTHS[selected], HEIGHTS[selected], screenWidth, screenHeight, GL11.GL_NEAREST);
        } finally { main.beginWrite(true); }
    }

    private static void blit(Framebuffer from, Framebuffer to, int fromWidth, int fromHeight, int toWidth, int toHeight, int filter) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, fromWidth, fromHeight, 0, 0, toWidth, toHeight, GL11.GL_COLOR_BUFFER_BIT, filter);
    }

    public static void reset() {
        for (int i = 0; i < FRAMES.length; i++) if (FRAMES[i] != null) { FRAMES[i].delete(); FRAMES[i] = null; }
        Arrays.fill(TIMES, -1); lastCapture = -1; next = 0;
    }
}
