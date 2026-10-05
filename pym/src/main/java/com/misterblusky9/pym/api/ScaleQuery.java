package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.physics.ScaleFrame;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ScaleQuery {
    static final ScaleQuery INSTANCE = new ScaleQuery();

    private ScaleQuery() {}

    public double of(final SubLevel subLevel) {
        return ScaleState.getScale(subLevel);
    }

    public double settled(final SubLevel subLevel) {
        return ScaleState.getSettledScale(subLevel);
    }

    public double target(final ServerSubLevel subLevel) {
        return ScaleState.getTargetScale(subLevel);
    }

    public double ofBody(final PhysicsPipelineBody body) {
        return ScaleFrame.scaleOf(body);
    }

    public double at(final Level level, final Vec3 point) {
        if (level == null || point == null) return ScaleBounds.FULL;
        return containerScale(Sable.HELPER.getContaining(level, point));
    }

    public double at(final Level level, final BlockPos pos) {
        if (level == null || pos == null) return ScaleBounds.FULL;
        return containerScale(Sable.HELPER.getContaining(level, pos));
    }

    public boolean isScaled(final SubLevel subLevel) {
        return ScaleState.isScaled(subLevel);
    }

    public boolean isSettled(final SubLevel subLevel) {
        return ScaleState.isSettled(subLevel);
    }

    public boolean isSettled(final UUID subLevelId) {
        return ScaleState.isSettled(subLevelId);
    }

    public boolean isAt(final SubLevel subLevel, final double scale) {
        return ScaleState.isAt(subLevel, scale);
    }

    private static double containerScale(final SubLevel subLevel) {
        return subLevel == null || subLevel.isRemoved() ? ScaleBounds.FULL : ScaleState.getScale(subLevel);
    }
}
