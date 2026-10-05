package com.misterblusky9.pocket.entity;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pocket.compression.PersonalLock;
import com.misterblusky9.pocket.compression.PersonalScale;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = "pocket")
public final class PersonalScaleLifecycle {
    @SubscribeEvent
    public static void loggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof final ServerPlayer player)) return;
        release(player);
        PersonalScale.restore(player);
        PersonalLock.sync(player);
    }

    @SubscribeEvent
    public static void cloned(final PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof final ServerPlayer from && event.getEntity() instanceof final ServerPlayer to) {
            PersonalLock.copy(from, to);
        }
    }

    @SubscribeEvent
    public static void respawned(final PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) PersonalLock.sync(player);
    }

    @SubscribeEvent
    public static void changedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) PersonalLock.sync(player);
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
        if (player == null) return;
        Pym.entities().setScale(player, 1.0D, 0);
    }

    private PersonalScaleLifecycle() {}
}
