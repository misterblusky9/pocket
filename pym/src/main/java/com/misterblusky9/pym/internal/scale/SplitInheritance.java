package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.internal.network.ScaleNetwork;
import com.misterblusky9.pym.internal.physics.PlotShapeCache;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SplitInheritance {
    public record Source(ServerSubLevel parent, Pose3d pose, double scale, ScaleRecord ramp, ScalePhysicsMode mode) {}

    public static Source capture(Level level, BlockPos anchor) {
        if (!(Sable.HELPER.getContaining(level, anchor) instanceof ServerSubLevel parent) || parent.isRemoved()) return null;
        return new Source(parent, new Pose3d(parent.logicalPose()), ScaleState.getServerScale(parent),
                ScaleState.recordOf(parent.getUniqueId()), ScalePhysicsTransitions.modeOf(parent));
    }

    public static double adoptLive(ServerSubLevel child, ServerSubLevel parent) {
        ScaleRecord ramp = ScaleState.recordOf(parent.getUniqueId());
        double scale = ScaleState.getServerScale(parent);
        adopt(child, parent, scale, ramp, ramp == null ? null : ramp.pivot(), ScalePhysicsTransitions.modeOf(parent));
        return scale;
    }

    public static void adoptOrphan(Source source, ServerSubLevel child) {
        if (source == null || child == null || child.isRemoved() || !source.parent().isRemoved()) return;
        if (ScaleState.hasServerState(child.getUniqueId())) return;
        ScaleRecord ramp = source.ramp();
        Pivot pivot = ramp == null || ramp.pivot() == null
                ? null
                : ramp.pivot().detach(source.parent().getUniqueId(), source.pose());
        adopt(child, source.parent(), source.scale(), ramp, pivot, source.mode());
    }

    private static void adopt(
            ServerSubLevel child,
            ServerSubLevel parent,
            double scale,
            ScaleRecord ramp,
            Pivot pivot,
            ScalePhysicsMode mode
    ) {
        ScaleRecord record = ScaleState.settle(child, ScaleSnap.snap(scale));
        if (ramp != null && ramp.moving()) {
            record.follow(ramp, pivot);
            ScalePhysicsTransitions.setMode(child, mode);
        }
        ScaleMotion.setPoseScale(child, record.current());
        child.updateLastPose();
        ScalePersistence.persist(child);
        ScaleState.clearServerBounds(child.getUniqueId());
        PlotShapeCache.invalidate(parent);
        PlotShapeCache.invalidate(child);
        if (!parent.isRemoved()) SubLevelParentage.record(child, parent);
        ScaleNetwork.sendScale(child, record.current(), record.target(), true);
    }

    private SplitInheritance() {}
}
