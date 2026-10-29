package com.bobincraft.drone.client;

import com.bobincraft.drone.DroneEntity;
import com.bobincraft.drone.DroneNetworking;
import com.bobincraft.drone.DroneType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/** A transparent, non-pausing receiver with mouse steering and assisted flight by default. */
public final class DroneScreen extends Screen {
    private static final int INK = 0xffeeeeee;
    private static final int MUTED = 0xffaaaaaa;
    private final Set<Integer> pressed = new HashSet<>();
    private boolean help;
    private float zoom = 1;
    private float throttle = 0.5f;
    private float pitchInput;
    private float rollInput;
    private float yawInput;
    private float lookPitch;
    private double pendingYawDegrees;
    private boolean assisted = true;
    private boolean cursorCaptured;
    private boolean mouseInitialized;
    private double previousMouseX;
    private double previousMouseY;
    private ButtonWidget unlinkButton;
    private boolean unlinkRequested;
    private final DroneActionQueue actions = new DroneActionQueue();
    private int frameTick;
    private int noticeTicks;
    private String notice;
    private double groundClearance;

    DroneScreen() { super(Text.translatable("gui.bobin_craft.drone.title.fpv")); }

    @Override protected void init() {
        clearChildren();
        int margin = Math.max(8, Math.min(18, width / 40));
        Text unlinkLabel = Text.translatable("gui.bobin_craft.drone.unlink");
        int buttonWidth = Math.min(width - 100, textRenderer.getWidth(unlinkLabel) + 16);
        unlinkButton = addDrawableChild(ButtonWidget.builder(unlinkLabel, button -> requestUnlink())
                .dimensions(margin, 35, buttonWidth, 20).build());
        unlinkButton.active = !unlinkRequested;
        unlinkButton.visible = false;
        captureCursor();
    }

