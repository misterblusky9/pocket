package com.misterblusky9.pocket.scale;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

public interface ScaleCommandSource {
    double NO_COMMAND = Double.NaN;

    @org.jetbrains.annotations.Nullable
    default CompressionStage commandedStage() { return null; }

    default double commandedScale() {
        final CompressionStage stage = commandedStage();
        return stage == null ? NO_COMMAND : stage.scale();
    }

    default boolean stepwiseTransitions() { return false; }

    default boolean yieldsToManualOverride() { return true; }

    default double transitionSpeedFactor() { return 1.0D; }

    default ScaleLimits scaleLimits() { return ScaleLimits.API; }

    default Vector3d anchorLocalPoint() { return null; }

    default boolean tryConsumeTransition(
            final ServerSubLevel subLevel,
            final CompressionStage from,
            final CompressionStage to
    ) { return true; }

    default boolean tryConsumeTransition(
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

    default void onTransitionCompleted(
            final ServerSubLevel subLevel,
            final double scale
    ) {
        onTransitionCompleted(subLevel, CompressionStage.nearest(scale));
    }

    default void setJamMessage(final String message) {}
    default void clearJamMessage() {}

    boolean isRemoved();
}
