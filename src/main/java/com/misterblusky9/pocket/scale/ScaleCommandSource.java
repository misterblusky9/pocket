package com.misterblusky9.pocket.scale;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;

public interface ScaleCommandSource {
    @org.jetbrains.annotations.Nullable
    CompressionStage commandedStage();

    default boolean stepwiseTransitions() { return false; }

    default boolean yieldsToManualOverride() { return true; }

    default double transitionSpeedFactor() { return 1.0D; }

    default Vector3d anchorLocalPoint() { return null; }

    default boolean tryConsumeTransition(
            final ServerSubLevel subLevel,
            final CompressionStage from,
            final CompressionStage to
    ) { return true; }

    default void onTransitionCompleted(
            final ServerSubLevel subLevel,
            final CompressionStage stage
    ) {}

    default void setJamMessage(final String message) {}
    default void clearJamMessage() {}

    boolean isRemoved();
}
