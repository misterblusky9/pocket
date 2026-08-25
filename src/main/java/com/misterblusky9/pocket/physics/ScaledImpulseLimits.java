package com.misterblusky9.pocket.physics;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.debug.PocketTrace;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ScaledImpulseLimits {
    private static final double MAX_EXTERNAL_DELTA_V = 12.0D;

    private static final double MAX_EXTERNAL_DELTA_OMEGA = 8.0D;

    private static final double MAX_STEP_DELTA_V = 80.0D;
    private static final double MAX_STEP_DELTA_OMEGA = 40.0D;

    private static final double MAX_STEP_DELTA_V_RESIZING = 12.0D;
    private static final double MAX_STEP_DELTA_OMEGA_RESIZING = 8.0D;

    private static final double MAX_SPEED = 120.0D;
    private static final double MAX_SPIN = 40.0D;

    public static double solverMass(final ServerSubLevel subLevel) {
        final var tracker = subLevel.getMassTracker();
        final double raw = tracker == null ? 0.0D : tracker.getMass();
        if (!Double.isFinite(raw) || raw <= 0.0D) return 0.0D;

        final double scale = ScaleState.getServerScale(subLevel);

        return raw * ScaledMassData.forceFactors(raw, scale)[0];
    }

    public static boolean bounds(final PhysicsPipelineBody body) {
        if (!(body instanceof final ServerSubLevel subLevel)) return false;
        if (subLevel.isRemoved()) return false;

        if (InternalForceScaleContext.activeScale(body) != 1.0D) return false;
        return Math.abs(ScaleState.getServerScale(subLevel) - 1.0D) > PocketSized.EPSILON;
    }

    public static double boundImpulse(
            final ServerSubLevel subLevel,
            final Vector3d linear,
            final Vector3d angular
    ) {
        final double mass = solverMass(subLevel);
        if (mass <= 0.0D) return 1.0D;

        double factor = 1.0D;

        final double deltaV = linear.length() / mass;
        if (deltaV > MAX_EXTERNAL_DELTA_V) {
            factor = Math.min(factor, MAX_EXTERNAL_DELTA_V / deltaV);
        }

        final double inertia = principalInertia(subLevel);
        if (inertia > 0.0D) {
            final double deltaOmega = angular.length() / inertia;
            if (deltaOmega > MAX_EXTERNAL_DELTA_OMEGA) {
                factor = Math.min(factor, MAX_EXTERNAL_DELTA_OMEGA / deltaOmega);
            }
        }

        if (factor >= 1.0D) return 1.0D;

        PocketTrace.scale(
                "bounded external impulse {} factor={} |J|={} |T|={} solverMass={}",
                PocketTrace.context(subLevel), factor, linear.length(), angular.length(), mass);

        linear.mul(factor);
        angular.mul(factor);
        return factor;
    }

    private static double principalInertia(final ServerSubLevel subLevel) {
        final var tracker = subLevel.getMassTracker();
        if (tracker == null) return 0.0D;

        final var raw = tracker.getInertiaTensor();
        if (raw == null) return 0.0D;

        final double rawMass = tracker.getMass();
        final double scale = ScaleState.getServerScale(subLevel);
        final double inertiaScale = ScaledMassData.forceFactors(rawMass, scale)[1];

        final double smallest = Math.min(raw.m00(), Math.min(raw.m11(), raw.m22()));
        final double inertia = smallest * inertiaScale;
        return Double.isFinite(inertia) && inertia > 0.0D ? inertia : 0.0D;
    }

    public static Correction boundStep(
            final ServerSubLevel subLevel,
            final Vector3dc beforeLinear,
            final Vector3dc beforeAngular,
            final Vector3dc afterLinear,
            final Vector3dc afterAngular,
            final boolean resizing
    ) {
        if (!finite(afterLinear) || !finite(afterAngular) || !finite(beforeLinear) || !finite(beforeAngular)) {
            return null;
        }

        final Vector3d linear = new Vector3d(afterLinear);
        final Vector3d angular = new Vector3d(afterAngular);
        boolean corrected = false;

        final double stepLinearBudget = resizing ? MAX_STEP_DELTA_V_RESIZING : MAX_STEP_DELTA_V;
        final double stepAngularBudget = resizing ? MAX_STEP_DELTA_OMEGA_RESIZING : MAX_STEP_DELTA_OMEGA;

        final Vector3d deltaLinear = new Vector3d(afterLinear).sub(beforeLinear);
        final double deltaSpeed = deltaLinear.length();
        if (deltaSpeed > stepLinearBudget && afterLinear.length() > beforeLinear.length()) {
            linear.set(beforeLinear).fma(stepLinearBudget / deltaSpeed, deltaLinear);
            corrected = true;
        }

        final Vector3d deltaAngular = new Vector3d(afterAngular).sub(beforeAngular);
        final double deltaSpin = deltaAngular.length();
        if (deltaSpin > stepAngularBudget && afterAngular.length() > beforeAngular.length()) {
            angular.set(beforeAngular).fma(stepAngularBudget / deltaSpin, deltaAngular);
            corrected = true;
        }

        final double speed = linear.length();
        if (speed > MAX_SPEED) {
            linear.mul(MAX_SPEED / speed);
            corrected = true;
        }

        final double spin = angular.length();
        if (spin > MAX_SPIN) {
            angular.mul(MAX_SPIN / spin);
            corrected = true;
        }

        if (!corrected) return null;

        PocketTrace.scale(
                "bounded solver step {} dV={} dW={} -> v={} w={}",
                PocketTrace.context(subLevel), deltaSpeed, deltaSpin, linear.length(), angular.length());

        return new Correction(
                linear.sub(afterLinear, new Vector3d()),
                angular.sub(afterAngular, new Vector3d()));
    }

    private static boolean finite(final Vector3dc vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    public record Correction(Vector3d linear, Vector3d angular) {}

    private ScaledImpulseLimits() {}
}
