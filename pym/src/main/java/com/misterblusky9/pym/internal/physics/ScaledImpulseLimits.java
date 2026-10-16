package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.debug.PymTrace;
import com.misterblusky9.pym.internal.scale.ScaleState;
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

    public static double solverMass(ServerSubLevel subLevel) {
        var tracker = subLevel.getMassTracker();
        double raw = tracker == null ? 0.0D : tracker.getMass();
        if (!Double.isFinite(raw) || raw <= 0.0D) return 0.0D;

        double scale = ScaleState.getServerScale(subLevel);

        return raw * ScaledMassData.forceFactors(raw, scale, CoupledMass.solverFloor(subLevel))[0];
    }

    public static boolean bounds(PhysicsPipelineBody body) {
        if (!(body instanceof ServerSubLevel subLevel)) return false;
        if (subLevel.isRemoved()) return false;

        if (InternalForceScaleContext.activeScale(body) != 1.0D) return false;
        return Math.abs(ScaleState.getServerScale(subLevel) - 1.0D) > ScaleBounds.EPSILON;
    }

    public static double boundImpulse(
            ServerSubLevel subLevel,
            Vector3d linear,
            Vector3d angular
    ) {
        double mass = solverMass(subLevel);
        if (mass <= 0.0D) return 1.0D;

        double factor = 1.0D;

        double deltaV = linear.length() / mass;
        if (deltaV > MAX_EXTERNAL_DELTA_V) {
            factor = Math.min(factor, MAX_EXTERNAL_DELTA_V / deltaV);
        }

        double inertia = principalInertia(subLevel);
        if (inertia > 0.0D) {
            double deltaOmega = angular.length() / inertia;
            if (deltaOmega > MAX_EXTERNAL_DELTA_OMEGA) {
                factor = Math.min(factor, MAX_EXTERNAL_DELTA_OMEGA / deltaOmega);
            }
        }

        if (factor >= 1.0D) return 1.0D;

        PymTrace.scale(
                "bounded external impulse {} factor={} |J|={} |T|={} solverMass={}",
                PymTrace.context(subLevel), factor, linear.length(), angular.length(), mass);

        linear.mul(factor);
        angular.mul(factor);
        return factor;
    }

    private static double principalInertia(ServerSubLevel subLevel) {
        var tracker = subLevel.getMassTracker();
        if (tracker == null) return 0.0D;

        var raw = tracker.getInertiaTensor();
        if (raw == null) return 0.0D;

        double rawMass = tracker.getMass();
        double scale = ScaleState.getServerScale(subLevel);
        double inertiaScale =
                ScaledMassData.forceFactors(rawMass, scale, CoupledMass.solverFloor(subLevel))[1];

        double smallest = Math.min(raw.m00(), Math.min(raw.m11(), raw.m22()));
        double inertia = smallest * inertiaScale;
        return Double.isFinite(inertia) && inertia > 0.0D ? inertia : 0.0D;
    }

    public static Correction boundStep(
            ServerSubLevel subLevel,
            Vector3dc beforeLinear,
            Vector3dc beforeAngular,
            Vector3dc afterLinear,
            Vector3dc afterAngular,
            boolean resizing
    ) {
        if (!finite(afterLinear) || !finite(afterAngular) || !finite(beforeLinear) || !finite(beforeAngular)) {
            return null;
        }

        Vector3d linear = new Vector3d(afterLinear);
        Vector3d angular = new Vector3d(afterAngular);
        boolean corrected = false;

        double stepLinearBudget = resizing ? MAX_STEP_DELTA_V_RESIZING : MAX_STEP_DELTA_V;
        double stepAngularBudget = resizing ? MAX_STEP_DELTA_OMEGA_RESIZING : MAX_STEP_DELTA_OMEGA;

        Vector3d deltaLinear = new Vector3d(afterLinear).sub(beforeLinear);
        double deltaSpeed = deltaLinear.length();
        if (deltaSpeed > stepLinearBudget && afterLinear.length() > beforeLinear.length()) {
            linear.set(beforeLinear).fma(stepLinearBudget / deltaSpeed, deltaLinear);
            corrected = true;
        }

        Vector3d deltaAngular = new Vector3d(afterAngular).sub(beforeAngular);
        double deltaSpin = deltaAngular.length();
        if (deltaSpin > stepAngularBudget && afterAngular.length() > beforeAngular.length()) {
            angular.set(beforeAngular).fma(stepAngularBudget / deltaSpin, deltaAngular);
            corrected = true;
        }

        double speed = linear.length();
        if (speed > MAX_SPEED) {
            linear.mul(MAX_SPEED / speed);
            corrected = true;
        }

        double spin = angular.length();
        if (spin > MAX_SPIN) {
            angular.mul(MAX_SPIN / spin);
            corrected = true;
        }

        if (!corrected) return null;

        PymTrace.scale(
                "bounded solver step {} dV={} dW={} -> v={} w={}",
                PymTrace.context(subLevel), deltaSpeed, deltaSpin, linear.length(), angular.length());

        return new Correction(
                linear.sub(afterLinear, new Vector3d()),
                angular.sub(afterAngular, new Vector3d()));
    }

    private static boolean finite(Vector3dc vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    public record Correction(Vector3d linear, Vector3d angular) {}

    private ScaledImpulseLimits() {}
}
