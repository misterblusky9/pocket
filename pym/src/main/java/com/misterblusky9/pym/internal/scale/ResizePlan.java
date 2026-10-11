package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ResizeResult;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.entity.Entity;

import java.util.Map;

record ResizePlan(
        ServerSubLevel origin,
        Map<ServerSubLevel, Double> goals,
        Map<Entity, Double> entityGoals,
        Pivot pivot,
        double ticks,
        ResizeResult refusal
) {
    static ResizePlan refused(final ServerSubLevel origin, final ResizeResult refusal) {
        return new ResizePlan(origin, Map.of(), Map.of(), null, 0.0D, refusal);
    }

    double originGoal() {
        final Double goal = this.goals.get(this.origin);
        return goal == null ? ScaleState.getTargetScale(this.origin) : goal;
    }
}
