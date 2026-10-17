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
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        release(player);
        PersonalScale.restore(player);
        PersonalLock.sync(player);
    }

    @SubscribeEvent
    public static void cloned(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer from && event.getEntity() instanceof ServerPlayer to) {
            PersonalLock.copy(from, to);
            ScaledRespawn.cloneScale(from, to, event.isWasDeath());
        }
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!event.isEndConquered()) ScaledRespawn.finishRespawn(player);
            PersonalLock.sync(player);
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PersonalLock.sync(player);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        release(event.getEntity() instanceof ServerPlayer player ? player : null);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            release(player);
        }
    }

    private static void release(ServerPlayer player) {
        if (player == null) return;
        Pym.entities().setScale(player, 1.0D, 0);
    }

    private PersonalScaleLifecycle() {}
}
