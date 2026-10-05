package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ScaleSelectingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.InputEvent;

public final class ScaleSelectionInput {
    public static void onInteraction(final InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !player.isShiftKeyDown()) return;

        final InteractionHand hand = event.getHand();
        final ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ScaleSelectingItem)) return;

        event.setCanceled(true);
        event.setSwingHand(false);
        minecraft.setScreen(new ScaleSelectionScreen(hand));
    }

    private ScaleSelectionInput() {}
}
