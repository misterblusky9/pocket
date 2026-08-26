package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.CompressionGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class CompressionGunScreenHooks {
    public static void open(final InteractionHand hand) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        final ItemStack gun = minecraft.player.getItemInHand(hand);
        if (!(gun.getItem() instanceof CompressionGunItem)) return;

        minecraft.setScreen(new CompressionGunTargetingScreen(gun, hand));
    }

    private CompressionGunScreenHooks() {}
}
