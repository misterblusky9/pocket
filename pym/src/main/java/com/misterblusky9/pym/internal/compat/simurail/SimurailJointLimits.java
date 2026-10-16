package com.misterblusky9.pym.internal.compat.simurail;

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SimurailJointLimits {
    private static final Set<PhysicsConstraintHandle> RAIL_GUIDES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final ThreadLocal<Integer> METRIC_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void railGuide(PhysicsConstraintHandle handle) {
        if (handle != null && handle.isValid()) RAIL_GUIDES.add(handle);
    }

    public static boolean isRailGuide(PhysicsConstraintHandle handle) {
        return handle != null && RAIL_GUIDES.contains(handle);
    }

    public static boolean worldMetricContext() {
        return METRIC_DEPTH.get() != 0;
    }

    public static void withWorldMetricLimits(Runnable action) {
        int before = METRIC_DEPTH.get();
        METRIC_DEPTH.set(before + 1);
        try {
            action.run();
        } finally {
            if (before == 0) METRIC_DEPTH.remove();
            else METRIC_DEPTH.set(before);
        }
    }

    private SimurailJointLimits() {}
}
