package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.scale.ScaleState;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;

public final class PhysicsStaffScale {
    private static final double POCKET$HOLD_CLEARANCE = 0.25D;

    private static final double POCKET$ABSOLUTE_MIN_HOLD = 0.1D;

    private static final double POCKET$HALF_DIAGONAL = 0.8660254037844386D;

    private static SubLevel pickupTarget;

    public static void beginPickup(final SubLevel subLevel) {
        pickupTarget = subLevel;
    }

    public static void endPickup() {
        pickupTarget = null;
    }

    public static double dragScale() {
        if (!(dragTarget() instanceof final ClientSubLevel clientSubLevel)) {
            return 1.0D;
        }

        final double scale = ScaleState.getClientScale(clientSubLevel);
        return scale > 0.0D && scale < 1.0D ? scale : 1.0D;
    }

    public static double minHoldDistance(final double vanillaMin) {
        final SubLevel target = dragTarget();
        if (target == null) {
            return vanillaMin;
        }

        final BoundingBox3dc bounds = target.boundingBox();
        final double sizeX = bounds.maxX() - bounds.minX();
        final double sizeY = bounds.maxY() - bounds.minY();
        final double sizeZ = bounds.maxZ() - bounds.minZ();
        final double largest = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (!(largest > 0.0D)) {
            return vanillaMin;
        }

        final double radius = largest * POCKET$HALF_DIAGONAL;
        return Math.max(POCKET$ABSOLUTE_MIN_HOLD, Math.min(vanillaMin, radius + POCKET$HOLD_CLEARANCE));
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

    private PhysicsStaffScale() {}
}
