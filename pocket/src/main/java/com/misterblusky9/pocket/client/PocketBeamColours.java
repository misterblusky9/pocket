package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.network.ShrinkRayBeamColourPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

public final class PocketBeamColours {
    public static final int INERT_COLOUR = ShrinkRayBeamColourPayload.INERT_COLOUR;

    private static final int MAX_PENDING = 8;
    private static final double MATCH_EPSILON_SQR = 1.0E-6D;
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();

    private PocketBeamColours() {
    }

    private static final Pending UNTAGGED = new Pending(INERT_COLOUR, Vec3.ZERO, -1);

    public static void push(final int colour, final Vec3 target, final int shooter) {
        if (target == null) return;
        if (PENDING.size() >= MAX_PENDING) PENDING.removeFirst();
        PENDING.addLast(new Pending(colour, target, shooter));
    }

    public static Pending take(final Vec3 end) {
        if (end == null) return UNTAGGED;

        final var iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            final Pending pending = iterator.next();
            if (pending.target.distanceToSqr(end) <= MATCH_EPSILON_SQR) {
                iterator.remove();
                return pending;
            }
        }
        return UNTAGGED;
    }

    public static void clear() {
        PENDING.clear();
    }

    public record Pending(int colour, Vec3 target, int shooter) {}
}
