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
            ServerSubLevelContainer container,
            ServerSubLevel subLevel,
            double from,
            double to,
            Pivot pivot,
            boolean clearFloor
    ) {
        if (!ScaleBounds.isValid(from) || !ScaleBounds.isValid(to)) return;

        Vector3d position = new Vector3d(subLevel.logicalPose().position());
        Vector3d target = new Vector3d(position);
        if (pivot != null) {
            Vector3d world = pivot.resolve(container, subLevel);
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

    public static void setPoseScale(SubLevel subLevel, double scale) {
        if (!ScaleBounds.isValid(scale)) return;
        subLevel.logicalPose().scale().set(scale, scale, scale);
        subLevel.updateBoundingBox();
    }

    public static void enforceClientScale(ClientSubLevel subLevel) {
        double scale = ScaleState.getClientScale(subLevel);
        if (ScaleBounds.same(subLevel.logicalPose().scale().x(), scale)) return;
        setPoseScale(subLevel, scale);
        subLevel.forceUpdateBounds();
    }

    private ScaleMotion() {}
}
