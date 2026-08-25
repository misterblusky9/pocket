package com.misterblusky9.pocket.pocket;

import com.misterblusky9.pocket.item.PocketCaseItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public final class CannonDeploymentQueue {
    private static final Deque<Request> PENDING = new ArrayDeque<>();

    public static void enqueue(
            final ServerLevel level,
            final ItemStack payload,
            final Vec3 position,
            final Vec3 motion,
            final UUID owner
    ) {
        enqueue(level, payload, position, motion, owner, CannonExpansionMode.IMMEDIATE);
    }

    public static void enqueue(
            final ServerLevel level,
            final ItemStack payload,
            final Vec3 position,
            final Vec3 motion,
            final UUID owner,
            final CannonExpansionMode mode
    ) {
        PENDING.addLast(new Request(
                level.dimension(), payload.copy(), position, motion, owner,
                level.getGameTime() + 1L, mode
        ));
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;

        final MinecraftServer server = event.getServer();
        final int queued = PENDING.size();
        for (int i = 0; i < queued; i++) {
            final Request request = PENDING.removeFirst();
            final ServerLevel level = server.getLevel(request.dimension());
            if (level == null) continue;

            if (level.getGameTime() < request.notBeforeTick()) {
                PENDING.addLast(request);
                continue;
            }

            if (PocketCaseItem.deployFromCannon(
                    level, request.payload(), request.position(), request.motion(), request.mode()
            ) == null) {
                refund(server, request);
            }
            return;
        }
    }

    private static void refund(final MinecraftServer server, final Request request) {
        if (request.owner() == null) return;
        final ServerPlayer player = server.getPlayerList().getPlayer(request.owner());
        if (player == null) return;
        final ItemStack refund = request.payload().copy();
        if (!player.getInventory().add(refund)) player.drop(refund, false);
        player.getInventory().setChanged();
    }

    private record Request(
            ResourceKey<Level> dimension,
            ItemStack payload,
            Vec3 position,
            Vec3 motion,
            UUID owner,
            long notBeforeTick,
            CannonExpansionMode mode
    ) {}

    private CannonDeploymentQueue() {}
}
