package com.misterblusky9.pym.api;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public interface ScaleDriver {
    double NO_COMMAND = Double.NaN;

    double commandedScale();

    boolean isRemoved();

    default boolean stepwiseTransitions() { return false; }

    default double nextStage(final double from, final double requested) { return requested; }

    default boolean yieldsToManualOverride() { return true; }

    default boolean propagatesJoints() { return false; }

    default double transitionTicks() { return Double.NaN; }

    default ScaleBounds bounds() { return ScaleBounds.ANY; }

    @Deprecated(forRemoval = false)
    default boolean allowsUnsupported() { return true; }

    @Nullable
    default Vector3d anchorLocalPoint() { return null; }

    default boolean tryBeginTransition(final ServerSubLevel subLevel, final double from, final double to) {
        return true;
    }

    @Nullable
    default String transitionRefusedMessage() { return null; }

    default void onTransitionCompleted(final ServerSubLevel subLevel, final double scale) {}

    default void setJamMessage(final String message) {}

    default void clearJamMessage() {}
}
