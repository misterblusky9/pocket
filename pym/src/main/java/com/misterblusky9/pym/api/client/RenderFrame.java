package com.misterblusky9.pym.api.client;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

public final class RenderFrame {
    private static final double PADDING = 2.0D;

    private static long frame;
    private static Frustum frustum;
    private static long frustumFrame = -1L;

    public static long frame() {
        return frame;
    }

    public static boolean mayBeVisible(final SubLevel subLevel) {
        if (frustum == null || frustumFrame != frame) return true;
        if (!(subLevel instanceof final ClientSubLevel client) || client.isRemoved()) return true;
        final BoundingBox3dc box = client.boundingBox();
        if (box == null) return true;
        return frustum.isVisible(new AABB(
                box.minX() - PADDING, box.minY() - PADDING, box.minZ() - PADDING,
                box.maxX() + PADDING, box.maxY() + PADDING, box.maxZ() + PADDING));
    }

    public static void begin() {
        frame++;
    }

    public static void capture(final Frustum captured) {
        frustum = captured;
        frustumFrame = frame;
    }

    private RenderFrame() {}
}
