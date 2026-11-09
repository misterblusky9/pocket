package com.misterblusky9.pym.api;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public record ResizeResult(Status status, double scale, @Nullable String message) {
    public enum Status {
        ACCEPTED,
        INVALID_SCALE,
        UNAVAILABLE,
        OUT_OF_BOUNDS,
        @Deprecated(forRemoval = false)
        LIMITED,
        @Deprecated(forRemoval = false)
        UNCONFIRMED,
        BLOCKED
    }

    public boolean accepted() {
        return this.status == Status.ACCEPTED;
    }

    public static ResizeResult accepted(double scale) {
        return new ResizeResult(Status.ACCEPTED, scale, null);
    }

    public static ResizeResult refused(Status status) {
        return new ResizeResult(status, Double.NaN, null);
    }

    public static ResizeResult refused(Status status, @Nullable String message) {
        return new ResizeResult(status, Double.NaN, message);
    }

    public Component describeComponent() {
        return this.message == null
                ? Component.literal(this.status.name())
                : Component.literal(this.status.name() + ": ").append(RefusalText.component(this.message));
    }

    public String describe() {
        return describeComponent().getString();
    }
}
