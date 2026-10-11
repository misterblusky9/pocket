package com.misterblusky9.pym.internal.collision;

import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;

import java.util.List;

public final class PlotScanBounds {
    public static Iterable<BlockPos> betweenClosed(
            final SubLevel subLevel,
            final int minX,
            final int minY,
            final int minZ,
            final int maxX,
            final int maxY,
            final int maxZ
    ) {
        final BoundingBox3ic plot = plotBounds(subLevel);
        if (plot == null) return BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ);

        final int x0 = Math.max(minX, plot.minX());
        final int y0 = Math.max(minY, plot.minY());
        final int z0 = Math.max(minZ, plot.minZ());
        final int x1 = Math.min(maxX, plot.maxX());
        final int y1 = Math.min(maxY, plot.maxY());
        final int z1 = Math.min(maxZ, plot.maxZ());
        if (x0 > x1 || y0 > y1 || z0 > z1) return List.of();
        return BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1);
    }

    public static void clampCells(final SubLevel subLevel, final BoundingBox3d local) {
        final BoundingBox3ic plot = plotBounds(subLevel);
        if (plot == null) return;

        final double x0 = Math.max(local.minX, plot.minX());
        final double y0 = Math.max(local.minY, plot.minY());
        final double z0 = Math.max(local.minZ, plot.minZ());
        final double x1 = Math.min(local.maxX, plot.maxX() + 1.0);
        final double y1 = Math.min(local.maxY, plot.maxY() + 1.0);
        final double z1 = Math.min(local.maxZ, plot.maxZ() + 1.0);
        if (x0 > x1 || y0 > y1 || z0 > z1) {
            local.set(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            return;
        }
        local.set(x0, y0, z0, x1, y1, z1);
    }

    private static BoundingBox3ic plotBounds(final SubLevel subLevel) {
        if (subLevel == null || subLevel.getPlot() == null) return null;
        return subLevel.getPlot().getBoundingBox();
    }

    private PlotScanBounds() {}
}
