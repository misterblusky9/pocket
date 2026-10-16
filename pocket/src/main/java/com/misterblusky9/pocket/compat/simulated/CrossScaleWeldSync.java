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
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    @SubscribeEvent
    public static void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendTo(player);
    }

    public static void broadcast(ServerLevel level) {
        if (level == null) return;
        List<WeldRecord> welds = List.copyOf(WeldStore.get(level).all());
        CrossScaleWeldListPayload payload = new CrossScaleWeldListPayload(welds);
        for (ServerPlayer player : level.players()) {
            try {
                PacketDistributor.sendToPlayer(player, payload);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void sendTo(ServerPlayer player) {
        if (player == null) return;
        try {
            PacketDistributor.sendToPlayer(
                    player,
                    new CrossScaleWeldListPayload(List.copyOf(WeldStore.get(player.serverLevel()).all())));
        } catch (RuntimeException ignored) {
        }
    }

    private CrossScaleWeldSync() {}
}
