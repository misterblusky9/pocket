package com.misterblusky9.pocket.scale;

import com.misterblusky9.pym.api.ScaleBounds;

public enum CompressionStage {
    NORMAL(0, 1.0D, "1×"),
    HALF(1, 0.5D, "1/2×"),
    QUARTER(2, 0.25D, "1/4×"),
    EIGHTH(3, 0.125D, "1/8×"),
    SIXTEENTH(4, 0.0625D, "1/16×");

    private final int depth;
    private final double scale;
    private final String label;

    private static final double SNAP_TOLERANCE = 1.0E-6D;

    CompressionStage(int depth, double scale, String label) {
        this.depth = depth;
        this.scale = scale;
        this.label = label;
    }

    public int depth() { return this.depth; }
    public double scale() { return this.scale; }
    public String label() { return this.label; }

    public boolean isCompressed() { return this != NORMAL; }
    public boolean isDeeperThan(CompressionStage other) { return this.depth > other.depth; }

    public CompressionStage stepToward(CompressionStage target) {
        if (target == null || target == this) return this;
        int next = this.depth + Integer.signum(target.depth - this.depth);
        return values()[Math.max(0, Math.min(values().length - 1, next))];
    }

    public static CompressionStage fromDepth(int depth) {
        return values()[Math.max(0, Math.min(values().length - 1, depth))];
    }

    public CompressionStage cycle(int steps) {
        CompressionStage[] all = values();
        int next = Math.floorMod(this.depth + steps, all.length);
        return all[next];
    }

    public static CompressionStage nearest(double scale) {
        double target = Double.isNaN(scale)
                ? ScaleBounds.FULL
                : Math.max(SIXTEENTH.scale, Math.min(NORMAL.scale, scale));
        CompressionStage best = NORMAL;
        double bestError = Double.MAX_VALUE;
        for (CompressionStage stage : values()) {
            double error = Math.abs(stage.scale - target);
            if (error < bestError) {
                best = stage;
                bestError = error;
            }
        }
        return best;
    }

    public static CompressionStage exact(double scale) {
        for (CompressionStage stage : values()) {
            if (Math.abs(stage.scale - scale) <= SNAP_TOLERANCE) return stage;
        }
        return null;
    }

    public static double snap(double scale) {
        if (!ScaleBounds.isValid(scale)) return scale;
        CompressionStage stage = exact(scale);
        return stage == null ? scale : stage.scale;
    }

    @Deprecated(forRemoval = false)
    public static double stepToward(double from, double to) {
        return ScaleLadder.stepToward(from, to);
    }

    public static CompressionStage deepestForRpm(float rawRpm) {
        float rpm = Math.abs(rawRpm);
        if (rpm >= 128.0F) return SIXTEENTH;
        if (rpm >= 64.0F) return EIGHTH;
        if (rpm >= 32.0F) return QUARTER;
        if (rpm >= 16.0F) return HALF;
        return NORMAL;
    }
}
