package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.event.SubLevelScaleEvent;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.physics.ColliderDetail;
import com.misterblusky9.pym.internal.physics.PivotDriftCompensation;
import com.misterblusky9.pym.internal.physics.ColliderCoordinator;
import com.misterblusky9.pym.internal.physics.ScaledColliderRebuildQueue;
import com.misterblusky9.pym.internal.physics.ScaledFluidForces;
import com.misterblusky9.pym.internal.physics.ScaledSweepGuard;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import com.misterblusky9.pym.internal.physics.ScaledVelocityGuard;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

public final class ScaleLifecycle {
    public static void release(
            final ServerSubLevel subLevel,
            final SubLevelRemovalReason reason
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;

        final UUID id = subLevel.getUniqueId();
        PymTrace.scale("release runtime state uuid={} reason={}", id, reason);

        NeoForge.EVENT_BUS.post(new SubLevelScaleEvent.Released(subLevel, reason));

        ScaledColliderRebuildQueue.forget(subLevel);
        discard(id);
    }

    public static void discard(final UUID id) {
        if (id == null) return;
        ScaleDrivers.forget(id);
        ScalePhysicsTransitions.forget(id);
        ColliderCoordinator.forget(id);
        ScaledVelocityGuard.forget(id);
        ScaledSweepGuard.forget(id);
        ScaledFluidForces.forget(id);
        ColliderDetail.forget(id);
        PivotDriftCompensation.forget(id);
        SubLevelParentage.forget(id);
        PlotScan.forget(id);
        ScaleState.forget(id);
    }

    private ScaleLifecycle() {}
}
