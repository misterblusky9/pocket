package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleDriver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public interface ScaleCommandSource extends ScaleDriver {
    @org.jetbrains.annotations.Nullable
    default CompressionStage commandedStage() { return null; }

    @Override
    default double commandedScale() {
        CompressionStage stage = commandedStage();
        return stage == null ? NO_COMMAND : stage.scale();
    }

    @Override
    default double nextStage(double from, double requested) {
        return ScaleLadder.stepToward(from, requested);
    }

    default boolean tryConsumeTransition(
            ServerSubLevel subLevel,
            CompressionStage from,
            CompressionStage to
    ) { return true; }

    @Override
    default boolean tryBeginTransition(
            ServerSubLevel subLevel,
            double from,
            double to
    ) {
        return tryConsumeTransition(subLevel, CompressionStage.nearest(from), CompressionStage.nearest(to));
    }

    default void onTransitionCompleted(
            ServerSubLevel subLevel,
            CompressionStage stage
    ) {}

    @Override
    default void onTransitionCompleted(
            ServerSubLevel subLevel,
            double scale
    ) {
        onTransitionCompleted(subLevel, CompressionStage.nearest(scale));
    }
}
