package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.scale.CompressionStage;
import com.misterblusky9.pocket.scale.ScaleState;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.UUID;

public final class CompressionHud {
    private static final double GUN_RANGE = 160.0D;
    private static final double RAY_RANGE = 192.0D;

    private static final int HOTBAR_CLEARANCE = 59;

    private static final int SCALE_COLOUR = 0xC6C6C6;
    private static final int STATUS_COLOUR = 0x8C8C8C;

    private static final float ELLIPSIS_TICKS = 6.0F;

    private CompressionHud() {}

    public static void render(final RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        if (!holdingTool(player)) return;

        final CompressionAim.Aim aim = CompressionAim.ofLocalPlayer(
                holding(player, CompressionGunItem.class).isEmpty() ? RAY_RANGE : GUN_RANGE);
        if (aim == null) return;

        final GuiGraphics graphics = event.getGuiGraphics();
        final Font font = minecraft.font;
        final int centreX = graphics.guiWidth() / 2;
        final UUID id = aim.subLevelId();
        final String status = statusOf(id);

        int y = graphics.guiHeight() - HOTBAR_CLEARANCE;
        if (status != null) {
            drawCentred(graphics, font, status, centreX, y + font.lineHeight + 1, STATUS_COLOUR);
        }

        drawCentred(graphics, font,
                CompressionStage.nearest(ScaleState.getClientScale(id)).label(),
                centreX, y, SCALE_COLOUR);
    }

    private static String statusOf(final UUID id) {
        if (!CompressionFieldRenderer.isGripped(id)) return null;

        if (!CompressionFieldRenderer.isSealed(id)) {
            return Math.round(CompressionFieldRenderer.progress(id) * 100.0F) + "%";
        }

        return (CompressionFieldRenderer.isGrowing(id) ? "Growing" : "Shrinking") + ellipsis();
    }

    private static String ellipsis() {
        final int step = (int) (AnimationTickHolder.getRenderTime() / ELLIPSIS_TICKS) % 4;
        return ".".repeat(step);
    }

    private static boolean holdingTool(final LocalPlayer player) {
        return !holding(player, CompressionGunItem.class).isEmpty()
                || !holding(player, CreativeShrinkRayItem.class).isEmpty();
    }

    private static ItemStack holding(final LocalPlayer player, final Class<?> type) {
        final ItemStack main = player.getMainHandItem();
        if (type.isInstance(main.getItem())) return main;

        final ItemStack off = player.getOffhandItem();
        if (type.isInstance(off.getItem())) return off;

        return ItemStack.EMPTY;
    }

    private static void drawCentred(
            final GuiGraphics graphics,
            final Font font,
            final String text,
            final int centreX,
            final int y,
            final int colour
    ) {
        graphics.drawString(font, text, centreX - font.width(text) / 2, y,
                0xFF000000 | (colour & 0x00FFFFFF), true);
    }
}
