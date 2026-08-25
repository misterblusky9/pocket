package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.scale.ScaleState;
import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class KinematicCollisionSuppression {
    private static final Set<KinematicContraption> SUPPRESSED =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static synchronized boolean shouldSuppress(
            final ServerLevel level,
            final KinematicContraption contraption
    ) {
        if (level == null || contraption == null || !contraption.sable$isValid()) return false;

        final SubLevel parent = Sable.HELPER.getContaining(level, contraption.sable$getPosition());
        if (!(parent instanceof final ServerSubLevel serverParent) || serverParent.isRemoved()) {
            return false;
        }

        return ScaleState.getServerScale(serverParent) < 1.0D - PocketSized.EPSILON;
    }

    public static synchronized void markSuppressed(final KinematicContraption contraption) {
        if (contraption != null) SUPPRESSED.add(contraption);
    }

    public static synchronized void forget(final KinematicContraption contraption) {
        if (contraption != null) SUPPRESSED.remove(contraption);
    }

    public static synchronized void ensureSuppressed(
            final ServerSubLevel parent,
            final PhysicsPipeline pipeline
    ) {
        for (final KinematicContraption child : parent.getPlot().getContraptions()) {
            if (child == null || !child.sable$isValid() || SUPPRESSED.contains(child)) continue;

            PocketTrace.scale(
                    "suppressing contraption collider uuid={} contraption={}@{} totalSuppressions={}",
                    parent.getUniqueId(),
                    child.getClass().getSimpleName(),
                    Integer.toHexString(System.identityHashCode(child)),
                    ++suppressions);

            pipeline.remove(child);
            SUPPRESSED.add(child);
        }
    }

    public static synchronized void ensureRestored(
            final ServerSubLevel parent,
            final PhysicsPipeline pipeline
    ) {
        for (final KinematicContraption child : parent.getPlot().getContraptions()) {
            if (child == null || !child.sable$isValid() || !SUPPRESSED.remove(child)) continue;
            PocketTrace.scale(
                    "restoring contraption collider uuid={} contraption={}@{}",
                    parent.getUniqueId(),
                    child.getClass().getSimpleName(),
                    Integer.toHexString(System.identityHashCode(child)));
            pipeline.add(child);
        }
    }

    private static int suppressions;

    private KinematicCollisionSuppression() {
    }
}
