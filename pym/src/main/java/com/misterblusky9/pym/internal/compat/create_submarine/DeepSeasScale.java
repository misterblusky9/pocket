package com.misterblusky9.pym.internal.compat.create_submarine;

import com.misterblusky9.pym.api.InternalForces;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.create_submarine.mixin.HandleBodyAccess;
import com.misterblusky9.pym.internal.scale.ScaleRecord;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public final class DeepSeasScale {
    private static final ThreadLocal<Double> BLAST_SCALE = ThreadLocal.withInitial(() -> ScaleBounds.FULL);

    public static void internally(RigidBodyHandle handle, Runnable apply) {
        ServerSubLevel owner = handle == null ? null : owner(handle);
        if (owner == null) {
            apply.run();
            return;
        }
        try (InternalForces ignored = Pym.internalForces(owner)) {
            apply.run();
        }
    }

    public static double of(SubLevelAccess sub) {
        return sub instanceof SubLevel subLevel ? usable(Pym.scale().of(subLevel)) : ScaleBounds.FULL;
    }

    public static double of(UUID id) {
        if (id == null) return ScaleBounds.FULL;
        ScaleRecord record = ScaleState.recordOf(id);
        return usable(record != null ? record.current() : ScaleState.getClientScale(id));
    }

    public static boolean isFull(double scale) {
        return Math.abs(scale - ScaleBounds.FULL) <= ScaleBounds.EPSILON;
    }

    public static double immersion(double value, double offset, double span, double scale) {
        if (isFull(scale)) return value;
        return ((value * span - offset) / scale + offset) / span;
    }

    public static Object scaleBox(Object value, double scale) {
        if (isFull(scale) || !(value instanceof AABB box)) return value;
        double cx = (box.minX + box.maxX) * 0.5D;
        double cy = (box.minY + box.maxY) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        double hx = (box.maxX - box.minX) * 0.5D * scale;
        double hy = (box.maxY - box.minY) * 0.5D * scale;
        double hz = (box.maxZ - box.minZ) * 0.5D * scale;
        return new AABB(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    public static void blastAt(double scale, Runnable blast) {
        BLAST_SCALE.set(usable(scale));
        try {
            blast.run();
        } finally {
            BLAST_SCALE.remove();
        }
    }

    public static double blastScale() {
        return BLAST_SCALE.get();
    }

    private static ServerSubLevel owner(RigidBodyHandle handle) {
        return ((HandleBodyAccess) handle).pym$deepSeasBody() instanceof ServerSubLevel subLevel && !subLevel.isRemoved()
                ? subLevel
                : null;
    }

    private static double usable(double scale) {
        return ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    private DeepSeasScale() {
    }
}
