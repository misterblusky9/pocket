package com.misterblusky9.pym.internal.physics;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.api.spi.ScaleCoupling;
import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CoupledMass {
    private static final double MAX_MASS_RATIO = 64.0D;

    private static final Map<UUID, Double> APPLIED_FLOORS = new HashMap<>();

    public static double solverFloor(final ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return 0.0D;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return 0.0D;

        final Set<UUID> members = PymExtensions.couplingGraph(container).component(subLevel.getUniqueId());
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

        final ScaleCoupling.Graph graph = PymExtensions.couplingGraph(container);
        final Set<UUID> coupled = graph.members();
        if (coupled.isEmpty() && APPLIED_FLOORS.isEmpty()) return;

        for (final UUID id : coupled) {
            if (!(container.getSubLevel(id) instanceof final ServerSubLevel member) || member.isRemoved()) {
                continue;
            }
            apply(member, solverFloor(member));
        }

        final Iterator<Map.Entry<UUID, Double>> stale = APPLIED_FLOORS.entrySet().iterator();
        while (stale.hasNext()) {
            final UUID id = stale.next().getKey();
            if (coupled.contains(id)) continue;

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
        if (previous != null && Math.abs(previous - floor) <= ScaleBounds.EPSILON) return;

        RapierBridge.syncMassProperties(member, ScaleState.getServerScale(member), false);
    }

    private static double similarityMass(final ServerSubLevel member) {
        final MassData tracker = member.getMassTracker();
        return tracker == null ? 0.0D : ScaledMassData.similarityMass(tracker.getMass(), scaleOf(member));
    }

    private static double scaleOf(final SubLevel subLevel) {
        final double scale = ScaleState.getScale(subLevel);
        return ScaleBounds.isValid(scale) ? ScaleBounds.clampValid(scale) : 1.0D;
    }

    private CoupledMass() {}
}
