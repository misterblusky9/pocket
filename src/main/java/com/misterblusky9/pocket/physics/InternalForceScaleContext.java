package com.misterblusky9.pocket.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;

import java.util.ArrayDeque;

public final class InternalForceScaleContext {
    private static final ThreadLocal<ArrayDeque<ServerSubLevel>> STACK =
            new ThreadLocal<>();

    public static void enter(final ServerSubLevel subLevel) {
        ArrayDeque<ServerSubLevel> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        stack.push(subLevel);
    }

    public static void exit(final ServerSubLevel subLevel) {
        final ArrayDeque<ServerSubLevel> stack = STACK.get();
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

    public static double activeScale(final PhysicsPipelineBody body) {
        if (!(body instanceof final ServerSubLevel subLevel)) return 1.0D;

        final ArrayDeque<ServerSubLevel> stack = STACK.get();
        if (stack == null || stack.isEmpty() || stack.peek() != subLevel) return 1.0D;

        final double scale = ScaleState.getServerScale(subLevel);
        if (!Double.isFinite(scale)
                || scale <= 0.0D
                || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            return 1.0D;
        }
        return scale;
    }

    public static double[] forceFactors(final PhysicsPipelineBody body) {
        final double scale = activeScale(body);
        if (scale == 1.0D || !(body instanceof final ServerSubLevel subLevel)) {
            return IDENTITY;
        }

        final var tracker = subLevel.getMassTracker();
        final double rawMass = tracker == null ? 0.0D : tracker.getMass();
        return ScaledMassData.forceFactors(rawMass, scale);
    }

    private static final double[] IDENTITY = {1.0D, 1.0D};

    private InternalForceScaleContext() {
    }
}
