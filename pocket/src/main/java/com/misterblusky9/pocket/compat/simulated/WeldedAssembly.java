package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.Set;
import java.util.UUID;

public final class WeldedAssembly {
    public static Set<UUID> members(final SubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return Set.of();

        final SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        return container == null
                ? Set.of(subLevel.getUniqueId())
                : CrossScaleWelds.componentIds(container, subLevel.getUniqueId());
    }

    public static double coarsestScale(final SubLevel subLevel) {
        final Set<UUID> members = members(subLevel);
        if (members.size() <= 1) return scaleOf(subLevel);

        final SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return scaleOf(subLevel);

        double coarsest = 0.0D;
        for (final UUID id : members) {
            final SubLevel member = container.getSubLevel(id);
            if (member == null || member.isRemoved()) continue;
            coarsest = Math.max(coarsest, scaleOf(member));
        }
        return coarsest > 0.0D ? coarsest : scaleOf(subLevel);
    }

    public static double extent(final SubLevel subLevel) {
        if (subLevel == null) return 0.0D;

        final Set<UUID> members = members(subLevel);
        if (members.size() <= 1) return extentOf(subLevel.boundingBox());

        final SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return extentOf(subLevel.boundingBox());

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (final UUID id : members) {
            final SubLevel member = container.getSubLevel(id);
            if (member == null || member.isRemoved()) continue;

            final BoundingBox3dc box = member.boundingBox();
            if (box == null) continue;

            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }

        if (minX > maxX) return extentOf(subLevel.boundingBox());
        return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
    }

    private static double scaleOf(final SubLevel subLevel) {
        final double scale = Pym.scale().of(subLevel);
        return ScaleBounds.isValid(scale) ? ScaleBounds.clampValid(scale) : 1.0D;
    }

    private static double extentOf(final BoundingBox3dc box) {
        if (box == null) return 0.0D;
        return Math.max(
                box.maxX() - box.minX(),
                Math.max(box.maxY() - box.minY(), box.maxZ() - box.minZ()));
    }

    private WeldedAssembly() {}
}
