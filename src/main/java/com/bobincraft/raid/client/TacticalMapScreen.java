package com.bobincraft.raid.client;

import com.bobincraft.raid.DeploymentPlanner;
import com.bobincraft.raid.RaidNetworking;
import com.bobincraft.raid.TacticalMapData;
import com.bobincraft.warzone.WarzoneDimension;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

/** A server-supplied map. Deploy requests contain a rotation token and an offered point ID. */
public class TacticalMapScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int TOP = 27;
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 6.0;
    private static final int ROAD_COLOR = 0xffd9bd87;
    private static final int EXTRACTION_COLOR = 0xff7dffb2;
    private static final int PLAYER_OUTLINE_COLOR = 0xff0b1216;
    private static final int PLAYER_RING_COLOR = 0xffffffff;
    private static final int PLAYER_CORE_COLOR = 0xff00c8ff;
    private static final int PLAYER_ARROW_COLOR = 0xff42ecff;

    private final TacticalMapData map;
    private final boolean fieldMode;
    private static SavedView savedFieldView;
    private record SavedView(int min, int size, int terrainHash, double x, double z, double zoom) {}
    private Identifier terrainTexture;
    private double centerX;
    private double centerZ;
    private double zoom = MIN_ZOOM;
    private int selectedIndex;
    private int mapLeft;
    private int mapTop;
    private int mapWidth;
    private int mapHeight;
    private boolean dragging;
    private boolean submitted;
    private boolean mapKeyReleased;
    private ButtonWidget deployButton;

    public TacticalMapScreen(TacticalMapData map) {
        this(map, false);
    }

    protected TacticalMapScreen(TacticalMapData map, boolean fieldMode) {
        super(Text.translatable(fieldMode ? "gui.bobin_craft.tactical.title" : "gui.bobin_craft.raid.title"));
        if (map.fieldMap() != fieldMode) throw new IllegalArgumentException("Wrong map interface for payload");
        this.map = map;
        this.fieldMode = fieldMode;
        this.centerX = map.min() + map.size() / 2.0;
        this.centerZ = centerX;
        this.selectedIndex = map.deployments().isEmpty() ? -1 : 0;
        if (fieldMode && savedFieldView != null && savedFieldView.min() == map.min()
                && savedFieldView.size() == map.size() && savedFieldView.terrainHash() == java.util.Arrays.hashCode(map.colors())) {
            this.centerX = savedFieldView.x();
            this.centerZ = savedFieldView.z();
            this.zoom = savedFieldView.zoom();
        }
    }

    static void clearRememberedView() { savedFieldView = null; }

    /** Converts Minecraft player yaw (degrees, 0=South/+Z, -90=East/+X, 90=West/-X, 180=North/-Z) to a unit (dx, dz) map vector. */
    public static double[] headingVector(float yawDegrees) {
        double rad = Math.toRadians(yawDegrees);
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    public static String compassDirection(float yawDegrees) {
        double[] v = headingVector(yawDegrees);
        double angle = Math.toDegrees(Math.atan2(v[0], -v[1])); // 0 = North (-Z), 90 = East (+X)
        if (angle < 0) angle += 360.0;
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[(int) Math.floor((angle + 22.5) / 45.0) & 7];
    }

    @Override
    public void init() {
        mapKeyReleased = !RaidClient.mapKeyHeld(client);
        mapLeft = MARGIN;
        mapTop = TOP;
        mapWidth = Math.max(24, width - MARGIN * 2);
        mapHeight = Math.max(24, height - TOP - (fieldMode ? 61 : 66));
        clampCenter();
        terrainTexture = TacticalMapTextureCache.texture(client, map);

        int buttonWidth = Math.max(60, Math.min(130, (width - 32) / 2));
        if (fieldMode) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.bobin_craft.raid.close"), button -> close())
                    .dimensions(width / 2 - buttonWidth / 2, height - 25, buttonWidth, 20).build());
            return;
        }
        deployButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.bobin_craft.raid.deploy"), button -> deploy())
                .dimensions(width / 2 - buttonWidth - 4, height - 26, buttonWidth, 20).build());
        deployButton.active = selectedPoint() != null && !submitted;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.bobin_craft.raid.close"), button -> close())
                .dimensions(width / 2 + 4, height - 26, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xf01e241b);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, 0xffe9e3d2);
        if (hasLivePlayer()) {
            double px = livePlayerX(delta);
            double pz = livePlayerZ(delta);
            float yaw = client.player.getYaw(delta);
            String coords = String.format("X: %d  Z: %d  [%s]", MathHelper.floor(px), MathHelper.floor(pz), compassDirection(yaw));
            context.drawTextWithShadow(textRenderer, coords, Math.max(MARGIN, width - MARGIN - textRenderer.getWidth(coords)), 9, 0xff7dffb2);
        }
        context.fill(mapLeft - 1, mapTop - 1, mapLeft + mapWidth + 1, mapTop + mapHeight + 1, 0xff899071);
        context.fill(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight, 0xff1a2119);

        context.enableScissor(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight);
        double scale = pixelsPerBlock();
        int terrainLeft = worldToScreenX(map.min());
        int terrainTop = worldToScreenY(map.min());
        int terrainSize = Math.max(1, (int) Math.ceil(map.size() * scale));
        context.drawTexture(terrainTexture, terrainLeft, terrainTop, terrainSize, terrainSize,
                0.0f, 0.0f, map.resolution(), map.resolution(), map.resolution(), map.resolution());

        for (TacticalMapData.Segment road : map.roads()) {
            int x0 = worldToScreenX(road.x0()), y0 = worldToScreenY(road.z0());
            int x1 = worldToScreenX(road.x1()), y1 = worldToScreenY(road.z1());
            if (Math.max(x0, x1) < mapLeft || Math.min(x0, x1) > mapLeft + mapWidth
                    || Math.max(y0, y1) < mapTop || Math.min(y0, y1) > mapTop + mapHeight) continue;
            drawLine(context, x0, y0, x1, y1, 3, 0xaa302d23);
            drawLine(context, x0, y0, x1, y1, 1, ROAD_COLOR);
        }
        for (TacticalMapData.Marker poi : map.pois()) {
            int x = worldToScreenX(poi.x());
            int y = worldToScreenY(poi.z());
            if (x < mapLeft - 4 || x > mapLeft + mapWidth + 4 || y < mapTop - 4 || y > mapTop + mapHeight + 4) continue;
            context.fill(x - 3, y - 3, x + 4, y + 4, 0xff161b1d);
            context.fill(x - 2, y - 2, x + 3, y + 3, dangerColor(poi.danger()));
        }

        DeploymentPlanner.Point extraction = map.extraction();
        if (extraction != null) {
            int x = worldToScreenX(extraction.x());
            int y = worldToScreenY(extraction.z());
            context.fill(x - 5, y - 5, x + 6, y + 6, 0xff122c23);
            context.drawBorder(x - 5, y - 5, 11, 11, EXTRACTION_COLOR);
            context.drawCenteredTextWithShadow(textRenderer, "E", x, y - 4, EXTRACTION_COLOR);
        }
        for (int i = 0; i < map.deployments().size(); i++) {
            DeploymentPlanner.Point point = map.deployments().get(i);
            int x = worldToScreenX(point.x());
            int y = worldToScreenY(point.z());
            boolean selected = i == selectedIndex;
            context.fill(x - 7, y - 7, x + 8, y + 8, selected ? 0xfff2dd82 : 0xff172a35);
            context.fill(x - 5, y - 5, x + 6, y + 6, dangerColor(point.danger()));
            context.drawCenteredTextWithShadow(textRenderer, Integer.toString(i + 1), x, y - 4, 0xffffffff);
        }
        if (hasLivePlayer()) {
            drawPlayerMarker(context, delta);
        }
        context.disableScissor();

        if (fieldMode) {
            Text legend = Text.translatable("gui.bobin_craft.tactical.legend");
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(legend.getString(), Math.max(1, width - MARGIN * 2)),
                    MARGIN, height - 54, 0xffd7dcc0);
            Text controls = Text.translatable("gui.bobin_craft.tactical.controls", RaidClient.mapKeyName());
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(controls.getString(), Math.max(1, width - MARGIN * 2)),
                    MARGIN, height - 40, 0xffa6af92);
        } else {
            DeploymentPlanner.Point selected = selectedPoint();
            Text selection = selected == null ? Text.translatable("gui.bobin_craft.raid.none")
                    : Text.translatable("gui.bobin_craft.raid.selected", selected.label(),
                    selected.x() + ", " + selected.z(), Text.translatable("gui.bobin_craft.raid.danger", selected.danger()));
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(selection.getString(), Math.max(1, width - MARGIN * 2)),
                    MARGIN, height - 57, 0xffece6d5);
            Text controls = Text.translatable("gui.bobin_craft.raid.controls");
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(controls.getString(), Math.max(1, width - MARGIN * 2)),
                    MARGIN, height - 42, 0xffaab5bc);
        }
        super.render(context, mouseX, mouseY, delta);
        drawMapTooltip(context, mouseX, mouseY);
    }

    private boolean hasLivePlayer() {
        return client != null && client.player != null && (fieldMode || (client.world != null && WarzoneDimension.isWarzone(client.world)));
    }

    private double livePlayerX(float delta) {
        return client.player.age > 1 ? MathHelper.lerp(delta, client.player.prevX, client.player.getX()) : client.player.getX();
    }

    private double livePlayerZ(float delta) {
        return client.player.age > 1 ? MathHelper.lerp(delta, client.player.prevZ, client.player.getZ()) : client.player.getZ();
    }

    private void drawPlayerMarker(DrawContext context, float delta) {
        double px = livePlayerX(delta);
        double pz = livePlayerZ(delta);
        float yaw = client.player.getYaw(delta);
        int rawX = worldToScreenX(px);
        int rawY = worldToScreenY(pz);
        int sx = MathHelper.clamp(rawX, mapLeft + 6, mapLeft + mapWidth - 6);
        int sy = MathHelper.clamp(rawY, mapTop + 6, mapTop + mapHeight - 6);

        double[] dir = headingVector(yaw);
        double dx = dir[0];
        double dz = dir[1];
        double perpX = -dz;
        double perpY = dx;

        int tipX = (int) Math.round(sx + dx * 10.0);
        int tipY = (int) Math.round(sy + dz * 10.0);
        int wing1X = (int) Math.round(sx + dx * 4.0 + perpX * 4.5);
        int wing1Y = (int) Math.round(sy + dz * 4.0 + perpY * 4.5);
        int wing2X = (int) Math.round(sx + dx * 4.0 - perpX * 4.5);
        int wing2Y = (int) Math.round(sy + dz * 4.0 - perpY * 4.5);

        // High-contrast dark outline around direction indicator and marker body.
        drawLine(context, sx, sy, tipX, tipY, 5, PLAYER_OUTLINE_COLOR);
        drawLine(context, tipX, tipY, wing1X, wing1Y, 4, PLAYER_OUTLINE_COLOR);
        drawLine(context, tipX, tipY, wing2X, wing2Y, 4, PLAYER_OUTLINE_COLOR);
        context.fill(sx - 5, sy - 5, sx + 6, sy + 6, PLAYER_OUTLINE_COLOR);

        // Bright directional chevron and 3-layer marker dot.
        drawLine(context, sx, sy, tipX, tipY, 3, PLAYER_ARROW_COLOR);
        drawLine(context, tipX, tipY, wing1X, wing1Y, 2, PLAYER_RING_COLOR);
        drawLine(context, tipX, tipY, wing2X, wing2Y, 2, PLAYER_RING_COLOR);
        context.fill(sx - 4, sy - 4, sx + 5, sy + 5, PLAYER_RING_COLOR);
        context.fill(sx - 3, sy - 3, sx + 4, sy + 4, PLAYER_CORE_COLOR);
        context.fill(sx - 1, sy - 1, sx + 2, sy + 2, PLAYER_RING_COLOR);
    }

    private void drawMapTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!insideMap(mouseX, mouseY)) return;
        if (hasLivePlayer()) {
            double px = client.player.getX();
            double pz = client.player.getZ();
            if (near(mouseX, mouseY, px, pz, 8)) {
                String label = String.format("%s (%d, %d) [%s]",
                        client.player.getName().getString(),
                        MathHelper.floor(px),
                        MathHelper.floor(pz),
                        compassDirection(client.player.getYaw()));
                context.drawTooltip(textRenderer, Text.literal(label), mouseX, mouseY);
                return;
            }
        }
        int deployment = deploymentAt(mouseX, mouseY);
        if (deployment >= 0) {
            DeploymentPlanner.Point point = map.deployments().get(deployment);
            context.drawTooltip(textRenderer, Text.translatable("gui.bobin_craft.raid.selected", point.label(),
                    point.x() + ", " + point.z(), Text.translatable("gui.bobin_craft.raid.danger", point.danger())), mouseX, mouseY);
            return;
        }
        DeploymentPlanner.Point extraction = map.extraction();
        if (extraction != null && near(mouseX, mouseY, extraction.x(), extraction.z(), 8)) {
            context.drawTooltip(textRenderer, Text.translatable("gui.bobin_craft.raid.extraction"), mouseX, mouseY);
            return;
        }
        for (TacticalMapData.Marker poi : map.pois()) {
            if (near(mouseX, mouseY, poi.x(), poi.z(), 6)) {
                Text label = fieldMode ? Text.translatable("gui.bobin_craft.tactical.landmark", poi.label(), poi.danger())
                        : Text.translatable("gui.bobin_craft.raid.selected", poi.label(),
                        poi.x() + ", " + poi.z(), Text.translatable("gui.bobin_craft.raid.danger", poi.danger()));
                context.drawTooltip(textRenderer, label, mouseX, mouseY);
                return;
            }
        }
    }

    private static int dangerColor(int danger) {
        if (danger <= 33) return 0xff759d66;
        if (danger <= 66) return 0xffb18b43;
        return 0xffe5786a;
    }

    private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int thickness, int color) {
        double dx = x1 - (double) x0;
        double dy = y1 - (double) y0;
        int length = (int) Math.ceil(Math.hypot(dx, dy));
        if (length == 0) return;
        context.getMatrices().push();
        context.getMatrices().translate(x0, y0, 0);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        context.fill(0, -thickness / 2, length, (thickness + 1) / 2, color);
        context.getMatrices().pop();
    }

    private void deploy() {
        DeploymentPlanner.Point point = selectedPoint();
        if (fieldMode || submitted || point == null || !ClientPlayNetworking.canSend(RaidNetworking.DEPLOY)) return;
        submitted = true;
        deployButton.active = false;
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeLong(map.rotation());
        buffer.writeInt(point.id());
        ClientPlayNetworking.send(RaidNetworking.DEPLOY, buffer);
        close();
    }

    private DeploymentPlanner.Point selectedPoint() {
        return selectedIndex >= 0 && selectedIndex < map.deployments().size() ? map.deployments().get(selectedIndex) : null;
    }

    public double pixelsPerBlock() {
        return Math.min(mapWidth, mapHeight) * zoom / map.size();
    }

    public int worldToScreenX(double worldX) {
        return (int) Math.round(mapLeft + mapWidth / 2.0 + (worldX - centerX) * pixelsPerBlock());
    }

    public int worldToScreenY(double worldZ) {
        return (int) Math.round(mapTop + mapHeight / 2.0 + (worldZ - centerZ) * pixelsPerBlock());
    }

    public double screenToWorldX(double screenX) {
        return centerX + (screenX - mapLeft - mapWidth / 2.0) / pixelsPerBlock();
    }

    public double screenToWorldZ(double screenY) {
        return centerZ + (screenY - mapTop - mapHeight / 2.0) / pixelsPerBlock();
    }

    public void setViewportForTest(int left, int top, int width, int height) {
        this.mapLeft = left;
        this.mapTop = top;
        this.mapWidth = width;
        this.mapHeight = height;
        clampCenter();
    }

    private boolean insideMap(double x, double y) {
        return x >= mapLeft && y >= mapTop && x < mapLeft + mapWidth && y < mapTop + mapHeight;
    }

    private boolean near(double screenX, double screenY, double worldX, double worldZ, int radius) {
        return Math.abs(screenX - worldToScreenX(worldX)) <= radius && Math.abs(screenY - worldToScreenY(worldZ)) <= radius;
    }

    private int deploymentAt(double mouseX, double mouseY) {
        for (int i = 0; i < map.deployments().size(); i++) {
            DeploymentPlanner.Point point = map.deployments().get(i);
            if (near(mouseX, mouseY, point.x(), point.z(), 9)) return i;
        }
        return -1;
    }

    private void clampCenter() {
        double halfX = mapWidth / (2.0 * pixelsPerBlock());
        double halfZ = mapHeight / (2.0 * pixelsPerBlock());
        double midpoint = map.min() + map.size() / 2.0;
        centerX = halfX * 2 >= map.size() ? midpoint : MathHelper.clamp(centerX, map.min() + halfX, map.min() + map.size() - halfX);
        centerZ = halfZ * 2 >= map.size() ? midpoint : MathHelper.clamp(centerZ, map.min() + halfZ, map.min() + map.size() - halfZ);
    }

    private void zoomAt(double amount, double screenX, double screenY) {
        double anchorX = screenToWorldX(screenX);
        double anchorZ = screenToWorldZ(screenY);
        zoom = MathHelper.clamp(zoom * Math.pow(1.2, amount), MIN_ZOOM, MAX_ZOOM);
        centerX = anchorX - (screenX - mapLeft - mapWidth / 2.0) / pixelsPerBlock();
        centerZ = anchorZ - (screenY - mapTop - mapHeight / 2.0) / pixelsPerBlock();
        clampCenter();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (fieldMode && RaidClient.matchesMapMouse(button)) {
            if (mapKeyReleased) close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideMap(mouseX, mouseY)) {
            int point = deploymentAt(mouseX, mouseY);
            if (point >= 0) {
                selectedIndex = point;
                deployButton.active = !submitted;
            } else {
                dragging = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (fieldMode && RaidClient.matchesMapKey(keyCode, scanCode)) mapKeyReleased = true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            centerX -= deltaX / pixelsPerBlock();
            centerZ -= deltaY / pixelsPerBlock();
            clampCenter();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (fieldMode && RaidClient.matchesMapMouse(button)) mapKeyReleased = true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (insideMap(mouseX, mouseY)) {
            zoomAt(amount, mouseX, mouseY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fieldMode && RaidClient.matchesMapKey(keyCode, scanCode)) {
            if (mapKeyReleased) close();
            return true;
        }
        int index = keyCode - GLFW.GLFW_KEY_1;
        if (index >= 0 && index < map.deployments().size()) {
            selectedIndex = index;
            deployButton.active = !submitted;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD
                || keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomAt(keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT ? -1 : 1,
                    mapLeft + mapWidth / 2.0, mapTop + mapHeight / 2.0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            zoom = MIN_ZOOM;
            centerX = centerZ = map.min() + map.size() / 2.0;
            clampCenter();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (hasLivePlayer()) {
                centerX = client.player.getX();
                centerZ = client.player.getZ();
                clampCenter();
                return true;
            }
        }
        double step = 20.0 / pixelsPerBlock();
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_A
                || keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_D) {
            if (keyCode == GLFW.GLFW_KEY_A) centerX -= step;
            if (keyCode == GLFW.GLFW_KEY_D) centerX += step;
            if (keyCode == GLFW.GLFW_KEY_W) centerZ -= step;
            if (keyCode == GLFW.GLFW_KEY_S) centerZ += step;
            clampCenter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (!mapKeyReleased && client != null && !RaidClient.mapKeyHeld(client)) mapKeyReleased = true;
        if (fieldMode && (client == null || client.world == null || !WarzoneDimension.isWarzone(client.world))) close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        dragging = false;
        if (fieldMode) {
            savedFieldView = new SavedView(map.min(), map.size(), java.util.Arrays.hashCode(map.colors()), centerX, centerZ, zoom);
        }
        terrainTexture = null; // The session cache owns the reusable texture.
        super.removed();
    }
}
