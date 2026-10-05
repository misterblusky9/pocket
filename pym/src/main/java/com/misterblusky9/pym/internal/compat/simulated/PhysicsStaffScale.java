package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PhysicsStaffScale {
    private static final double PYM$HOLD_CLEARANCE = 0.25D;

    private static final double PYM$ABSOLUTE_MIN_HOLD = 0.1D;

    private static final double PYM$HALF_DIAGONAL = 0.8660254037844386D;

    private static SubLevel pickupTarget;

    public static void beginPickup(final SubLevel subLevel) {
        pickupTarget = subLevel;
    }

    public static void endPickup() {
        pickupTarget = null;
    }

    public static double dragScale() {
        final SubLevel target = dragTarget();
        if (target == null) {
            return 1.0D;
        }

        final double scale = coarsestScale(target);
        return scale > 0.0D && scale < 1.0D ? scale : 1.0D;
    }

    public static double minHoldDistance(final double vanillaMin) {
        final SubLevel target = dragTarget();
        if (target == null) {
            return vanillaMin;
        }

        final double largest = extent(target);
        if (!(largest > 0.0D)) {
            return vanillaMin;
        }

        final double radius = largest * PYM$HALF_DIAGONAL;
        return Math.max(PYM$ABSOLUTE_MIN_HOLD, Math.min(vanillaMin, radius + PYM$HOLD_CLEARANCE));
    }

    private static SubLevel dragTarget() {
        if (pickupTarget != null) {
            return pickupTarget;
        }

        final PhysicsStaffClientHandler handler = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER;
        if (handler == null) {
            return null;
        }

        final PhysicsStaffClientHandler.ClientDragSession session = handler.getDragSession();
        return session == null ? null : session.dragSubLevel();
    }

    private static double coarsestScale(final SubLevel target) {
        double coarsest = 0.0D;
        for (final SubLevel member : group(target)) coarsest = Math.max(coarsest, Pym.scale().of(member));
        return coarsest > 0.0D ? coarsest : Pym.scale().of(target);
    }

    private static double extent(final SubLevel target) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (final SubLevel member : group(target)) {
            final BoundingBox3dc box = member.boundingBox();
            if (box == null) continue;
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        if (minX > maxX) return 0.0D;
        return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
    }

    private static List<SubLevel> group(final SubLevel target) {
        final SubLevelContainer container = SubLevelContainer.getContainer(target.getLevel());
        final List<SubLevel> members = new ArrayList<>();
        for (final UUID id : Pym.connections().coupled(target)) {
            final SubLevel member = container == null ? null : container.getSubLevel(id);
            if (member != null && !member.isRemoved()) members.add(member);
        }
        if (members.isEmpty()) members.add(target);
        return members;
    }

    private PhysicsStaffScale() {}
}
