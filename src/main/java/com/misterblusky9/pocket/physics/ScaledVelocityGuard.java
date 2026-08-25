package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaledVelocityGuard {
    private static final Map<UUID, double[]> LAST = new ConcurrentHashMap<>();

    public static void forget(final UUID id) {
        if (id != null) LAST.remove(id);
    }

    public static void afterStep(final PhysicsPipeline pipeline, final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (id == null) return;

        if (Math.abs(ScaleState.getServerScale(subLevel) - 1.0D) <= PocketSized.EPSILON) {
            LAST.remove(id);
            return;
        }

        final Vector3d linear = pipeline.getLinearVelocity(subLevel, new Vector3d());
        final Vector3d angular = pipeline.getAngularVelocity(subLevel, new Vector3d());
        if (linear == null || angular == null) return;

        final double[] previous = LAST.get(id);
        if (previous == null) {
            store(id, linear, angular);
            return;
        }

        final boolean resizing = !ScaleState.isSettled(id);

        final ScaledImpulseLimits.Correction correction = ScaledImpulseLimits.boundStep(
                subLevel,
                new Vector3d(previous[0], previous[1], previous[2]),
                new Vector3d(previous[3], previous[4], previous[5]),
                linear,
                angular,
                resizing);

        if (correction == null) {
            store(id, linear, angular);
            return;
        }

        pipeline.addLinearAndAngularVelocity(subLevel, correction.linear(), correction.angular());
        store(id, linear.add(correction.linear()), angular.add(correction.angular()));
    }

    private static void store(final UUID id, final Vector3d linear, final Vector3d angular) {
        LAST.put(id, new double[] {linear.x, linear.y, linear.z, angular.x, angular.y, angular.z});
    }

    private ScaledVelocityGuard() {}
}
