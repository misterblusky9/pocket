package com.misterblusky9.pym.internal.client;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PortraitScope {
    private static final int MAX_DEPTH = 4;
    private static final Deque<Frame> FRAMES = new ArrayDeque<>();

    private record Frame(ClientSubLevel subLevel, Pose3d pose, double visualScale) {}

    public static boolean isDrawing(final ClientSubLevel subLevel) {
        final Frame frame = FRAMES.peek();
        return frame != null && frame.subLevel() == subLevel;
    }

    public static boolean active() {
        return !FRAMES.isEmpty();
    }

    public static boolean full() {
        return FRAMES.size() >= MAX_DEPTH;
    }

    public static Pose3dc pose() {
        return FRAMES.element().pose();
    }

    public static double visualScale() {
        return FRAMES.element().visualScale();
    }

    public static void enter(final ClientSubLevel subLevel, final Vector3dc origin, final Matrix4fc modelView) {
        final Pose3d pose = new Pose3d();
        pose.scale().set(1.0D, 1.0D, 1.0D);
        pose.rotationPoint().set(origin);

        final double volume = Math.abs(modelView.determinant3x3());
        FRAMES.push(new Frame(subLevel, pose, volume > 0.0D ? Math.cbrt(volume) : 1.0D));
    }

    public static void exit() {
        FRAMES.pop();
    }

    private PortraitScope() {}
}
