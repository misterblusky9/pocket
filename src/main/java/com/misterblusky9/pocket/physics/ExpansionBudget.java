package com.misterblusky9.pocket.physics;

public final class ExpansionBudget {
    public static final double BASE_NUDGE_PER_TICK = 0.5D;

    public static double perTick(
            final double pivotY,
            final double boxMinY,
            final double previousScale,
            final double nextScale
    ) {
        if (!(nextScale > previousScale) || previousScale <= 0.0D) return BASE_NUDGE_PER_TICK;

        final double lowerExtent = pivotY - boxMinY;
        if (!(lowerExtent > 0.0D)) return BASE_NUDGE_PER_TICK;

        return BASE_NUDGE_PER_TICK + lowerExtent * (1.0D - previousScale / nextScale);
    }

    private ExpansionBudget() {}
}
