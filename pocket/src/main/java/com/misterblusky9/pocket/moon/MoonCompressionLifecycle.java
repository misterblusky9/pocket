package com.misterblusky9.pocket.moon;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = "pocket")
public final class MoonCompressionLifecycle {
    @SubscribeEvent
    public static void loginReset(ServerStartingEvent event) {
        MoonCompressionSessions.reset();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MoonCompressionSessions.abandon(player);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        MoonCompressionSessions.reset();
    }

    private MoonCompressionLifecycle() {}
}
