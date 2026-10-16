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
    private static final double MAX_INERTIA_RATIO = 16.0D;

    private static final Map<UUID, Floors> APPLIED_FLOORS = new HashMap<>();

    private record Floors(double mass, double inertia) {}

    public static double solverFloor(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return 0.0D;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return 0.0D;

        Set<UUID> members = PymExtensions.massCouplingGraph(container).component(subLevel.getUniqueId());
        if (members.size() <= 1) return 0.0D;

        double heaviest = 0.0D;
        for (UUID id : members) {
            if (!(container.getSubLevel(id) instanceof ServerSubLevel member) || member.isRemoved()) {
                continue;
            }
            heaviest = Math.max(heaviest, similarityMass(member));
        }
        return heaviest > 0.0D ? heaviest / MAX_MASS_RATIO : 0.0D;
    }

    public static double solverInertiaFloor(ServerSubLevel subLevel) {
        if (subLevel == null || subLevel.isRemoved() || subLevel.getUniqueId() == null) return 0.0D;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return 0.0D;

        Set<UUID> members = PymExtensions.massCouplingGraph(container).component(subLevel.getUniqueId());
        if (members.size() <= 1) return 0.0D;

        double largest = 0.0D;
        for (UUID id : members) {
            if (!(container.getSubLevel(id) instanceof ServerSubLevel member) || member.isRemoved()) continue;
            MassData tracker = member.getMassTracker();
            if (tracker == null || tracker.getInertiaTensor() == null) continue;

            var inertia = tracker.getInertiaTensor();
            double scale = scaleOf(member);
            double mean = (inertia.m00() + inertia.m11() + inertia.m22()) / 3.0D;
            double effective = mean * scale * scale * scale * scale * scale;
            if (Double.isFinite(effective)) largest = Math.max(largest, effective);
        }
        return largest / MAX_INERTIA_RATIO;
    }

    public static void tick(ServerSubLevelContainer container) {
        if (container == null) return;

        ScaleCoupling.Graph graph = PymExtensions.massCouplingGraph(container);
        Set<UUID> coupled = graph.members();
        if (coupled.isEmpty() && APPLIED_FLOORS.isEmpty()) return;

        for (UUID id : coupled) {
            if (!(container.getSubLevel(id) instanceof ServerSubLevel member) || member.isRemoved()) {
                continue;
            }
            apply(member, new Floors(solverFloor(member), solverInertiaFloor(member)));
        }

        Iterator<Map.Entry<UUID, Floors>> stale = APPLIED_FLOORS.entrySet().iterator();
        while (stale.hasNext()) {
            UUID id = stale.next().getKey();
            if (coupled.contains(id)) continue;

            stale.remove();
            if (container.getSubLevel(id) instanceof ServerSubLevel member && !member.isRemoved()) {
                RapierBridge.syncMassProperties(member, ScaleState.getServerScale(member), false);
            }
        }
    }

    public static void invalidate() {
        APPLIED_FLOORS.clear();
    }

    private static void apply(ServerSubLevel member, Floors floors) {
        Floors previous = APPLIED_FLOORS.put(member.getUniqueId(), floors);
        if (previous != null
                && Math.abs(previous.mass() - floors.mass()) <= ScaleBounds.EPSILON
                && Math.abs(previous.inertia() - floors.inertia()) <= ScaleBounds.EPSILON) return;

        RapierBridge.syncMassProperties(member, ScaleState.getServerScale(member), false);
    }

    private static double similarityMass(ServerSubLevel member) {
        MassData tracker = member.getMassTracker();
        return tracker == null ? 0.0D : ScaledMassData.similarityMass(tracker.getMass(), scaleOf(member));
    }

    private static double scaleOf(SubLevel subLevel) {
        double scale = ScaleState.getScale(subLevel);
        return ScaleBounds.isValid(scale) ? ScaleBounds.clampValid(scale) : 1.0D;
    }

    private CoupledMass() {}
}
