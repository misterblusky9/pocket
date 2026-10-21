package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.AtelierMenu;
import com.misterblusky9.pocket.item.LiveCaseItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AtelierScreen extends AbstractContainerScreen<AtelierMenu> {
    private static final int WIDTH = 232;
    private static final int HEIGHT = 226;
    private static final int ROW_TOP = 18;
    private static final int ROW_PITCH = 16;
    private static final int LABEL_X = 100;
    private static final int VALUE_X = 132;
    private static final int BUTTON_X = 160;
    private static final int BUTTON_W = 16;
    private static final int BUTTON_H = 14;
    private static final int PREVIEW_LEFT = 28;
    private static final int PREVIEW_RIGHT = 96;
    private static final int PREVIEW_BOTTOM = 128;
    private static final int PREVIEW_SCALE = 25;
    private static final String[] STEP_LABELS = {"--", "-", "+", "++"};
    private static final int HEAD_ARMOR_SLOT = 3;

    public AtelierScreen(AtelierMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelX = AtelierMenu.INVENTORY_X;
        this.inventoryLabelY = AtelierMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        for (int field = 0; field < AtelierMenu.FIELDS.length; field++) {
            for (int step = 0; step < STEP_LABELS.length; step++) {
                int id = field * STEP_LABELS.length + step;
                addRenderableWidget(Button.builder(Component.literal(STEP_LABELS[step]), button -> click(id))
                        .bounds(this.leftPos + BUTTON_X + step * BUTTON_W, this.topPos + ROW_TOP + field * ROW_PITCH,
                                BUTTON_W, BUTTON_H)
                        .build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Reset"), button -> click(AtelierMenu.RESET))
                .bounds(this.leftPos + BUTTON_X, this.topPos + ROW_TOP + AtelierMenu.FIELDS.length * ROW_PITCH,
                        BUTTON_W * STEP_LABELS.length, BUTTON_H)
                .build());
    }

    private void click(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF555555);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFFC6C6C6);
        for (Slot slot : this.menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF373737);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF8B8B8B);
        }
        graphics.fill(x + PREVIEW_LEFT, y + ROW_TOP, x + PREVIEW_RIGHT, y + PREVIEW_BOTTOM, 0xFF000000);
        preview(graphics, mouseX, mouseY);
    }

    private void preview(GuiGraphics graphics, int mouseX, int mouseY) {
        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) return;
        ItemStack worked = this.menu.worked();
        ItemStack worn = player.getInventory().armor.get(HEAD_ARMOR_SLOT);
        if (!worked.isEmpty()) player.getInventory().armor.set(HEAD_ARMOR_SLOT, worked);
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    this.leftPos + PREVIEW_LEFT, this.topPos + ROW_TOP,
                    this.leftPos + PREVIEW_RIGHT, this.topPos + PREVIEW_BOTTOM,
                    PREVIEW_SCALE, 0.0625F, mouseX, mouseY, player);
        } finally {
            player.getInventory().armor.set(HEAD_ARMOR_SLOT, worn);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        ItemStack worked = this.menu.worked();
        float[] pose = LiveCaseItem.wornPose(worked);
        int colour = worked.isEmpty() ? 0xFF8B8B8B : 0xFF404040;
        for (int field = 0; field < AtelierMenu.FIELDS.length; field++) {
            int rowY = ROW_TOP + field * ROW_PITCH + 3;
            graphics.drawString(this.font, AtelierMenu.FIELDS[field], LABEL_X, rowY, colour, false);
            String unit = field < 3 ? "px" : "°";
            graphics.drawString(this.font, String.format("%.0f%s", pose[field], unit), VALUE_X, rowY, colour, false);
        }
    }
}
