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
    public static void loginReset(final ServerStartingEvent event) {
        MoonCompressionSessions.release(null);
    }

    @SubscribeEvent
    public static void logout(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            MoonCompressionSessions.release(player);
        }
    }

    @SubscribeEvent
    public static void stopping(final ServerStoppingEvent event) {
        MoonCompressionSessions.release(null);
    }

    private MoonCompressionLifecycle() {}
}
