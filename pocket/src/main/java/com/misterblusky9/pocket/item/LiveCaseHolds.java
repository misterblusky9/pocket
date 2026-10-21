package com.misterblusky9.pocket.item;

import com.misterblusky9.pocket.network.LiveCaseHeldPayload;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LiveCaseHolds {
    private static final int RESEND_TICKS = 40;

    private static final Set<UUID> MARKED = new HashSet<>();
    private static Set<UUID> sent = Set.of();
    private static int sinceSent;

    private static volatile Set<UUID> client = Set.of();

    public static void mark(final UUID id) {
        MARKED.add(id);
    }

    public static void onServerTick(final ServerTickEvent.Post event) {
        sinceSent++;
        if (!MARKED.equals(sent) || sinceSent >= RESEND_TICKS) {
            sent = Set.copyOf(MARKED);
            sinceSent = 0;
            PacketDistributor.sendToAllPlayers(new LiveCaseHeldPayload(List.copyOf(sent)));
        }
        MARKED.clear();
    }

    public static void accept(final List<UUID> held) {
        client = Set.copyOf(held);
    }

    public static boolean anyHeldOnClient() {
        return !client.isEmpty();
    }

    public static boolean heldOnClient(final UUID id) {
        return client.contains(id);
    }

    private LiveCaseHolds() {}
}
