package com.misterblusky9.pym.api.client;

import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.companion.math.Pose3dc;

public record TaperedLinkProfile(double startRadius, double endRadius, double length) {
    public static final TaperedLinkProfile UNIFORM = new TaperedLinkProfile(1.0D, 1.0D, 1.0D);
    // disabled for now
    public static final boolean ENABLED = false;

    public static TaperedLinkProfile between(
            ClientSubLevel owner, ClientSubLevel partner, double length
    ) {
        if (!ENABLED) return UNIFORM;
        double ratio = scaleOf(partner) / scaleOf(owner);
        return of(1.0D, ratio, length);
    }

    public static TaperedLinkProfile of(double start, double end, double length) {
        if (!(start > 0.0D) || !(end > 0.0D)
                || !Double.isFinite(start) || !Double.isFinite(end)
                || !(length > 1.0E-6D) || !Double.isFinite(length)) return UNIFORM;
        return start == 1.0D && end == 1.0D ? UNIFORM : new TaperedLinkProfile(start, end, length);
    }

    public double radiusAtV(float v) {
        return radiusAt(Math.abs((double) v) / this.length);
    }

    public double radiusAt(double progress) {
        double t = Double.isFinite(progress) ? Math.clamp(progress, 0.0D, 1.0D) : 0.0D;
        return this.startRadius + (this.endRadius - this.startRadius) * t;
    }

    private static double scaleOf(ClientSubLevel subLevel) {
        if (subLevel == null) return 1.0D;
        Pose3dc pose = subLevel.renderPose();
        if (pose == null) return 1.0D;
        var scale = pose.scale();
        double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D
                && Math.abs(uniform - scale.y()) <= ScaleBounds.EPSILON
                && Math.abs(uniform - scale.z()) <= ScaleBounds.EPSILON ? uniform : 1.0D;
    }
}
