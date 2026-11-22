package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.network.CaseClonePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CaseCloning {
    public static void request(ItemStack stack) {
        if (Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen)
            PacketDistributor.sendToServer(new CaseClonePayload(stack.copyWithCount(1), false));
    }

    public static void pick(ItemStack stack) {
        PacketDistributor.sendToServer(new CaseClonePayload(stack.copyWithCount(1), true));
    }

    public static void accept(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.screen instanceof CreativeModeInventoryScreen screen)
                || !screen.getMenu().getCarried().isEmpty()) {
            java.util.UUID token = CaseClonePayload.token(stack);
            if (token != null) PacketDistributor.sendToServer(new com.misterblusky9.pocket.network.CaseCloneDiscardPayload(token));
            return;
        }
        minecraft.player.inventoryMenu.setCarried(stack);
        screen.getMenu().setCarried(stack);
    }

    private CaseCloning() {}
}
