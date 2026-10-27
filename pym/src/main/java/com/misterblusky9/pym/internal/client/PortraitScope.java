package com.misterblusky9.pym.internal.client;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.joml.Matrix4fc;

public final class PortraitScope {
    private static final Pose3d POSE = new Pose3d();

    private static ClientSubLevel drawing;
    private static double visualScale = 1.0D;

    public static boolean isDrawing(final ClientSubLevel subLevel) {
        return drawing != null && drawing == subLevel;
    }

    public static Pose3dc pose() {
        return POSE;
    }

    public static double visualScale() {
        return visualScale;
    }

    public static void enter(final ClientSubLevel subLevel, final Matrix4fc modelView) {
        if (drawing != null) throw new IllegalStateException("Portraits do not nest");

        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        POSE.position().set(0.0D, 0.0D, 0.0D);
        POSE.orientation().identity();
        POSE.scale().set(1.0D, 1.0D, 1.0D);
        POSE.rotationPoint().set(
                (bounds.minX() + bounds.maxX() + 1) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1) * 0.5D);

        final double volume = Math.abs(modelView.determinant3x3());
        visualScale = volume > 0.0D ? Math.cbrt(volume) : 1.0D;
        drawing = subLevel;
    }

    public static void exit() {
        drawing = null;
        visualScale = 1.0D;
    }

    private PortraitScope() {}
}
