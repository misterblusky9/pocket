package com.misterblusky9.pocket.client;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

public final class PocketClientFrame {
    private static final double VISIBILITY_PADDING = 2.0D;

    private static long frame;

    private static int subLevelBlockEntityPassDepth;

    private static Frustum frustum;
    private static long frustumFrame = -1L;

    public static void beginFrame() {
        frame++;
        subLevelBlockEntityPassDepth = 0;
    }

    public static void beginSubLevelBlockEntityPass() {
        subLevelBlockEntityPassDepth++;
    }

    public static void endSubLevelBlockEntityPass() {
        if (subLevelBlockEntityPassDepth > 0) subLevelBlockEntityPassDepth--;
    }

    public static boolean isInSubLevelBlockEntityPass() {
        return subLevelBlockEntityPassDepth > 0;
    }

    public static long frame() {
        return frame;
    }

    public static void captureFrustum(final Frustum captured) {
        frustum = captured;
        frustumFrame = frame;
    }

    public static boolean isPotentiallyVisible(final SubLevel raw) {
        if (frustum == null || frustumFrame != frame) return true;
        if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) return true;

        final BoundingBox3dc box = subLevel.boundingBox();
        if (box == null) return true;

        return frustum.isVisible(new AABB(
                box.minX() - VISIBILITY_PADDING, box.minY() - VISIBILITY_PADDING, box.minZ() - VISIBILITY_PADDING,
                box.maxX() + VISIBILITY_PADDING, box.maxY() + VISIBILITY_PADDING, box.maxZ() + VISIBILITY_PADDING
        ));
    }

    private PocketClientFrame() {}
}
