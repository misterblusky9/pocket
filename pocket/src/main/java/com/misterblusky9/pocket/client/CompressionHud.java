package com.misterblusky9.pocket.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.ScaleReadout;
import com.misterblusky9.pocket.pocket.PocketedSubLevelEvents;
import com.misterblusky9.pocket.compression.EntityCompressionTargeting;
import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.item.EmptyBoxItem;
import com.misterblusky9.pocket.item.HotGlueGunItem;
import com.misterblusky9.pocket.item.PocketCaseItem;
import com.misterblusky9.pocket.item.PocketKnifeItem;
import com.misterblusky9.pocket.item.SelfResizeDeviceItem;
import com.misterblusky9.pocket.moon.MoonTargeting;
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

    private static final int TILDE_DROP = 3;

    private static final float ELLIPSIS_TICKS = 6.0F;

    private CompressionHud() {}

    public static void render(final RenderGuiEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        final double range = rangeFor(player);
        final boolean selfResize = !holding(player, SelfResizeDeviceItem.class).isEmpty();
        if (Double.isNaN(range) && !selfResize) return;

        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);

        final GuiGraphics graphics = event.getGuiGraphics();
        final Font font = minecraft.font;
        final int centreX = graphics.guiWidth() / 2;
        final int y = graphics.guiHeight() - HOTBAR_CLEARANCE;

        if (Double.isNaN(range)) {
            drawCentred(graphics, font, HudScaleText.of(ScaleReadout.value(player), Pym.entities().scaleOf(player)), centreX, y, SCALE_COLOUR);
            return;
        }

        final EntityCompressionTargeting.Target entityAim = EntityCompressionTargeting.find(
                player, range, !holding(player, CreativeShrinkRayItem.class).isEmpty());
        if (entityAim != null) {
            drawCentred(
                    graphics, font, HudScaleText.of(
                            ScaleReadout.value(entityAim.entity()) * EntityCompressionTargeting.containerScale(entityAim.entity()),
                            EntityCompressionTargeting.scale(entityAim.entity())),
                    centreX, y, SCALE_COLOUR);
            return;
        }

        if (canScaleMoon(player) && MoonTargeting.isLookingAtMoon(player, MoonScaleClient.get(), partialTick, range)) {
            final String moonStatus = moonStatus();
            if (moonStatus != null) {
                drawCentred(
                        graphics, font, moonStatus, centreX,
                        y + font.lineHeight + 1, STATUS_COLOUR);
            }
            drawCentred(
                    graphics,
                    font,
                    HudScaleText.of(MoonScaleClient.readoutValue(), MoonScaleClient.get()),
                    centreX,
                    y,
                    SCALE_COLOUR
            );
            return;
        }

        final CompressionAim.Aim aim = CompressionAim.ofLocalPlayer(range);
        if (aim == null) return;

        final UUID id = aim.subLevelId();
        final String status = statusOf(id);

        if (status != null) {
            drawCentred(graphics, font, status, centreX, y + font.lineHeight + 1, STATUS_COLOUR);
        }

        final String readout = holdingCase(player) && tooBigToPocket(player, aim)
                ? "Too big"
                : HudScaleText.of(ScaleReadout.value(aim.subLevel()), Pym.scale().of(aim.subLevel()));
        drawCentred(graphics, font, readout, centreX, y, SCALE_COLOUR);
    }

    private static String moonStatus() {
        if (!MoonCompressionFieldRenderer.isGripped()) return null;
        if (!MoonCompressionFieldRenderer.isSealed()) {
            return Math.round(MoonCompressionFieldRenderer.progress() * 100.0F) + "%";
        }
        return (MoonCompressionFieldRenderer.isGrowing() ? "Growing" : "Shrinking") + ellipsis();
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

    private static double rangeFor(final LocalPlayer player) {
        if (!holding(player, CreativeShrinkRayItem.class).isEmpty()) return RAY_RANGE;
        if (!holding(player, CompressionGunItem.class).isEmpty()) return GUN_RANGE;
        if (holdingCase(player)
                || !holding(player, HotGlueGunItem.class).isEmpty()
                || !holding(player, PocketKnifeItem.class).isEmpty()) {
            return player.blockInteractionRange();
        }
        return Double.NaN;
    }

    private static boolean tooBigToPocket(final LocalPlayer player, final CompressionAim.Aim aim) {
        return !PocketedSubLevelEvents.fitsPocket(player, Pym.scale().settled(aim.subLevel()));
    }

    private static boolean canScaleMoon(final LocalPlayer player) {
        return !holding(player, CompressionGunItem.class).isEmpty()
                || !holding(player, CreativeShrinkRayItem.class).isEmpty();
    }

    private static boolean holdingCase(final LocalPlayer player) {
        return !holding(player, EmptyBoxItem.class).isEmpty()
                || !holding(player, PocketCaseItem.class).isEmpty();
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
        final int argb = 0xFF000000 | (colour & 0x00FFFFFF);
        final int x = centreX - font.width(text) / 2;
        if (!text.startsWith("~")) {
            graphics.drawString(font, text, x, y, argb, true);
            return;
        }

        graphics.drawString(font, "~", x, y + TILDE_DROP, argb, true);
        graphics.drawString(font, text.substring(1), x + font.width("~"), y, argb, true);
    }
}
