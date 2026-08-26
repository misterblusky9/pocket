package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CreativeShrinkRayItem;
import com.misterblusky9.pocket.network.ShrinkRayStagePayload;
import com.misterblusky9.pocket.scale.CompressionStage;
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
        final CompressionStage current = CreativeShrinkRayItem.selectedStage(stack);

        final CompressionStage next = current.cycle(delta > 0.0D ? -1 : 1);
        if (next == current) return;

        CreativeShrinkRayItem.setSelectedStage(stack, next);
        PacketDistributor.sendToServer(new ShrinkRayStagePayload(hand, next));
        showLadder(player, next);
    }

    public static void showLadder(final LocalPlayer player, final CompressionStage selected) {
        final MutableComponent line = Component.empty();
        final CompressionStage[] stages = CompressionStage.values();

        for (int i = 0; i < stages.length; i++) {
            if (i > 0) line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));

            final CompressionStage stage = stages[i];
            if (stage == selected) {
                line.append(Component.literal(stage.label())
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            } else {
                line.append(Component.literal(stage.label()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        player.displayClientMessage(line, true);
    }

    private static InteractionHand handHoldingRay(final LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof CreativeShrinkRayItem) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().getItem() instanceof CreativeShrinkRayItem) return InteractionHand.OFF_HAND;
        return null;
    }

    private ShrinkRayControls() {}
}
