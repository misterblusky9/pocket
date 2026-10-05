package com.misterblusky9.pym.api;

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

    public static ResizeResult accepted(final double scale) {
        return new ResizeResult(Status.ACCEPTED, scale, null);
    }

    public static ResizeResult refused(final Status status) {
        return new ResizeResult(status, Double.NaN, null);
    }

    public static ResizeResult refused(final Status status, @Nullable final String message) {
        return new ResizeResult(status, Double.NaN, message);
    }

    public String describe() {
        return this.message == null ? this.status.name() : this.status.name() + ": " + this.message;
    }
}
