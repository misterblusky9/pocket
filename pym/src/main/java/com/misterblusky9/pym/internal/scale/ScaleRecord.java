package com.misterblusky9.pym.internal.scale;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.ScaleDriver;

public final class ScaleRecord {
    private double current;
    private double target;
    private double start;
    private int elapsed;
    private double duration;
    private Pivot pivot;
    private ScaleDriver driver;
    private boolean alone;
    private boolean dirty;

    ScaleRecord(final double scale) {
        this.current = scale;
        this.target = scale;
        this.start = scale;
    }

    public double current() { return this.current; }
    public double target() { return this.target; }
    public boolean moving() { return !ScaleBounds.same(this.current, this.target); }

    public double settled() { return moving() ? this.start : this.current; }

    Pivot pivot() { return this.pivot; }
    ScaleDriver driver() { return this.driver; }
    boolean alone() { return this.alone; }
    boolean dirty() { return this.dirty; }
    void markPersisted() { this.dirty = false; }

    void begin(
            final double goal,
            final double ticks,
            final Pivot pivot,
            final ScaleDriver driver,
            final boolean alone
    ) {
        this.start = this.current;
        this.target = goal;
        this.elapsed = 0;
        this.duration = Math.max(1.0D, ticks);
        this.pivot = pivot;
        this.driver = driver;
        this.alone = alone;
        this.dirty = true;
    }

    double step() {
        this.elapsed++;
        if (this.elapsed >= this.duration) return this.target;
        final double t = this.elapsed / this.duration;
        final double eased = 1.0D - (1.0D - t) * (1.0D - t);
        return this.start + (this.target - this.start) * eased;
    }

    void show(final double scale) {
        this.current = scale;
    }

    void settle(final double scale) {
        this.current = scale;
        this.target = scale;
        this.start = scale;
        this.pivot = null;
        this.driver = null;
        this.dirty = true;
    }
}
