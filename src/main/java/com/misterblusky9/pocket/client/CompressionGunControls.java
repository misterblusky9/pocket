package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CompressionGunItem;
import com.misterblusky9.pocket.network.CompressionGunSettingsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CompressionGunControls {
    public static void onScroll(final InputEvent.MouseScrollingEvent event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) return;
        if (!player.isShiftKeyDown()) return;

        final InteractionHand hand = handHoldingGun(player);
        if (hand == null) return;

        final double delta = event.getScrollDeltaY();
        if (delta == 0.0D) return;

        event.setCanceled(true);

        final ItemStack stack = player.getItemInHand(hand);
        final boolean growing = delta > 0.0D;
        CompressionGunItem.setGrowing(stack, growing);

        PacketDistributor.sendToServer(new CompressionGunSettingsPayload(
                hand,
                CompressionGunItem.targetingMode(stack),
                growing
        ));

        showModes(player, growing);
    }

    private static InteractionHand handHoldingGun(final LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof CompressionGunItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof CompressionGunItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }


    public static void showModes(final LocalPlayer player, final boolean growing) {
        final var line = Component.empty();
        if (growing) {
            line.append(Component.literal("Shrink").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal("Grow").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            line.append(Component.literal("Shrink").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.literal("Grow").withStyle(ChatFormatting.DARK_GRAY));
        }
        player.displayClientMessage(line, true);
    }

    private CompressionGunControls() {}
}
