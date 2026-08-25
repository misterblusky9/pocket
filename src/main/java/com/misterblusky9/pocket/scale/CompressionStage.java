package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.PocketSized;

public enum CompressionStage {
    NORMAL(0, 1.0D, "1×"),
    HALF(1, 0.5D, "1/2×"),
    QUARTER(2, 0.25D, "1/4×"),
    EIGHTH(3, 0.125D, "1/8×"),
    SIXTEENTH(4, 0.0625D, "1/16×");

    private final int depth;
    private final double scale;
    private final String label;

    CompressionStage(final int depth, final double scale, final String label) {
        this.depth = depth;
        this.scale = scale;
        this.label = label;
    }

    public int depth() { return this.depth; }
    public double scale() { return this.scale; }
    public String label() { return this.label; }

    public boolean isCompressed() { return this != NORMAL; }
    public boolean isDeeperThan(final CompressionStage other) { return this.depth > other.depth; }

    public CompressionStage stepToward(final CompressionStage target) {
        if (target == null || target == this) return this;
        final int next = this.depth + Integer.signum(target.depth - this.depth);
        return values()[Math.max(0, Math.min(values().length - 1, next))];
    }

    public static CompressionStage fromDepth(final int depth) {
        return values()[Math.max(0, Math.min(values().length - 1, depth))];
    }

    public CompressionStage cycle(final int steps) {
        final CompressionStage[] all = values();
        final int next = Math.floorMod(this.depth + steps, all.length);
        return all[next];
    }

    public static CompressionStage nearest(final double scale) {
        final double clamped = PocketSized.clampScale(scale);
        CompressionStage best = NORMAL;
        double bestError = Double.MAX_VALUE;
        for (final CompressionStage stage : values()) {
            final double error = Math.abs(stage.scale - clamped);
            if (error < bestError) {
                best = stage;
                bestError = error;
            }
        }
        return best;
    }

    public static CompressionStage deepestForRpm(final float rawRpm) {
        final float rpm = Math.abs(rawRpm);
        if (rpm >= 128.0F) return SIXTEENTH;
        if (rpm >= 64.0F) return EIGHTH;
        if (rpm >= 32.0F) return QUARTER;
        if (rpm >= 16.0F) return HALF;
        return NORMAL;
    }
}
