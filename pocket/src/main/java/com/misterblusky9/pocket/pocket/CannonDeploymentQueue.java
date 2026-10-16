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
            ServerLevel level,
            ItemStack payload,
            Vec3 position,
            Vec3 motion,
            UUID owner
    ) {
        enqueue(level, payload, position, motion, owner, CannonExpansionMode.IMMEDIATE);
    }

    public static void enqueue(
            ServerLevel level,
            ItemStack payload,
            Vec3 position,
            Vec3 motion,
            UUID owner,
            CannonExpansionMode mode
    ) {
        PENDING.addLast(new Request(
                level.dimension(), payload.copy(), position, motion, owner,
                level.getGameTime() + 1L, mode
        ));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;

        MinecraftServer server = event.getServer();
        int queued = PENDING.size();
        for (int i = 0; i < queued; i++) {
            Request request = PENDING.removeFirst();
            ServerLevel level = server.getLevel(request.dimension());
            if (level == null) continue;

            if (level.getGameTime() < request.notBeforeTick()) {
                PENDING.addLast(request);
                continue;
            }

            if (PocketCaseItem.deployFromCannon(
                    level, request.payload(), request.position(), request.motion(), request.mode()
            ) == null) {
                refund(server, level, request);
            }
            return;
        }
    }

    private static void refund(MinecraftServer server, ServerLevel level, Request request) {
        if (request.owner() == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(request.owner());
        if (player == null) return;
        ItemStack refund = PocketCaseItem.recoveryFor(level, request.payload());
        if (refund.isEmpty()) return;
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
