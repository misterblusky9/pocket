package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterRivets;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ConstraintConfigurations {
    public static PhysicsConstraintConfiguration<?> copy(PhysicsConstraintConfiguration<?> configuration) {
        if (configuration instanceof FixedConstraintConfiguration fixed) {
            return new FixedConstraintConfiguration(
                    copy(fixed.pos1()), copy(fixed.pos2()), new Quaterniond(fixed.orientation()));
        }
        if (configuration instanceof FreeConstraintConfiguration free) {
            return new FreeConstraintConfiguration(
                    copy(free.pos1()), copy(free.pos2()), new Quaterniond(free.orientation()));
        }
        if (configuration instanceof GenericConstraintConfiguration generic) {
            return new GenericConstraintConfiguration(
                    copy(generic.pos1()), copy(generic.pos2()),
                    new Quaterniond(generic.orientation1()), new Quaterniond(generic.orientation2()),
                    generic.lockedAxes());
        }
        if (configuration instanceof RotaryConstraintConfiguration rotary) {
            return new RotaryConstraintConfiguration(
                    copy(rotary.pos1()), copy(rotary.pos2()),
                    copy(rotary.normal1()), copy(rotary.normal2()));
        }

        return configuration;
    }

    public static boolean anchorsWouldMove(
            PhysicsConstraintConfiguration<?> configuration,
            PhysicsPipelineBody body1,
            Vector3dc pivot1,
            double scale1,
            PhysicsPipelineBody body2,
            Vector3dc pivot2,
            double scale2
    ) {
        if (configuration == null) return true;

        if (configuration instanceof FixedConstraintConfiguration fixed
                && CoasterRivets.requiresRefresh(
                        fixed, body1, scale1, body2, scale2)) {
            return true;
        }

        return anchorMoves(body1, pos1(configuration), pivot1, scale1)
                || anchorMoves(body2, pos2(configuration), pivot2, scale2);
    }

    private static boolean anchorMoves(
            PhysicsPipelineBody body,
            Vector3dc anchor,
            Vector3dc bakedPivot,
            double bakedScale
    ) {
        if (anchor == null || bakedPivot == null) return false;
        if (!(body instanceof ServerSubLevel subLevel)) return false;

        Vector3dc pivotNow = ScaleFrame.pivot(subLevel);
        if (pivotNow == null) return false;

        double scaleNow = ScaleState.getServerScale(subLevel);

        Vector3d then = new Vector3d(anchor).sub(bakedPivot).mul(bakedScale);
        Vector3d now = new Vector3d(anchor).sub(pivotNow).mul(scaleNow);

        return now.distanceSquared(then) > 1.0E-12D;
    }

    public static Vector3dc pos1(PhysicsConstraintConfiguration<?> configuration) {
        if (configuration instanceof FixedConstraintConfiguration fixed) return fixed.pos1();
        if (configuration instanceof FreeConstraintConfiguration free) return free.pos1();
        if (configuration instanceof GenericConstraintConfiguration generic) return generic.pos1();
        if (configuration instanceof RotaryConstraintConfiguration rotary) return rotary.pos1();
        return null;
    }

    public static Vector3dc pos2(PhysicsConstraintConfiguration<?> configuration) {
        if (configuration instanceof FixedConstraintConfiguration fixed) return fixed.pos2();
        if (configuration instanceof FreeConstraintConfiguration free) return free.pos2();
        if (configuration instanceof GenericConstraintConfiguration generic) return generic.pos2();
        if (configuration instanceof RotaryConstraintConfiguration rotary) return rotary.pos2();
        return null;
    }

    private static Vector3dc copy(Vector3dc value) {
        return value == null ? null : new Vector3d(value);
    }

    private ConstraintConfigurations() {}
}
