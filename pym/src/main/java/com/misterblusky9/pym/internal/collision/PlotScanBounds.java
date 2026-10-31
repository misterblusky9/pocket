package com.misterblusky9.pym.internal.collision;

import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PlotScanBounds {
    public static Iterable<BlockPos> betweenClosed(
            SubLevel subLevel,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        BoundingBox3ic plot = plotBounds(subLevel);
        if (plot == null) return BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ);

        int x0 = Math.max(minX, plot.minX());
        int y0 = Math.max(minY, plot.minY());
        int z0 = Math.max(minZ, plot.minZ());
        int x1 = Math.min(maxX, plot.maxX());
        int y1 = Math.min(maxY, plot.maxY());
        int z1 = Math.min(maxZ, plot.maxZ());
        if (x0 > x1 || y0 > y1 || z0 > z1) return List.of();
        return BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1);
    }

    public static boolean clipCollisionSearchToPlot(SubLevel subLevel, BoundingBox3d local) {
        BoundingBox3ic plot = plotBounds(subLevel);
        if (plot == null || plot.volume() <= 0.0D) return false;

        double minX = Math.max(local.minX, plot.minX());
        double minY = Math.max(local.minY, plot.minY());
        double minZ = Math.max(local.minZ, plot.minZ());
        double maxX = Math.min(local.maxX, plot.maxX() + 1.0D);
        double maxY = Math.min(local.maxY, plot.maxY() + 1.0D);
        double maxZ = Math.min(local.maxZ, plot.maxZ() + 1.0D);

        if (minX > maxX || minY > maxY || minZ > maxZ) return false;
        local.set(minX, minY, minZ, maxX, maxY, maxZ);
        return true;
    }

    public static void clampCells(SubLevel subLevel, BoundingBox3d local) {
        BoundingBox3ic plot = plotBounds(subLevel);
        if (plot == null) return;

        double x0 = Math.max(local.minX, plot.minX());
        double y0 = Math.max(local.minY, plot.minY());
        double z0 = Math.max(local.minZ, plot.minZ());
        double x1 = Math.min(local.maxX, plot.maxX() + 1.0);
        double y1 = Math.min(local.maxY, plot.maxY() + 1.0);
        double z1 = Math.min(local.maxZ, plot.maxZ() + 1.0);
        if (x0 > x1 || y0 > y1 || z0 > z1) {
            local.set(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            return;
        }
        local.set(x0, y0, z0, x1, y1, z1);
    }

    @Nullable
    public static AABB clipEntityQuery(SubLevel subLevel, AABB query) {
        if (!ScaleState.isScaled(subLevel)) return query;

        BoundingBox3ic plot = plotBounds(subLevel);
        if (plot == null) return null;

        double padding = 1.0D;
        double minX = Math.max(query.minX, plot.minX() - padding);
        double minY = Math.max(query.minY, plot.minY() - padding);
        double minZ = Math.max(query.minZ, plot.minZ() - padding);
        double maxX = Math.min(query.maxX, plot.maxX() + 1.0D + padding);
        double maxY = Math.min(query.maxY, plot.maxY() + 1.0D + padding);
        double maxZ = Math.min(query.maxZ, plot.maxZ() + 1.0D + padding);

        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                || minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return null;
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static BoundingBox3ic plotBounds(SubLevel subLevel) {
        if (subLevel == null || subLevel.getPlot() == null) return null;
        return subLevel.getPlot().getBoundingBox();
    }

    private PlotScanBounds() {}
}
