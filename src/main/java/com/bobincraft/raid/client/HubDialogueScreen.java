package com.bobincraft.raid.client;

import com.bobincraft.raid.HubDialogueSession;
import com.bobincraft.raid.HubNpcInteraction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.UUID;

/** Branching face-to-face conversations use the same field dossier palette as service windows. */
public final class HubDialogueScreen extends Screen {
    private final UUID entityId;
    private final UUID sessionToken;
    private final HubNpcInteraction.Role role;
    private HubNpcInteraction.Topic topic;
    private int panelX, panelY, panelWidth, panelHeight;
    private int nextActionTick;
    private int localTick;
    private int responseScroll;
    private boolean waiting;

    public HubDialogueScreen(UUID entity, UUID token, HubNpcInteraction.Role role, HubNpcInteraction.Topic topic, Text name) {
        super(name);
        entityId = entity;
        sessionToken = token;
        this.role = role;
        this.topic = topic;
    }

    public UUID sessionToken() { return sessionToken; }

    public void setTopic(HubNpcInteraction.Topic topic) {
        this.topic = topic;
        waiting = false;
        responseScroll = 0;
        clearAndInit();
    }

    @Override protected void init() {
        panelWidth = Math.min(440, width - 16);
        panelHeight = Math.min(254, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int left = panelX + 12;
        int buttonsTop = panelY + panelHeight - (role.service() ? 111 : 89);
        int buttonWidth = panelWidth - 24;
        addChoice(left, buttonsTop, buttonWidth, HubNpcInteraction.Choice.STATUS, "status");
        addChoice(left, buttonsTop + 22, buttonWidth, HubNpcInteraction.Choice.RULES, "rules");
        addChoice(left, buttonsTop + 44, buttonWidth, HubNpcInteraction.Choice.DUTY, role.key() + ".duty");
        if (role.service()) {
            addChoice(left, buttonsTop + 66, buttonWidth - 92, HubNpcInteraction.Choice.TRADE, role.key() + ".service");
            addChoice(left + buttonWidth - 88, buttonsTop + 66, 88, HubNpcInteraction.Choice.GOODBYE, "goodbye");
        } else {
            addChoice(left, buttonsTop + 66, buttonWidth, HubNpcInteraction.Choice.GOODBYE, "goodbye");
        }
    }

    private void addChoice(int left, int top, int buttonWidth, HubNpcInteraction.Choice choice, String key) {
        ButtonWidget button = new FieldButton(left, top, buttonWidth,
                Text.translatable("gui.bobin_craft.npc.choice." + key), pressed -> submit(choice), HubMerchantScreen.accent(role));
        button.active = !waiting && localTick >= nextActionTick;
        addDrawableChild(button);
    }

    private void submit(HubNpcInteraction.Choice choice) {
        if (waiting || localTick < nextActionTick) return;
        nextActionTick = localTick + HubDialogueSession.ACTION_INTERVAL_TICKS;
        waiting = true;
        HubNpcClient.choose(entityId, sessionToken, choice);
        if (choice == HubNpcInteraction.Choice.GOODBYE) {
            if (client != null) client.setScreen(null);
        } else clearAndInit();
    }

    @Override public void tick() {
        localTick++;
        if (client == null || client.player == null || client.world == null) { close(); return; }
        Entity npc = findNpc();
        if (npc == null || !npc.isAlive() || client.player.squaredDistanceTo(npc) > HubDialogueSession.MAX_DISTANCE_SQUARED) {
            close(); return;
        }
        if (!waiting && localTick == nextActionTick) clearAndInit();
        // A server refusal or interrupted connection can be retried without locking the interface.
        if (waiting && localTick > nextActionTick + 40) { waiting = false; clearAndInit(); }
    }

    private Entity findNpc() {
        if (client == null || client.world == null) return null;
        for (Entity entity : client.world.getEntities()) if (entity.getUuid().equals(entityId)) return entity;
        return null;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int accent = HubMerchantScreen.accent(role);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xff111915);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xff68715e);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 3, accent);
        context.drawText(textRenderer, Text.translatable("gui.bobin_craft.npc.contact"), panelX + 12, panelY + 12, accent, false);
        context.drawText(textRenderer, textRenderer.trimToWidth(title.getString(), panelWidth - 30), panelX + 12, panelY + 28, 0xffe9e6d1, false);
        int portraitWidth = panelWidth >= 350 ? 80 : 0;
        int textX = panelX + 12 + portraitWidth;
        int textTop = panelY + 48;
        int buttonsTop = panelY + panelHeight - (role.service() ? 111 : 89);
        if (portraitWidth > 0) {
            context.fill(panelX + 11, textTop, panelX + 80, buttonsTop - 8, 0xff202c23);
            Entity npc = findNpc();
            if (npc instanceof LivingEntity living) {
                InventoryScreen.drawEntity(context, panelX + 44, buttonsTop - 12,
                        Math.min(25, (buttonsTop - textTop) / 3), 18, 0, living);
            }
        }
        Text reply = Text.translatable("dialogue.bobin_craft." + role.key() + "." + topic.name().toLowerCase(Locale.ROOT));
        var lines = textRenderer.wrapLines(reply, panelWidth - 24 - portraitWidth);
        int visible = Math.max(1, (buttonsTop - textTop - 9) / 11);
        responseScroll = MathHelper.clamp(responseScroll, 0, Math.max(0, lines.size() - visible));
        context.enableScissor(textX, textTop, panelX + panelWidth - 10, buttonsTop - 7);
        for (int line = responseScroll; line < Math.min(lines.size(), responseScroll + visible); line++) {
            context.drawText(textRenderer, lines.get(line), textX, textTop + (line - responseScroll) * 11, 0xffc7cebd, false);
        }
        context.disableScissor();
        if (lines.size() > visible) context.drawText(textRenderer, "↕", panelX + panelWidth - 9, buttonsTop - 18, accent, false);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        responseScroll = Math.max(0, responseScroll - (int) Math.signum(amount));
        return true;
    }

    @Override public void close() {
        HubNpcClient.choose(entityId, sessionToken, HubNpcInteraction.Choice.GOODBYE);
        if (client != null) client.setScreen(null);
    }

    @Override public boolean shouldPause() { return false; }

    private static final class FieldButton extends ButtonWidget {
        private final int accent;
        FieldButton(int x, int y, int width, Text text, PressAction action, int accent) {
            super(x, y, width, 20, text, action, DEFAULT_NARRATION_SUPPLIER);
            this.accent = accent;
        }
        @Override protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean focused = isHovered() || isFocused();
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                    active && focused ? 0xff3c4b35 : 0xff263228);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), active && focused ? accent : 0xff46533e);
            context.fill(getX() + 2, getY() + 3, getX() + 4, getY() + getHeight() - 3, active ? accent : 0xff54604e);
            drawMessage(context, MinecraftClient.getInstance().textRenderer, active ? 0xffe1e5d1 : 0xff7e8876);
        }
    }
}
