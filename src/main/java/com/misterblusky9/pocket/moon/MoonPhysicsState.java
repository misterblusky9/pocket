package com.misterblusky9.pocket.moon;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class MoonPhysicsState {
    private static final double EPSILON = 1.0E-7D;
    private static final Map<Level, State> SERVER_STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile State clientState;

    public static void updateServer(
            final Level level,
            final double x,
            final double y,
            final double z,
            final double halfExtent,
            final double qx,
            final double qy,
            final double qz,
            final double qw
    ) {
        if (level == null || !Double.isFinite(halfExtent) || halfExtent <= 0.0D) {
            if (level != null) SERVER_STATES.remove(level);
            return;
        }
        SERVER_STATES.put(level, state(x, y, z, halfExtent, qx, qy, qz, qw));
    }

    public static void clearServer(final Level level) {
        if (level != null) SERVER_STATES.remove(level);
    }

    public static void updateClient(
            final double x,
            final double y,
            final double z,
            final double halfExtent,
            final double qx,
            final double qy,
            final double qz,
            final double qw
    ) {
        if (!Double.isFinite(halfExtent) || halfExtent <= 0.0D) {
            clientState = null;
            return;
        }
        clientState = state(x, y, z, halfExtent, qx, qy, qz, qw);
    }

    public static void clearClient() {
        clientState = null;
    }

    public static State get(final Level level) {
        if (level == null) return null;
        return level.isClientSide() ? clientState : SERVER_STATES.get(level);
    }

    public static RayHit raycast(final Level level, final Vec3 from, final Vec3 to) {
        return raycast(get(level), from, to);
    }

    public static RayHit raycast(final State state, final Vec3 from, final Vec3 to) {
        if (state == null || from == null || to == null) return null;

        final Quaterniond inverse = new Quaterniond(state.orientation()).conjugate();
        final Vector3d localFrom = new Vector3d(
                from.x - state.position().x,
                from.y - state.position().y,
                from.z - state.position().z
        );
        final Vector3d localTo = new Vector3d(
                to.x - state.position().x,
                to.y - state.position().y,
                to.z - state.position().z
        );
        inverse.transform(localFrom);
        inverse.transform(localTo);

        final double[] range = {0.0D, 1.0D};
        if (!clipAxis(localFrom.x, localTo.x - localFrom.x, state.halfExtent(), range)) return null;
        if (!clipAxis(localFrom.y, localTo.y - localFrom.y, state.halfExtent(), range)) return null;
        if (!clipAxis(localFrom.z, localTo.z - localFrom.z, state.halfExtent(), range)) return null;

        final double t = Math.max(0.0D, Math.min(1.0D, range[0]));
        final Vec3 world = from.lerp(to, t);
        final Vector3d local = localFrom.lerp(localTo, t, new Vector3d());
        return new RayHit(t, world, local);
    }

    private static State state(
            final double x,
            final double y,
            final double z,
            final double halfExtent,
            final double qx,
            final double qy,
            final double qz,
            final double qw
    ) {
        final Vector3d position = new Vector3d(x, y, z);
        final Quaterniond orientation = new Quaterniond(qx, qy, qz, qw).normalize();
        final double radius = halfExtent * 1.7320508075688772D;
        return new State(
                position,
                halfExtent,
                orientation,
                new AABB(
                        x - radius,
                        y - radius,
                        z - radius,
                        x + radius,
                        y + radius,
                        z + radius
                )
        );
    }

    private static boolean clipAxis(
            final double start,
            final double delta,
            final double half,
            final double[] range
    ) {
        if (Math.abs(delta) <= EPSILON) {
            return start >= -half && start <= half;
        }
        double t1 = (-half - start) / delta;
        double t2 = (half - start) / delta;
        if (t1 > t2) {
            final double swap = t1;
            t1 = t2;
            t2 = swap;
        }
        range[0] = Math.max(range[0], t1);
        range[1] = Math.min(range[1], t2);
        return range[1] >= range[0];
    }

    public record State(Vector3d position, double halfExtent, Quaterniond orientation, AABB broadphase) {}

    public record RayHit(double t, Vec3 worldPoint, Vector3d localPoint) {}

    private MoonPhysicsState() {}
}
