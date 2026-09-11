package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.compression.CompressionBlacklist;
import com.misterblusky9.pocket.compression.CompressionSessions;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWeldSync;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.compat.simulated.WeldRuntime;
import com.misterblusky9.pocket.compat.simulated.WeldStore;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.physics.ColliderDetail;
import com.misterblusky9.pocket.physics.PivotDriftCompensation;
import com.misterblusky9.pocket.physics.ScaledBoundsCollider;
import com.misterblusky9.pocket.physics.ScaledColliderRebuildQueue;
import com.misterblusky9.pocket.physics.ScaledFluidForces;
import com.misterblusky9.pocket.physics.ScaledSweepGuard;
import com.misterblusky9.pocket.physics.ScaledVelocityGuard;
import com.misterblusky9.pocket.pocket.PocketMetrics;
import com.misterblusky9.pocket.tweezers.TweezerLocks;
import com.misterblusky9.pocket.tweezers.TweezerSessions;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.UUID;

public final class ScaleLifecycle {
    public static void release(
            final ServerSubLevel subLevel,
            final SubLevelRemovalReason reason
    ) {
        if (subLevel == null || subLevel.getUniqueId() == null) return;

        final UUID id = subLevel.getUniqueId();
        PocketTrace.scale("release runtime state uuid={} reason={}", id, reason);

        CompressionSessions.releaseSubLevel(subLevel);
        TweezerSessions.releaseSubLevel(subLevel);
        if (reason == SubLevelRemovalReason.REMOVED) {
            TweezerLocks.remove(subLevel.getLevel(), subLevel);
            releaseWelds(subLevel, id);
        }

        ScaleController.clearExternalCommand(id);
        ManualScaleOverride.clear(id);
        CompressionBlacklist.invalidate(id);
        ScaledColliderRebuildQueue.forget(subLevel);
        ScaledBoundsCollider.forgetSubLevel(id);
        ScaledVelocityGuard.forget(id);
        ScaledSweepGuard.forget(id);
        ScaledFluidForces.forget(id);
        ColliderDetail.forget(id);
        PivotDriftCompensation.forget(id);
        SubLevelParentage.forget(id);
        PocketMetrics.invalidate(id);
        ScaleState.clearServerBounds(id);
        ScaleState.clearServerState(id);
    }

    // Only a permanent removal cuts welds. An unload has to leave the record alone so the
    // weld can be rebuilt when the craft comes back.
    private static void releaseWelds(final ServerSubLevel subLevel, final UUID id) {
        if (!(subLevel.getLevel() instanceof final ServerLevel serverLevel)) return;
        final List<WeldRecord> cut = WeldStore.get(serverLevel).removeAllTouching(id);
        if (cut.isEmpty()) return;
        for (final WeldRecord record : cut) WeldRuntime.drop(record.weldId());
        CrossScaleWeldSync.broadcast(serverLevel);
    }

    private ScaleLifecycle() {}
}
