package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.scale.ScaleState;
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
    public static PhysicsConstraintConfiguration<?> copy(final PhysicsConstraintConfiguration<?> configuration) {
        if (configuration instanceof final FixedConstraintConfiguration fixed) {
            return new FixedConstraintConfiguration(
                    copy(fixed.pos1()), copy(fixed.pos2()), new Quaterniond(fixed.orientation()));
        }
        if (configuration instanceof final FreeConstraintConfiguration free) {
            return new FreeConstraintConfiguration(
                    copy(free.pos1()), copy(free.pos2()), new Quaterniond(free.orientation()));
        }
        if (configuration instanceof final GenericConstraintConfiguration generic) {
            return new GenericConstraintConfiguration(
                    copy(generic.pos1()), copy(generic.pos2()),
                    new Quaterniond(generic.orientation1()), new Quaterniond(generic.orientation2()),
                    generic.lockedAxes());
        }
        if (configuration instanceof final RotaryConstraintConfiguration rotary) {
            return new RotaryConstraintConfiguration(
                    copy(rotary.pos1()), copy(rotary.pos2()),
                    copy(rotary.normal1()), copy(rotary.normal2()));
        }

        return configuration;
    }

    public static boolean anchorsWouldMove(
            final PhysicsConstraintConfiguration<?> configuration,
            final PhysicsPipelineBody body1,
            final Vector3dc pivot1,
            final double scale1,
            final PhysicsPipelineBody body2,
            final Vector3dc pivot2,
            final double scale2
    ) {
        if (configuration == null) return true;

        return anchorMoves(body1, pos1(configuration), pivot1, scale1)
                || anchorMoves(body2, pos2(configuration), pivot2, scale2);
    }

    private static boolean anchorMoves(
            final PhysicsPipelineBody body,
            final Vector3dc anchor,
            final Vector3dc bakedPivot,
            final double bakedScale
    ) {
        if (anchor == null || bakedPivot == null) return false;
        if (!(body instanceof final ServerSubLevel subLevel)) return false;

        final Vector3dc pivotNow = ScaleFrame.pivot(subLevel);
        if (pivotNow == null) return false;

        final double scaleNow = ScaleState.getServerScale(subLevel);

        final Vector3d then = new Vector3d(anchor).sub(bakedPivot).mul(bakedScale);
        final Vector3d now = new Vector3d(anchor).sub(pivotNow).mul(scaleNow);

        return now.distanceSquared(then) > 1.0E-12D;
    }

    public static Vector3dc pos1(final PhysicsConstraintConfiguration<?> configuration) {
        if (configuration instanceof final FixedConstraintConfiguration fixed) return fixed.pos1();
        if (configuration instanceof final FreeConstraintConfiguration free) return free.pos1();
        if (configuration instanceof final GenericConstraintConfiguration generic) return generic.pos1();
        if (configuration instanceof final RotaryConstraintConfiguration rotary) return rotary.pos1();
        return null;
    }

    public static Vector3dc pos2(final PhysicsConstraintConfiguration<?> configuration) {
        if (configuration instanceof final FixedConstraintConfiguration fixed) return fixed.pos2();
        if (configuration instanceof final FreeConstraintConfiguration free) return free.pos2();
        if (configuration instanceof final GenericConstraintConfiguration generic) return generic.pos2();
        if (configuration instanceof final RotaryConstraintConfiguration rotary) return rotary.pos2();
        return null;
    }

    private static Vector3dc copy(final Vector3dc value) {
        return value == null ? null : new Vector3d(value);
    }

    private ConstraintConfigurations() {}
}
