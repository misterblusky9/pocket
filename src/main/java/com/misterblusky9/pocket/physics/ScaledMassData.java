package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;

public final class ScaledMassData implements MassData {
    private final MassData delegate;
    private final double scale;

    public static final double SUPERLIGHT_MASS = 0.25D;
    private static final double MIN_SOLVER_MASS = SUPERLIGHT_MASS;

    private final double scale3;
    private final double inertiaScale;
    private final double effectiveMass;

    public ScaledMassData(final MassData delegate, final double scale) {
        this.delegate = delegate;
        this.scale = scale;
        final double scale2 = scale * scale;
        this.scale3 = scale2 * scale;
        final double rawMass = Math.max(0.0D, delegate.getMass());
        final double similarityMass = rawMass * this.scale3;
        this.effectiveMass = rawMass > 0.0D ? Math.max(similarityMass, MIN_SOLVER_MASS) : 0.0D;
        final double effectiveMassScale = rawMass > 0.0D ? this.effectiveMass / rawMass : this.scale3;

        this.inertiaScale = Math.max(this.scale3 * scale2, effectiveMassScale * scale2);
    }

    @Override
    public double getMass() {
        return this.effectiveMass;
    }

    @Override
    public double getInverseMass() {
        return this.effectiveMass > 0.0D ? 1.0D / this.effectiveMass : this.delegate.getInverseMass();
    }

    @Override
    public Matrix3dc getInertiaTensor() {
        return new Matrix3d(this.delegate.getInertiaTensor()).scale(this.inertiaScale);
    }

    @Override
    public Matrix3dc getInverseInertiaTensor() {
        return new Matrix3d(this.delegate.getInverseInertiaTensor()).scale(1.0D / this.inertiaScale);
    }

    @Override
    public @Nullable Vector3dc getCenterOfMass() {
        return this.delegate.getCenterOfMass();
    }

    @Override
    public double getInverseNormalMass(final Vector3dc position, final Vector3dc direction) {
        return this.delegate.getInverseNormalMass(position, direction) / this.massScale();
    }

    public MassData delegate() {
        return this.delegate;
    }

    public double scale() {
        return this.scale;
    }

    public double massScale() {
        final double rawMass = Math.max(0.0D, this.delegate.getMass());
        return rawMass > 0.0D ? this.effectiveMass / rawMass : this.scale3;
    }

    public double inertiaScale() {
        return this.inertiaScale;
    }

    public static double[] forceFactors(final double rawMass, final double scale) {
        final double scale2 = scale * scale;
        final double scale3 = scale2 * scale;
        final double mass = Math.max(0.0D, rawMass);
        if (!(mass > 0.0D)) return new double[] {scale3, scale3 * scale2};

        final double effective = Math.max(mass * scale3, MIN_SOLVER_MASS);
        final double massScale = effective / mass;
        return new double[] {massScale, Math.max(scale3 * scale2, massScale * scale2)};
    }
}
