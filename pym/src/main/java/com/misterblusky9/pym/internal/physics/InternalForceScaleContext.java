package com.misterblusky9.pym.internal.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.ScaleState;

import java.util.ArrayDeque;

public final class InternalForceScaleContext {
    private static final ThreadLocal<ArrayDeque<ServerSubLevel>> STACK =
            new ThreadLocal<>();

    public static void enter(ServerSubLevel subLevel) {
        ArrayDeque<ServerSubLevel> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        stack.push(subLevel);
    }

    public static void exit(ServerSubLevel subLevel) {
        ArrayDeque<ServerSubLevel> stack = STACK.get();
        if (stack == null) return;
        if (!stack.isEmpty()) {
            if (stack.peek() == subLevel) {
                stack.pop();
            } else {
                stack.remove(subLevel);
            }
        }
        if (stack.isEmpty()) STACK.remove();
    }

    public static double activeScale(PhysicsPipelineBody body) {
        if (!(body instanceof ServerSubLevel subLevel)) return 1.0D;
        if (PairedForceScale.factorsFor(body) != null) return scaleOf(subLevel);

        ArrayDeque<ServerSubLevel> stack = STACK.get();
        if (stack == null || stack.isEmpty() || stack.peek() != subLevel) return 1.0D;

        return scaleOf(subLevel);
    }

    public static double scaleOf(ServerSubLevel subLevel) {
        double scale = ScaleState.getServerScale(subLevel);
        if (!Double.isFinite(scale)
                || scale <= 0.0D
                || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            return 1.0D;
        }
        return scale;
    }

    public static double[] forceFactors(PhysicsPipelineBody body) {
        double[] paired = PairedForceScale.factorsFor(body);
        if (paired != null) return paired;
        double scale = activeScale(body);
        if (scale == 1.0D || !(body instanceof ServerSubLevel subLevel)) {
            return IDENTITY;
        }
        return factorsAt(subLevel, scale);
    }

    public static double[] queuedFactors(ServerSubLevel subLevel) {
        double scale = scaleOf(subLevel);
        return scale == 1.0D ? IDENTITY : factorsAt(subLevel, scale);
    }

    private static double[] factorsAt(ServerSubLevel subLevel, double scale) {
        var tracker = subLevel.getMassTracker();
        double rawMass = tracker == null ? 0.0D : tracker.getMass();
        return ScaledMassData.forceFactors(rawMass, scale, CoupledMass.solverFloor(subLevel));
    }

    private static final double[] IDENTITY = {1.0D, 1.0D};

    private InternalForceScaleContext() {
    }
}
