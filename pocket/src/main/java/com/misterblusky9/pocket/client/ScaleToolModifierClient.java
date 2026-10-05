package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ScaleToolModifier;
import com.misterblusky9.pocket.network.ScaleToolModifierPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScaleToolModifierClient {
    private static boolean sent;

    public static void onClientTick(final ClientTickEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            sent = false;
            return;
        }

        final boolean targetOnly = minecraft.screen == null
                && Screen.hasControlDown()
                && ScaleToolModifier.holdsScaleTool(player);
        ScaleToolModifier.set(player, targetOnly);
        if (targetOnly == sent) return;

        sent = targetOnly;
        PacketDistributor.sendToServer(new ScaleToolModifierPayload(targetOnly));
    }

    private ScaleToolModifierClient() {}
}
