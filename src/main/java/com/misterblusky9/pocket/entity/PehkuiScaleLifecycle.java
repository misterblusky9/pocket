package com.misterblusky9.pocket.entity;

import com.misterblusky9.pocket.compression.SelfCompressionSessions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = "pocket")
public final class PehkuiScaleLifecycle {
    @SubscribeEvent
    public static void loggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof final ServerPlayer player)) return;
        release(player);
        SelfCompressionSessions.restore(player);
    }

    @SubscribeEvent
    public static void loggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        release(event.getEntity() instanceof final ServerPlayer player ? player : null);
    }

    @SubscribeEvent
    public static void stopping(final ServerStoppingEvent event) {
        for (final ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            release(player);
        }
    }

    private static void release(final ServerPlayer player) {
        if (player == null || !PehkuiScaleBridge.ownsScaling()) return;
        EntityScaleTracker.forget(player);
        PehkuiScaleBridge.clearPersonalScale(player);
        PehkuiScaleBridge.clear(player);
    }

    private PehkuiScaleLifecycle() {}
}
