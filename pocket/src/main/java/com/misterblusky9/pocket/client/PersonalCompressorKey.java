package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.PersonalCompressors;
import com.misterblusky9.pocket.network.PersonalCompressorUsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PersonalCompressorKey {
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        while (PocketKeys.PERSONAL_COMPRESSOR.consumeClick()) {
            if (player == null || minecraft.screen != null || PersonalCompressors.find(player) == null) continue;
            if (player.isShiftKeyDown()) {
                minecraft.setScreen(new ScaleSelectionScreen(null));
            } else {
                PacketDistributor.sendToServer(PersonalCompressorUsePayload.INSTANCE);
            }
        }
    }

    private PersonalCompressorKey() {}
}
