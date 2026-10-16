package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3dc;

public final class ScaledMassData implements MassData {
    private static final double MIN_SOLVER_MASS = 0.05D;

    private final MassData delegate;

    private final double scale3;
    private final double inertiaScale;
    private final double inertiaOffset;
    private final double effectiveMass;

    public ScaledMassData(MassData delegate, double scale) {
        this(delegate, scale, 0.0D);
    }

    public ScaledMassData(MassData delegate, double scale, double floor) {
        this(delegate, scale, floor, 0.0D);
    }

    public ScaledMassData(MassData delegate, double scale, double floor, double inertiaFloor) {
        this.delegate = delegate;
        double scale2 = scale * scale;
        this.scale3 = scale2 * scale;
        double rawMass = Math.max(0.0D, delegate.getMass());
        this.effectiveMass = effectiveMass(rawMass, scale, floor);
        double effectiveMassScale = rawMass > 0.0D ? this.effectiveMass / rawMass : this.scale3;

        this.inertiaScale = Math.max(this.scale3 * scale2, effectiveMassScale * scale2);
        Matrix3dc tensor = delegate.getInertiaTensor();
        double mean = (tensor.m00() + tensor.m11() + tensor.m22()) * this.inertiaScale / 3.0D;
        this.inertiaOffset = Double.isFinite(inertiaFloor) ? Math.max(0.0D, inertiaFloor - mean) : 0.0D;
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
        Matrix3d tensor = new Matrix3d(this.delegate.getInertiaTensor()).scale(this.inertiaScale);
        if (this.inertiaOffset > 0.0D) {
            tensor.m00(tensor.m00() + this.inertiaOffset);
            tensor.m11(tensor.m11() + this.inertiaOffset);
            tensor.m22(tensor.m22() + this.inertiaOffset);
        }
        return tensor;
    }

    @Override
    public Matrix3dc getInverseInertiaTensor() {
        if (this.inertiaOffset == 0.0D) {
            return new Matrix3d(this.delegate.getInverseInertiaTensor()).scale(1.0D / this.inertiaScale);
        }
        return new Matrix3d(this.getInertiaTensor()).invert();
    }

    @Override
    public @Nullable Vector3dc getCenterOfMass() {
        return this.delegate.getCenterOfMass();
    }

    @Override
    public double getInverseNormalMass(Vector3dc position, Vector3dc direction) {
        return this.delegate.getInverseNormalMass(position, direction) / this.massScale();
    }

    public double massScale() {
        double rawMass = Math.max(0.0D, this.delegate.getMass());
        return rawMass > 0.0D ? this.effectiveMass / rawMass : this.scale3;
    }

    public static double similarityMass(double rawMass, double scale) {
        double mass = Math.max(0.0D, rawMass);
        return mass * scale * scale * scale;
    }

    public static double effectiveMass(double rawMass, double scale, double floor) {
        double mass = Math.max(0.0D, rawMass);
        if (!(mass > 0.0D)) return 0.0D;
        return Math.max(similarityMass(mass, scale), lowerBound(floor));
    }

    public static double[] forceFactors(double rawMass, double scale, double floor) {
        double scale2 = scale * scale;
        double scale3 = scale2 * scale;
        double mass = Math.max(0.0D, rawMass);
        if (!(mass > 0.0D)) return new double[] {scale3, scale3 * scale2};

        double massScale = effectiveMass(mass, scale, floor) / mass;
        return new double[] {massScale, Math.max(scale3 * scale2, massScale * scale2)};
    }

    private static double lowerBound(double floor) {
        return Double.isFinite(floor) && floor > MIN_SOLVER_MASS ? floor : MIN_SOLVER_MASS;
    }
}
