package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.scale.Resizer;
import com.misterblusky9.pym.internal.scale.ScalePhysicsMode;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ResizeRequest {
    private final ServerSubLevel subLevel;
    private double scale = Double.NaN;
    private Vector3d anchor;
    private ScaleBounds bounds = ScaleBounds.ANY;
    private boolean propagate = true;
    private boolean fast;
    private boolean immediate;
    private double ticks = Double.NaN;

    ResizeRequest(final ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public ResizeRequest scaleTo(final double scale) {
        this.scale = scale;
        return this;
    }

    public ResizeRequest anchor(final Vector3dc localPoint) {
        this.anchor = localPoint == null ? null : new Vector3d(localPoint);
        return this;
    }

    public ResizeRequest bounds(final ScaleBounds bounds) {
        this.bounds = bounds == null ? ScaleBounds.ANY : bounds;
        return this;
    }

    public ResizeRequest propagate(final boolean propagate) {
        this.propagate = propagate;
        return this;
    }

    public ResizeRequest fast() {
        this.fast = true;
        return this;
    }

    public ResizeRequest ticks(final double ticks) {
        this.ticks = Double.isFinite(ticks) && ticks >= 0.0D ? ticks : Double.NaN;
        return this;
    }

    @Deprecated(forRemoval = false)
    public ResizeRequest unsupported() {
        return this;
    }

    public ResizeRequest immediate() {
        this.immediate = true;
        return this;
    }

    public ResizeResult submit() {
        if (this.immediate) return Resizer.adopt(this.subLevel, this.scale);
        return Resizer.submit(this.toRequest());
    }

    public ResizeResult check() {
        if (this.immediate) {
            if (this.subLevel == null || this.subLevel.isRemoved()) return ResizeResult.refused(ResizeResult.Status.UNAVAILABLE);
            return ScaleBounds.isValid(this.scale)
                    ? ResizeResult.accepted(this.scale)
                    : ResizeResult.refused(ResizeResult.Status.INVALID_SCALE);
        }
        return Resizer.check(this.toRequest());
    }

    private Resizer.Request toRequest() {
        return new Resizer.Request(
                this.subLevel,
                this.scale,
                this.anchor,
                this.bounds,
                this.propagate,
                this.fast ? ScalePhysicsMode.FAST : ScalePhysicsMode.TRACKING,
                this.ticks,
                null);
    }
}