    void flightTick() {
        if (client == null || client.player == null) return;
        DroneEntity drone = DroneClient.getDrone();
        if (drone == null) return;
        if (unlinkRequested) {
            DroneClient.sendControls(0, 0, 0, 0.5f, lookPitch, actions.drain());
            return;
        }
        if (assisted != drone.isAngleMode()) {
            assisted = drone.isAngleMode();
            throttle = assisted ? 0.5f : drone.isArmed() ? 0.5f : 0;
            pitchInput = rollInput = 0;
        }
        boolean focused = client.isWindowFocused() && cursorCaptured && !pointerRequested();
        float pitchTarget = focused ? axis(GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_W) : 0;
        float rollTarget = focused ? axis(GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D) : 0;
        float yawRate = assisted ? (drone.getDroneType() == DroneType.HEAVY ? 2.6f : drone.getDroneType().isFixedWing() ? 3.2f : drone.getDroneType().isCarrier() ? 4 : 6) : 4.8f;
        double appliedYaw = assisted && drone.getDroneType() == DroneType.FPV
                ? consumeFpvMouseYaw(pendingYawDegrees, yawRate) : MathHelper.clamp(pendingYawDegrees, -yawRate, yawRate);
        float yawTarget = focused ? (float) (appliedYaw / yawRate) : 0;
        if (focused) pendingYawDegrees -= appliedYaw;
        else pendingYawDegrees = 0;
        pitchInput = MathHelper.lerp(0.5f, pitchInput, pitchTarget);
        rollInput = MathHelper.lerp(0.5f, rollInput, rollTarget);
        yawInput = yawTarget;
        long handle = client.getWindow().getHandle();
        boolean descend = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
        float lift = focused ? (InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_SPACE) ? 1 : 0) - (descend ? 1 : 0) : 0;
        if (assisted) throttle = 0.5f + lift * 0.5f;
        else if (focused) throttle = MathHelper.clamp(throttle + lift * 0.025f, 0, 1);
        if (drone.getBatteryFraction() <= 0) throttle = assisted ? 0.5f : 0;
        DroneClient.sendControls(pitchInput, rollInput, yawInput, throttle, lookPitch, actions.drain());
        if (frameTick % 5 == 0 && client.world != null) {
            var origin = drone.getPos();
            var hit = client.world.raycast(new RaycastContext(origin, origin.add(0, -256, 0),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, drone));
            groundClearance = hit.getType() == HitResult.Type.MISS
                    ? Math.max(0, drone.getY() - client.world.getBottomY()) : Math.max(0, origin.y - hit.getPos().y);
        }
    }

    private float axis(int negative, int positive) {
        long handle = client.getWindow().getHandle();
        return (InputUtil.isKeyPressed(handle, positive) ? 1 : 0) - (InputUtil.isKeyPressed(handle, negative) ? 1 : 0);
    }

    static double consumeFpvMouseYaw(double pendingDegrees, double maximumStep) {
        return MathHelper.clamp(pendingDegrees * 0.7, -maximumStep, maximumStep);
    }

    void suspendControls() {
        pressed.clear();
        pendingYawDegrees = 0;
        pitchInput = rollInput = yawInput = 0;
        releaseCursor();
    }

    void sendSuspendedHeartbeat() {
        DroneClient.sendControls(0, 0, 0, assisted ? 0.5f : throttle, lookPitch, 0);
    }

    @Override public void tick() {
        frameTick++;
        if (noticeTicks > 0) noticeTicks--;
        if (client != null && (!client.isWindowFocused() || pointerRequested())) {
            if (!client.isWindowFocused()) pressed.clear();
            pendingYawDegrees = 0;
            releaseCursor();
        } else if (!cursorCaptured) captureCursor();
    }

    private boolean pointerRequested() {
        if (client == null) return false;
        long handle = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_ALT)
                || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @Override public void mouseMoved(double mouseX, double mouseY) {
        if (client == null || !cursorCaptured || !client.isWindowFocused() || pointerRequested() || unlinkRequested) return;
        if (!mouseInitialized) {
            previousMouseX = mouseX;
            previousMouseY = mouseY;
            mouseInitialized = true;
            return;
        }
        // GLFW's disabled cursor supplies unbounded virtual positions. Restore physical pixel deltas.
        double dx = (mouseX - previousMouseX) * client.getWindow().getWidth() / Math.max(1, width);
        double dy = (mouseY - previousMouseY) * client.getWindow().getHeight() / Math.max(1, height);
        previousMouseX = mouseX;
        previousMouseY = mouseY;
        double sensitivity = client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double degreesPerPixel = sensitivity * sensitivity * sensitivity * 8 * 0.15;
        DroneEntity drone = DroneClient.getDrone();
        double turnBacklog = assisted && drone != null && drone.getDroneType() == DroneType.FPV ? 24 : 90;
        pendingYawDegrees = MathHelper.clamp(pendingYawDegrees + dx * degreesPerPixel, -turnBacklog, turnBacklog);
        int invert = client.options.getInvertYMouse().getValue() ? -1 : 1;
        if (assisted) lookPitch = MathHelper.clamp(lookPitch + (float) (dy * degreesPerPixel * invert), -80, 80);
    }

    private void captureCursor() {
        if (client == null || !client.isWindowFocused() || pointerRequested()) return;
        // Mouse.lockCursor() closes screens; native capture retains this HUD and avoids rotating the operator.
        InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_DISABLED,
                client.getWindow().getWidth() / 2.0, client.getWindow().getHeight() / 2.0);
        cursorCaptured = true;
        mouseInitialized = false;
    }

    private void releaseCursor() {
        if (client == null || !cursorCaptured) return;
        InputUtil.setCursorParameters(client.getWindow().getHandle(), GLFW.GLFW_CURSOR_NORMAL,
                client.getWindow().getWidth() / 2.0, client.getWindow().getHeight() / 2.0);
        cursorCaptured = false;
        mouseInitialized = false;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!pressed.add(keyCode)) return true;
        if (unlinkRequested && keyCode != GLFW.GLFW_KEY_ESCAPE) return true;
        DroneEntity drone = DroneClient.getDrone();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> close();
            case GLFW.GLFW_KEY_H -> help = !help;
            case 293 -> DroneViewDebug.toggle(); // GLFW_KEY_F4
            case GLFW.GLFW_KEY_U -> requestUnlink();
            case GLFW.GLFW_KEY_R -> {
                if (drone != null && !drone.isAngleMode() && !drone.isArmed() && throttle > 0.08f) {
                    notice = "status.arm_throttle";
                    noticeTicks = 60;
                } else {
                    throttle = drone != null && drone.isAngleMode() ? 0.5f : 0;
                    actions.add(DroneNetworking.ARM);
                }
            }
            case GLFW.GLFW_KEY_M -> { if (drone != null && drone.getDroneType() == DroneType.FPV) actions.add(DroneNetworking.LEVEL); }
            case GLFW.GLFW_KEY_C -> { if (drone != null && drone.getDroneType().isCarrier()) actions.add(DroneNetworking.CAMERA); }
            default -> { }
        }
        // Consume inventory, attack and ordinary movement keys while the receiver owns input.
        return true;
    }

    private void requestUnlink() {
        // Drone may be unloaded client-side (out of tracking range) while the session is still active;
        // only the active session, not a locally tracked entity, is required to send the unlink packet.
        if (unlinkRequested || !DroneClient.isActive()) return;
        unlinkRequested = true;
        actions.add(DroneNetworking.UNLINK);
        DroneClient.logAction("unlink_queued");
        pendingYawDegrees = 0;
        pitchInput = rollInput = yawInput = 0;
        throttle = 0.5f;
        if (unlinkButton != null) unlinkButton.active = false;
    }

    /** Drained by DroneClient while the drone entity isn't tracked, so a queued unlink still reaches the server. */
    int drainQueuedActions() { return actions.drain(); }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        pressed.remove(keyCode);
        return true;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (client == null) return true;
        boolean focused = client.isWindowFocused();
        boolean pointerMode = pointerRequested();
        handleFlightClick(button, focused, cursorCaptured, pointerMode);
        // Alt reserves clicks for widgets; captured flight clicks never reach the unlink button.
        if (!focused || cursorCaptured || !pointerMode || unlinkRequested) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Hardware state is sampled once by mouseClicked; only a focused flight click can release a payload. */
    void handleFlightClick(int button, boolean focused, boolean captured, boolean pointerMode) {
        if (!focused || !captured || pointerMode || unlinkRequested || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        DroneEntity drone = DroneClient.getDrone();
        if (drone == null || !drone.getDroneType().isCarrier()) return;
        if (!drone.isArmed()) { notice = "status.motors_first"; noticeTicks = 50; }
        else if (drone.getPayloadCount() > 0) {
            actions.add(DroneNetworking.DROP);
            DroneClient.logAction("payload_release_queued");
        } else { notice = "status.no_payload"; noticeTicks = 45; }
    }

    float zoom() { return zoom; }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        DroneEntity drone = DroneClient.getDrone();
        float maximum = drone != null && drone.getDroneType().isCarrier() ? 4 : 2;
        zoom = MathHelper.clamp(zoom + (float) amount * .25f, 1, maximum);
        return true;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        DroneEntity drone = DroneClient.getDrone();
        float signal = drone == null ? 0 : MathHelper.clamp(drone.getSignalStrength(), 0, 1);
        drawInterference(context, signal);
        if (drone == null) {
            context.drawCenteredTextWithShadow(textRenderer, tr("status.connecting"), width / 2, height / 2, INK);
        } else {
            DroneHud.render(context, textRenderer, width, height, drone, groundClearance, zoom);
            if (signal >= 0.25f && DroneViewDebug.isTerrainBufferingOnClient(client, drone)) {
                context.drawCenteredTextWithShadow(textRenderer, "БУФЕРИЗАЦИЯ МЕСТНОСТИ...", width / 2, height - 34, MUTED);
            }
        }
        if (unlinkButton != null) unlinkButton.visible = pointerRequested();
        if (help && drone != null) renderHelp(context, drone);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawInterference(DrawContext context, float signal) {
        var quality = DroneVideoProfile.at(signal, DroneVideoFeed.millis());
        if (quality.blackout()) context.fill(0, 0, width, height, 0xff0b0b0b);
        float noise = 1 - quality.signal();
        int seed = frameTick * 73471 + 193;
        int blockSize = Math.max(2, width / 160);
        for (int i = 0; i < quality.corruptBlocks(); i++) {
            seed = seed * 1664525 + 1013904223;
            int x = Math.floorMod(seed >>> 3, Math.max(1, width / blockSize)) * blockSize;
            seed = seed * 1664525 + 1013904223;
            int y = Math.floorMod(seed >>> 3, Math.max(1, height / blockSize)) * blockSize;
            int grey = 28 + Math.floorMod(seed >>> 10, 150);
            int colour = (45 + (int) (noise * 105)) << 24 | grey << 16 | grey << 8 | grey;
            context.fill(x, y, Math.min(width, x + blockSize * (1 + (seed & 1))), Math.min(height, y + blockSize), colour);
        }
        if (signal < .35f) {
            for (int i = 0; i < 3 + Math.round(noise * 7); i++) {
                seed = seed * 1664525 + 1013904223;
                int x = Math.floorMod(seed >>> 3, Math.max(1, width));
                int y = Math.floorMod(frameTick * 13 + i * 73, Math.max(1, height));
                int length = Math.max(2, width / 10) + Math.floorMod(seed >>> 9, Math.max(1, width / 5));
                context.fill(x, y, Math.min(width, x + length), Math.min(height, y + 1),
                        (int) (noise * 100) << 24 | 0x777777);
            }
        }
    }

    private void renderHelp(DrawContext context, DroneEntity drone) {
        int panelWidth = Math.min(390, width - 24);
        String[] keys = !drone.isAngleMode()
                ? new String[]{"help.sticks", "help.throttle", "help.arm", "help.mode_assist"}
                : drone.getDroneType().isCarrier()
                ? new String[]{"help.mouse", "help.move", "help.lift", "help.arm_assist", "help.camera", "help.drop", "help.zoom"}
                : new String[]{"help.mouse", "help.move", "help.lift", "help.arm_assist", "help.mode_assist"};
        java.util.List<net.minecraft.text.OrderedText> lines = new java.util.ArrayList<>();
        for (String key : keys) lines.addAll(textRenderer.wrapLines(Text.translatable("gui.bobin_craft.drone." + key), panelWidth - 20));
        var note = textRenderer.wrapLines(Text.translatable("gui.bobin_craft.drone."
                + (drone.isAngleMode() ? "help.note_assist" : "help.note")), panelWidth - 20);
        int panelHeight = 32 + lines.size() * 11 + note.size() * 10;
        float scale = Math.min(1, Math.max(72, height - 130) / (float) panelHeight);
        int x = Math.round((width - panelWidth * scale) / 2);
        int y = Math.max(78, Math.round((height - panelHeight * scale) / 2));
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.fill(0, 0, panelWidth, panelHeight, 0xe4111111);
        context.drawBorder(0, 0, panelWidth, panelHeight, 0xff777777);
        context.drawTextWithShadow(textRenderer, tr("help.title"), 10, 10, INK);
        int lineY = 25;
        for (var line : lines) {
            context.drawText(textRenderer, line, 10, lineY, MUTED, false);
            lineY += 11;
        }
        lineY += 3;
        for (var line : note) {
            context.drawText(textRenderer, line, 10, lineY, 0xffb8b8b8, false);
            lineY += 10;
        }
        context.getMatrices().pop();
    }

    private String tr(String key) { return Text.translatable("gui.bobin_craft.drone." + key).getString(); }
    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void close() { DroneClient.endControl(true); }
    @Override public void removed() {
        suspendControls();
    }
}
