package com.misterblusky9.pocket.moon;

import com.misterblusky9.pocket.scale.CompressionStage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MoonLightLag {
    public static final int ROUND_TRIP_TICKS = 51;

    private static final Deque<Beam> IN_FLIGHT = new ArrayDeque<>();
    private static final Map<UUID, Emission> LAST = new HashMap<>();

    static void hold(
            final ServerPlayer player,
            final CompressionStage floor,
            final InteractionHand hand,
            final MoonTargeting.Hit hit,
            final boolean growing
    ) {
        if (player == null || floor == null || hit == null) return;
        emit(player, new Emission(floor, hand, hit, growing, player.level().getGameTime()));
    }

    static boolean renew(final ServerPlayer player, final CompressionStage floor) {
        if (player == null) return false;
        final Emission last = LAST.get(player.getUUID());
        if (last == null || last.floor() != floor) return false;

        final long now = player.level().getGameTime();
        if (now - last.tick() > 1L) return false;

        emit(player, new Emission(last.floor(), last.hand(), last.hit(), last.growing(), now));
        return true;
    }

    static void instant(
            final ServerPlayer player,
            final CompressionStage stage,
            final MoonTargeting.Hit hit
    ) {
        if (player == null || stage == null || hit == null) return;
        IN_FLIGHT.addLast(new Beam(
                Kind.INSTANT,
                player.getUUID(),
                stage,
                player.getUsedItemHand(),
                hit,
                false,
                player.level().getGameTime() + ROUND_TRIP_TICKS
        ));
    }

    static void release(final ServerPlayer player) {
        if (player == null) return;
        final UUID holder = player.getUUID();
        final boolean painting = LAST.remove(holder) != null;
        if (!painting && !inFlight(holder)) return;

        IN_FLIGHT.addLast(new Beam(
                Kind.RELEASE,
                holder,
                null,
                null,
                null,
                false,
                player.level().getGameTime() + ROUND_TRIP_TICKS
        ));
    }

    static void tick(final MinecraftServer server) {
        if (IN_FLIGHT.isEmpty()) return;
        final long now = server.overworld().getGameTime();

        while (!IN_FLIGHT.isEmpty() && IN_FLIGHT.peekFirst().arriveTick() <= now) {
            land(server, IN_FLIGHT.removeFirst());
        }
    }

    static void clear(final ServerPlayer player) {
        if (player == null) {
            IN_FLIGHT.clear();
            LAST.clear();
            return;
        }

        final UUID holder = player.getUUID();
        LAST.remove(holder);
        final Iterator<Beam> beams = IN_FLIGHT.iterator();
        while (beams.hasNext()) {
            if (beams.next().holder().equals(holder)) beams.remove();
        }
    }

    private static void emit(final ServerPlayer player, final Emission emission) {
        LAST.put(player.getUUID(), emission);
        IN_FLIGHT.addLast(new Beam(
                Kind.HOLD,
                player.getUUID(),
                emission.floor(),
                emission.hand(),
                emission.hit(),
                emission.growing(),
                emission.tick() + ROUND_TRIP_TICKS
        ));
    }

    private static boolean inFlight(final UUID holder) {
        for (final Beam beam : IN_FLIGHT) {
            if (beam.holder().equals(holder)) return true;
        }
        return false;
    }

    private static void land(final MinecraftServer server, final Beam beam) {
        final ServerPlayer holder = server.getPlayerList().getPlayer(beam.holder());
        if (holder == null) return;

        switch (beam.kind()) {
            case HOLD -> MoonCompressionSessions.deliverHold(
                    holder, beam.stage(), beam.hand(), beam.hit(), beam.growing());
            case INSTANT -> MoonCompressionSessions.deliverInstant(holder, beam.stage(), beam.hit());
            case RELEASE -> MoonCompressionSessions.deliverRelease(holder);
        }
    }

    private enum Kind { HOLD, INSTANT, RELEASE }

    private record Beam(
            Kind kind,
            UUID holder,
            CompressionStage stage,
            InteractionHand hand,
            MoonTargeting.Hit hit,
            boolean growing,
            long arriveTick
    ) {}

    private record Emission(
            CompressionStage floor,
            InteractionHand hand,
            MoonTargeting.Hit hit,
            boolean growing,
            long tick
    ) {}

    private MoonLightLag() {}
}
