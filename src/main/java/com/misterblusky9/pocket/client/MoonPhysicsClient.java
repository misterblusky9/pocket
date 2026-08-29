package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.moon.MoonPhysicsState;
import com.misterblusky9.pocket.moon.MoonScaleNetwork;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaterniond;

public final class MoonPhysicsClient {
    private static volatile Snapshot previous;
    private static volatile Snapshot current;

    public static void handle(
            final MoonScaleNetwork.MoonPhysicsPayload payload,
            final IPayloadContext context
    ) {
        if (!payload.active()) {
            MoonPhysicsState.clearClient();
            MoonClientSubLevels.clear();
            clear();
            return;
        }
        final Snapshot next = new Snapshot(
                payload.x(), payload.y(), payload.z(), payload.halfExtent(),
                payload.qx(), payload.qy(), payload.qz(), payload.qw()
        );
        final Snapshot old = current;
        previous = old == null ? next : old;
        current = next;
        MoonPhysicsState.updateClient(
                payload.x(), payload.y(), payload.z(), payload.halfExtent(),
                payload.qx(), payload.qy(), payload.qz(), payload.qw()
        );
        MoonClientSubLevels.sync(payload.plotX(), payload.plotZ());
    }

    public static boolean isActive() {
        return current != null;
    }

    public static RenderState renderState(final float partialTick) {
        final Snapshot to = current;
        if (to == null) return null;
        final Snapshot from = previous == null ? to : previous;
        final double t = Mth.clamp(partialTick, 0.0F, 1.0F);
        final Quaterniond orientation = new Quaterniond(from.qx, from.qy, from.qz, from.qw)
                .normalize()
                .slerp(new Quaterniond(to.qx, to.qy, to.qz, to.qw).normalize(), t);
        return new RenderState(
                new Vec3(
                        Mth.lerp(t, from.x, to.x),
                        Mth.lerp(t, from.y, to.y),
                        Mth.lerp(t, from.z, to.z)
                ),
                Mth.lerp(t, from.halfExtent, to.halfExtent),
                orientation
        );
    }

    public static void clear() {
        MoonPhysicsState.clearClient();
        previous = null;
        current = null;
    }

    public record RenderState(Vec3 position, double halfExtent, Quaterniond orientation) {}

    private record Snapshot(
            double x,
            double y,
            double z,
            double halfExtent,
            double qx,
            double qy,
            double qz,
            double qw
    ) {}

    private MoonPhysicsClient() {}
}
