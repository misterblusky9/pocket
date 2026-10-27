package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;

public final class PairedForceScale {
    private static final ThreadLocal<Pair> PAIR = new ThreadLocal<>();

    private record Pair(ServerSubLevel first, ServerSubLevel second, double[] firstFactors, double[] secondFactors) {}

    public static boolean enter(@Nullable ServerSubLevel first, @Nullable ServerSubLevel second) {
        if (first == null || second == null || first == second || PAIR.get() != null) return false;
        double m1 = rawMass(first);
        double m2 = rawMass(second);
        if (!(m1 > 0.0D) || !(m2 > 0.0D)) return false;

        double e1 = solverMass(first, m1);
        double e2 = solverMass(second, m2);
        double shared = (e1 * e2 / (e1 + e2)) / (m1 * m2 / (m1 + m2));
        if (!Double.isFinite(shared) || !(shared > 0.0D)) return false;

        PAIR.set(new Pair(first, second, factors(first, m1, shared), factors(second, m2, shared)));
        return true;
    }

    public static void exit() {
        PAIR.remove();
    }

    @Nullable
    static double[] factorsFor(PhysicsPipelineBody body) {
        Pair pair = PAIR.get();
        if (pair == null) return null;
        return body == pair.first ? pair.firstFactors : body == pair.second ? pair.secondFactors : null;
    }

    private static double[] factors(ServerSubLevel subLevel, double rawMass, double shared) {
        double scale = InternalForceScaleContext.scaleOf(subLevel);
        double[] own = scale == 1.0D
                ? new double[] {1.0D, 1.0D}
                : ScaledMassData.forceFactors(rawMass, scale, CoupledMass.solverFloor(subLevel));
        return new double[] {shared, own[1] * shared / own[0]};
    }

    private static double solverMass(ServerSubLevel subLevel, double rawMass) {
        return ScaledMassData.effectiveMass(rawMass, InternalForceScaleContext.scaleOf(subLevel),
                CoupledMass.solverFloor(subLevel));
    }

    private static double rawMass(ServerSubLevel subLevel) {
        var tracker = subLevel.getMassTracker();
        double mass = tracker == null ? 0.0D : tracker.getMass();
        return Double.isFinite(mass) ? mass : 0.0D;
    }

    private PairedForceScale() {}
}
