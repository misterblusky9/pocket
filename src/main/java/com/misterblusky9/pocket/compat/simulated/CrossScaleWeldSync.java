package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.network.CrossScaleWeldListPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@EventBusSubscriber(modid = "pocket")
public final class CrossScaleWeldSync {
    @SubscribeEvent
    public static void playerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) sendTo(player);
    }

    @SubscribeEvent
    public static void playerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) sendTo(player);
    }

    public static void broadcast(final ServerLevel level) {
        if (level == null) return;
        final List<WeldRecord> welds = List.copyOf(WeldStore.get(level).all());
        final CrossScaleWeldListPayload payload = new CrossScaleWeldListPayload(welds);
        for (final ServerPlayer player : level.players()) {
            try {
                PacketDistributor.sendToPlayer(player, payload);
            } catch (final RuntimeException ignored) {
            }
        }
    }

    public static void sendTo(final ServerPlayer player) {
        if (player == null) return;
        try {
            PacketDistributor.sendToPlayer(
                    player,
                    new CrossScaleWeldListPayload(List.copyOf(WeldStore.get(player.serverLevel()).all())));
        } catch (final RuntimeException ignored) {
        }
    }

    private CrossScaleWeldSync() {}
}
