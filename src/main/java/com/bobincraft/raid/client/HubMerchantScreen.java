package com.bobincraft.raid.client;

import com.bobincraft.raid.HubNpcInteraction;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;
import org.lwjgl.glfw.GLFW;

/** Camp requisitions: custom presentation over Minecraft's real, server-validated trade slots. */
public final class HubMerchantScreen extends HandledScreen<MerchantScreenHandler> {
    private final HubNpcInteraction.Role role;
    private int selected;
    private int firstVisible;
    private boolean sidebar;
    private boolean initialSelectionSent;
    private static final int VISIBLE_OFFERS = 7;

    public HubMerchantScreen(MerchantScreenHandler handler, PlayerInventory inventory, Text title,
                             HubNpcInteraction.Role role) {
        super(handler, inventory, title);
        this.role = role;
        backgroundHeight = 202;
        playerInventoryTitleX = 108;
        playerInventoryTitleY = 73;
    }

    @Override protected void init() {
        sidebar = width >= 432;
        backgroundWidth = sidebar ? 412 : 276;
        super.init();
    }

    public static int accent(HubNpcInteraction.Role role) {
        return switch (role) {
            case QUARTERMASTER -> 0xffd1ac64;
            case COOK -> 0xffc79961;
            case MEDIC -> 0xffd7877d;
            case COMMANDER -> 0xff7eafc0;
            case SENTRY -> 0xffaaba7e;
        };
    }

    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if (!initialSelectionSent && !handler.getRecipes().isEmpty()) {
            select(0);
            initialSelectionSent = true;
        }
        selected = MathHelper.clamp(selected, 0, Math.max(0, handler.getRecipes().size() - 1));
        firstVisible = MathHelper.clamp(firstVisible, 0, Math.max(0, handler.getRecipes().size() - VISIBLE_OFFERS));
    }

    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int color = accent(role);
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xff111915);
        context.drawBorder(x, y, backgroundWidth, backgroundHeight, 0xff65705d);
        context.fill(x, y, x + backgroundWidth, y + 3, color);
        context.fill(x + 101, y + 17, x + 273, y + 69, 0xff202a22);
        context.drawBorder(x + 101, y + 17, 172, 52, 0xff394737);
        context.fill(x + 101, y + 81, x + 273, y + 162, 0xff1b251e);
        context.drawBorder(x + 101, y + 81, 172, 81, 0xff394737);
        for (Slot slot : handler.slots) {
            int slotX = x + slot.x, slotY = y + slot.y;
            context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xff5c6654);
            context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xff0b120e);
            context.drawHorizontalLine(slotX, slotX + 15, slotY + 16, 0xff7d856c);
        }
        // A clear output frame and transfer arrow mark the actual native trade output slot.
        context.drawBorder(x + 218, y + 35, 20, 20, color);
        context.fill(x + 190, y + 43, x + 207, y + 46, color);
        context.fill(x + 202, y + 40, x + 205, y + 49, color);
        drawOffers(context, mouseX, mouseY);
        if (sidebar) drawDossier(context);
        context.fill(x + 7, y + 168, x + 269, y + 169, 0xff394737);
        context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.bobin_craft.npc.payment").getString(), 102), x + 107, y + 22, color, false);
        context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.bobin_craft.npc.take").getString(), 53), x + 215, y + 22, color, false);
        var hints = textRenderer.wrapLines(Text.translatable("gui.bobin_craft.npc.trade_hint"), 256);
        for (int line = 0; line < Math.min(2, hints.size()); line++) {
            context.drawText(textRenderer, hints.get(line), x + 10, y + 172 + line * 10, 0xffb8c1aa, false);
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.bobin_craft.npc.trade_controls").getString(), 256),
                x + 10, y + 192, 0xff7e8c77, false);
    }

    private void drawOffers(DrawContext context, int mouseX, int mouseY) {
        var offers = handler.getRecipes();
        for (int row = 0; row < VISIBLE_OFFERS; row++) {
            int index = firstVisible + row;
            int top = y + 18 + row * 20;
            context.fill(x + 5, top, x + 94, top + 19, index == selected ? 0xff3c4b35 : 0xff202b22);
            if (index >= offers.size()) continue;
            TradeOffer offer = offers.get(index);
            if (index == selected) context.fill(x + 5, top, x + 7, top + 19, accent(role));
            drawOfferItem(context, offer.getAdjustedFirstBuyItem(), x + 9, top + 1);
            if (!offer.getSecondBuyItem().isEmpty()) drawOfferItem(context, offer.getSecondBuyItem(), x + 32, top + 1);
            context.drawText(textRenderer, ">", x + 57, top + 5, offer.isDisabled() ? 0xffb66d64 : 0xffaebc9f, false);
            drawOfferItem(context, offer.getSellItem(), x + 74, top + 1);
            if (offer.isDisabled()) {
                context.fill(x + 8, top + 9, x + 92, top + 11, 0xbba54e48);
            } else if (mouseX >= x + 5 && mouseX < x + 94 && mouseY >= top && mouseY < top + 19) {
                context.drawBorder(x + 5, top, 89, 19, accent(role));
            }
        }
        context.fill(x + 96, y + 18, x + 99, y + 158, 0xff2d382e);
        if (offers.size() > VISIBLE_OFFERS) {
            int thumbHeight = Math.max(12, VISIBLE_OFFERS * 140 / offers.size());
            int thumbY = y + 18 + firstVisible * (140 - thumbHeight) / (offers.size() - VISIBLE_OFFERS);
            context.fill(x + 96, thumbY, x + 99, thumbY + thumbHeight, accent(role));
        }
    }

    private void drawOfferItem(DrawContext context, ItemStack item, int left, int top) {
        context.drawItem(item, left, top);
        context.drawItemInSlot(textRenderer, item, left, top);
    }

    private void drawDossier(DrawContext context) {
        int left = x + 283;
        context.fill(left - 3, y + 11, x + backgroundWidth - 9, y + backgroundHeight - 10, 0xff1c2820);
        context.drawBorder(left - 3, y + 11, backgroundWidth - 289, backgroundHeight - 21, 0xff394737);
        drawRoleBadge(context, left + 43, y + 22, role);
        context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.bobin_craft.npc.department." + role.key()).getString(), 105),
                left + 5, y + 57, accent(role), false);
        Text briefing = Text.translatable("gui.bobin_craft.npc.briefing." + role.key());
        var lines = textRenderer.wrapLines(briefing, 105);
        for (int line = 0; line < Math.min(9, lines.size()); line++) {
            context.drawText(textRenderer, lines.get(line), left + 5, y + 74 + line * 11, 0xffbdc7b0, false);
        }
        context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.bobin_craft.npc.authorized").getString(), 105), left + 5, y + 177, 0xff7e8c77, false);
    }

    static void drawRoleBadge(DrawContext context, int left, int top, HubNpcInteraction.Role role) {
        int color = accent(role);
        context.drawBorder(left - 4, top - 3, 29, 29, color);
        switch (role) {
            case MEDIC -> {
                context.fill(left + 6, top + 2, left + 14, top + 22, color);
                context.fill(left, top + 8, left + 20, top + 16, color);
            }
            case COOK -> {
                context.fill(left, top + 9, left + 20, top + 20, color);
                context.fill(left + 3, top + 7, left + 17, top + 9, color);
                for (int i = 0; i < 3; i++) context.fill(left + 3 + i * 6, top, left + 5 + i * 6, top + 5, color);
            }
            case COMMANDER -> {
                context.drawBorder(left + 2, top + 2, 16, 18, color);
                context.fill(left + 5, top + 6, left + 15, top + 8, color);
                context.fill(left + 5, top + 11, left + 15, top + 13, color);
                context.fill(left + 5, top + 16, left + 11, top + 18, color);
            }
            default -> {
                context.fill(left, top + 5, left + 20, top + 10, color);
                context.fill(left + 12, top + 10, left + 17, top + 18, color);
                context.fill(left + 3, top + 10, left + 6, top + 14, color);
                context.fill(left + 17, top + 3, left + 20, top + 5, color);
            }
        }
    }

    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("gui.bobin_craft.npc.stock").getString(), 85), 9, 6, accent(role), false);
        context.drawText(textRenderer, textRenderer.trimToWidth(title.getString(), 165), 107, 6, 0xffe2e6d5, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xffb6c2a6, false);
        if (selected < handler.getRecipes().size()) {
            TradeOffer offer = handler.getRecipes().get(selected);
            Text status = offer.isDisabled() ? Text.translatable("gui.bobin_craft.npc.exhausted")
                    : Text.translatable("gui.bobin_craft.npc.stock_remaining", offer.getMaxUses() - offer.getUses());
            context.drawText(textRenderer, textRenderer.trimToWidth(status.getString(), 164), 107, 59,
                    offer.isDisabled() ? 0xffd98e81 : 0xff9dac8e, false);
        }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        int row = (mouseY - y - 18) / 20;
        int index = firstVisible + row;
        if (mouseX >= x + 5 && mouseX < x + 94 && mouseY >= y + 18 && mouseY < y + 158
                && index >= 0 && index < handler.getRecipes().size()) {
            TradeOffer offer = handler.getRecipes().get(index);
            ItemStack hovered = mouseX >= x + 70 ? offer.getSellItem()
                    : mouseX >= x + 30 && mouseX < x + 53 ? offer.getSecondBuyItem() : offer.getAdjustedFirstBuyItem();
            if (!hovered.isEmpty()) context.drawItemTooltip(textRenderer, hovered, mouseX, mouseY);
        }
    }

    private void select(int index) {
        if (index < 0 || index >= handler.getRecipes().size() || client == null || client.getNetworkHandler() == null) return;
        selected = index;
        handler.setRecipeIndex(index);
        handler.switchTo(index);
        client.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= x + 5 && mouseX < x + 94 && mouseY >= y + 18 && mouseY < y + 158) {
            select(firstVisible + (int) (mouseY - y - 18) / 20);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        firstVisible = MathHelper.clamp(firstVisible - (int) Math.signum(amount), 0,
                Math.max(0, handler.getRecipes().size() - VISIBLE_OFFERS));
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            select(MathHelper.clamp(selected + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1),
                    0, Math.max(0, handler.getRecipes().size() - 1)));
            if (selected < firstVisible) firstVisible = selected;
            if (selected >= firstVisible + VISIBLE_OFFERS) firstVisible = selected - VISIBLE_OFFERS + 1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
