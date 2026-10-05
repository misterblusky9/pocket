package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.moon.MoonScaleNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "pocket", value = Dist.CLIENT)
public final class MoonScaleHandshake {
    private static int retryTicker;

    @SubscribeEvent
    public static void tick(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null) {
            retryTicker = 0;
            return;
        }

        if (MoonScaleClient.hasSnapshot()) return;

        if (retryTicker > 0) {
            retryTicker--;
            return;
        }

        retryTicker = 20;
        PacketDistributor.sendToServer(MoonScaleNetwork.MoonScaleRequestPayload.INSTANCE);
    }

    @SubscribeEvent
    public static void logout(final ClientPlayerNetworkEvent.LoggingOut event) {
        retryTicker = 0;
        MoonScaleClient.clear();
    }

    private MoonScaleHandshake() {}
}
