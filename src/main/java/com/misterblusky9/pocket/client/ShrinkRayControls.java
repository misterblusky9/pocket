package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.item.SelfResizeDeviceItem;
import com.misterblusky9.pocket.network.ShrinkRayScalePayload;
import com.misterblusky9.pocket.scale.ScaleFormat;
import com.misterblusky9.pocket.scale.ScaleLadder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ShrinkRayControls {
    private static double ghost = Double.NaN;

    public static void onPicked(final double scale) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        final double[] base = CreativeShrinkRayItem.ladder(player);
        ghost = ScaleLadder.onRung(base, scale) ? Double.NaN : scale;
        showLadder(player, ScaleLadder.withGhost(base, ghost), scale);
    }

    private static double[] activeLadder(final LocalPlayer player, final double current) {
        final double[] base = CreativeShrinkRayItem.ladder(player);
        if (Double.isNaN(ghost) || Math.abs(ghost - current) > PocketSized.EPSILON) {
            ghost = Double.NaN;
            return base;
        }
        return ScaleLadder.withGhost(base, ghost);
    }

    public static void onScroll(final InputEvent.MouseScrollingEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) return;
        if (!player.isShiftKeyDown()) return;

        final InteractionHand hand = handHoldingRay(player);
        if (hand == null) return;

        final double delta = event.getScrollDeltaY();
        if (delta == 0.0D) return;

        event.setCanceled(true);

        final ItemStack stack = player.getItemInHand(hand);
        final double current = CreativeShrinkRayItem.selectedScale(stack, player);
        final double[] ladder = activeLadder(player, current);
        final double next = ScaleLadder.cycle(ladder, current, delta > 0.0D ? -1 : 1);
        if (next == current) return;

        ghost = Double.NaN;
        CreativeShrinkRayItem.setSelectedScale(stack, next);
        PacketDistributor.sendToServer(new ShrinkRayScalePayload(hand, next));
        showLadder(player, CreativeShrinkRayItem.ladder(player), next);
    }

    public static void showLadder(final LocalPlayer player, final double[] ladder, final double selected) {
        final MutableComponent line = Component.empty();
        final int selectedIndex = ScaleLadder.nearestIndex(ladder, selected);

        for (int i = 0; i < ladder.length; i++) {
            if (i > 0) {
                line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
            }

            final String label = ScaleFormat.label(ladder[i]);
            if (i == selectedIndex) {
                line.append(Component.literal(label).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            } else {
                line.append(Component.literal(label).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        player.displayClientMessage(line, true);
    }

    private static InteractionHand handHoldingRay(final LocalPlayer player) {
        if (selectsStage(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (selectsStage(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }

    public static boolean selectsStage(final ItemStack stack) {
        return stack.getItem() instanceof CreativeShrinkRayItem
                || stack.getItem() instanceof SelfResizeDeviceItem;
    }

    private ShrinkRayControls() {}
}
