package com.bobincraft.drone.client;

import com.bobincraft.drone.DroneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Sparse camera OSD. All displayed measurements come from the aircraft, never invented camera settings. */
final class DroneHud {
    private static final int WHITE = 0xffeeeeee, GREY = 0xffb8b8b8, RED = 0xffd84a44, GREEN = 0xff8eaa79;
    private DroneHud() { }

    static void render(DrawContext c, TextRenderer font, int width, int height, DroneEntity drone,
                       double ground, float zoom) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean bomber = drone.getDroneType().isCarrier();
        boolean fixedWing = drone.getDroneType().isFixedWing();
        String code = switch (drone.getVariant()) {
            case FPV -> "FPV GP";
            case FPV_FRAGMENTATION -> "FPV FRAG";
            case FPV_BREACH -> "FPV BR";
            case BOMBER -> "UAV GP";
            case BOMBER_HIGH_EXPLOSIVE -> "UAV HE";
            case BOMBER_FRAGMENTATION -> "UAV FRAG";
            case HEAVY_BOMBER -> "HVY UAV";
            case FP1 -> "FP-1 OWA";
        };
        int margin = Math.max(8, Math.min(16, width / 40));
        c.drawTextWithShadow(font, code, margin, 12, WHITE);
        String state = fixedWing
                ? (drone.isArmed() ? "CRUISE  CAM F" : "STBY  CAM F")
                : (drone.isArmed() ? "ON" : "STBY") + (bomber && drone.isDownCamera() ? "  CAM D" : "  CAM F");
        c.drawTextWithShadow(font, state, width - margin - font.getWidth(state), 12, GREY);
        int zx = width / 2 - 24;
        c.drawBorder(zx, 8, 48, 17, GREEN);
        String z = String.format(Locale.ROOT, "%.2fX", zoom);
        c.drawCenteredTextWithShadow(font, z, width / 2, 13, WHITE);
        int heading = Math.floorMod(Math.round(drone.getYaw()) + 180, 360);
        c.drawCenteredTextWithShadow(font, String.format(Locale.ROOT, "%03d°", heading), width / 2, 30, GREY);

        int cx = width / 2, cy = height / 2;
        c.fill(cx - 10, cy, cx - 4, cy + 1, 0xbbffffff); c.fill(cx + 4, cy, cx + 10, cy + 1, 0xbbffffff);
        c.fill(cx, cy - 10, cx + 1, cy - 4, 0xbbffffff); c.fill(cx, cy + 4, cx + 1, cy + 10, 0xbbffffff);

        // Fixed-wing artificial horizon / bank & pitch indicator bars around center reticle
        if (fixedWing) {
            int pitchOffset = Math.round(drone.getPitch() * 0.75f);
            int bankOffset = Math.round(drone.getBankAngle() * 0.30f);
            c.fill(cx - 28, cy + pitchOffset - bankOffset, cx - 15, cy + pitchOffset - bankOffset + 1, GREEN);
            c.fill(cx + 15, cy + pitchOffset + bankOffset, cx + 28, cy + pitchOffset + bankOffset + 1, GREEN);
        }

        int y = height - 18;
        String measures = String.format(Locale.ROOT, "H %.1fm  D %.0fm  V %.1f", ground,
                client.player == null ? 0 : drone.distanceTo(client.player), DroneClient.getSpeed());
        c.drawTextWithShadow(font, measures, margin, y, WHITE);
        String payload = bomber ? "  " + drone.getPayloadCount() + "/" + drone.getVariant().payloadCapacity() : "";
        String battery = Math.round(drone.getBatteryFraction() * 100) + "%" + payload;
        int right = width - margin - font.getWidth(battery);
        c.drawTextWithShadow(font, battery, right, y, drone.getBatteryFraction() < .15 ? RED : WHITE);
        int barsX = right - 25;
        for (int i = 0; i < 4; i++) c.fill(barsX + i * 5, y + 8 - (i + 1) * 2,
                barsX + i * 5 + 3, y + 9, drone.getSignalStrength() >= (i + 1) * .2f ? WHITE : 0xff555555);
        DroneViewDebug.render(c, font, width, height, drone);
    }
}
