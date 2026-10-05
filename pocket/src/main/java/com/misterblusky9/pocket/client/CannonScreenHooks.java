package com.misterblusky9.pocket.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class CannonScreenHooks {
    public static void open(final ItemStack cannon, final InteractionHand hand) {
        Minecraft.getInstance().setScreen(new CannonExpansionScreen(cannon, hand));
    }

    private CannonScreenHooks() {}
}
