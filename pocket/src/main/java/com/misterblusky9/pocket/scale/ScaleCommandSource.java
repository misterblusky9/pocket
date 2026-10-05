package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleDriver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public interface ScaleCommandSource extends ScaleDriver {
    @org.jetbrains.annotations.Nullable
    default CompressionStage commandedStage() { return null; }

    @Override
    default double commandedScale() {
        final CompressionStage stage = commandedStage();
        return stage == null ? NO_COMMAND : stage.scale();
    }

    @Override
    default double nextStage(final double from, final double requested) {
        return ScaleLadder.stepToward(from, requested);
    }

    default boolean tryConsumeTransition(
            final ServerSubLevel subLevel,
            final CompressionStage from,
            final CompressionStage to
    ) { return true; }

    @Override
    default boolean tryBeginTransition(
            final ServerSubLevel subLevel,
            final double from,
            final double to
    ) {
        return tryConsumeTransition(subLevel, CompressionStage.nearest(from), CompressionStage.nearest(to));
    }

    default void onTransitionCompleted(
            final ServerSubLevel subLevel,
            final CompressionStage stage
    ) {}

    @Override
    default void onTransitionCompleted(
            final ServerSubLevel subLevel,
            final double scale
    ) {
        onTransitionCompleted(subLevel, CompressionStage.nearest(scale));
    }
}
