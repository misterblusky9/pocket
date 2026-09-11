package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.physics.RapierBridge;
import com.misterblusky9.pocket.physics.ScaledMassData;
import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WeldedAssembly {
    private static final double MAX_MASS_RATIO = 64.0D;

    private static final Map<UUID, Double> APPLIED_FLOORS = new HashMap<>();

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

    public static double solverFloor(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return 0.0D;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return 0.0D;

        final Set<UUID> members = CrossScaleWelds.componentIds(container, subLevel.getUniqueId());
        if (members.size() <= 1) return 0.0D;

        double heaviest = 0.0D;
        for (final UUID id : members) {
            if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                continue;
            }
            heaviest = Math.max(heaviest, similarityMass(member));
        }
        return heaviest > 0.0D ? heaviest / MAX_MASS_RATIO : 0.0D;
    }

    public static void tick(final ServerSubLevelContainer container) {
        if (container == null) return;

        final Set<UUID> welded = CrossScaleWelds.snapshot(container).endpointIds();
        if (welded.isEmpty() && APPLIED_FLOORS.isEmpty()) return;

        for (final UUID id : welded) {
            if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                continue;
            }
            apply(member, solverFloor(member));
        }

        final Iterator<Map.Entry<UUID, Double>> stale = APPLIED_FLOORS.entrySet().iterator();
        while (stale.hasNext()) {
            final UUID id = stale.next().getKey();
            if (welded.contains(id)) continue;

            stale.remove();
            if (container.getSubLevel(id) instanceof final ServerSubLevel member && !member.isRemoved()) {
                RapierBridge.syncMassProperties(member, ScaleState.getServerScale(member), false);
            }
        }
    }

    public static void invalidate() {
        APPLIED_FLOORS.clear();
    }

    private static void apply(final ServerSubLevel member, final double floor) {
        final Double previous = APPLIED_FLOORS.put(member.getUniqueId(), floor);
        if (previous != null && Math.abs(previous - floor) <= PocketSized.EPSILON) return;

        RapierBridge.syncMassProperties(member, ScaleState.getServerScale(member), false);
    }

    private static double similarityMass(final ServerSubLevel member) {
        final MassData tracker = member.getMassTracker();
        return tracker == null ? 0.0D : ScaledMassData.similarityMass(tracker.getMass(), scaleOf(member));
    }

    private static double scaleOf(final SubLevel subLevel) {
        final double scale = ScaleState.getScale(subLevel);
        return PocketSized.isValidScale(scale) ? PocketSized.clampScale(scale) : 1.0D;
    }

    private static double extentOf(final BoundingBox3dc box) {
        if (box == null) return 0.0D;
        return Math.max(
                box.maxX() - box.minX(),
                Math.max(box.maxY() - box.minY(), box.maxZ() - box.minZ()));
    }

    private WeldedAssembly() {}
}
