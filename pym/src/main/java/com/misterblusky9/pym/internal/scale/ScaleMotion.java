package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.physics.ExpansionClearance;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3d;

public final class ScaleMotion {
    private static final double MIN_MOVE_SQUARED = 1.0E-20D;

    static void apply(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel,
            final double from,
            final double to,
            final Pivot pivot,
            final boolean clearFloor
    ) {
        if (!ScaleBounds.isValid(from) || !ScaleBounds.isValid(to)) return;

        final Vector3d position = new Vector3d(subLevel.logicalPose().position());
        final Vector3d target = new Vector3d(position);
        if (pivot != null) {
            final Vector3d world = pivot.resolve(container, subLevel);
            target.sub(world).mul(to / from).add(world);
        }

        setPoseScale(subLevel, to);
        if (clearFloor && to > from) target.set(ExpansionClearance.resolve(subLevel, target, from, to));

        if (target.distanceSquared(position) > MIN_MOVE_SQUARED) {
            container.physicsSystem().getPipeline().teleport(subLevel, target, subLevel.logicalPose().orientation());
            subLevel.logicalPose().position().set(target);
            subLevel.updateBoundingBox();
        }
    }

    public static void setPoseScale(final SubLevel subLevel, final double scale) {
        if (!ScaleBounds.isValid(scale)) return;
        subLevel.logicalPose().scale().set(scale, scale, scale);
        subLevel.updateBoundingBox();
    }

    public static void enforceClientScale(final ClientSubLevel subLevel) {
        final double scale = ScaleState.getClientScale(subLevel);
        if (ScaleBounds.same(subLevel.logicalPose().scale().x(), scale)) return;
        setPoseScale(subLevel, scale);
        subLevel.forceUpdateBounds();
    }

    private ScaleMotion() {}
}
