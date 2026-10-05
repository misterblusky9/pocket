package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class MergingGlueScaleGate {
    public enum Refusal {
        NONE(null),
        SCALE("Scales must match"),
        COUPLED("Already connected");

        private final String message;

        Refusal(final String message) {
            this.message = message;
        }

        public String message() {
            return this.message;
        }
    }

    private MergingGlueScaleGate() {}

    public static Refusal check(final Level level, final BlockPos firstPos, final BlockPos secondPos) {
        if (level == null || firstPos == null || secondPos == null) return Refusal.NONE;
        final SubLevel first = Sable.HELPER.getContaining(level, firstPos);
        final SubLevel second = Sable.HELPER.getContaining(level, secondPos);
        return check(level, first, second);
    }

    public static Refusal check(final Level level, final SubLevel first, final SubLevel second) {
        if (level == null || first == null || second == null || first == second) return Refusal.NONE;
        if (Math.abs(Pym.scale().of(first) - Pym.scale().of(second)) > ScaleBounds.EPSILON) {
            return Refusal.SCALE;
        }
        return Pym.connections().coupled(first).contains(second.getUniqueId()) ? Refusal.COUPLED : Refusal.NONE;
    }
}
