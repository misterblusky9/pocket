package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PhysicsStaffScale {
    private static final double PYM$HOLD_CLEARANCE = 0.25D;

    private static final double PYM$ABSOLUTE_MIN_HOLD = 0.1D;

    private static final double PYM$HALF_DIAGONAL = 0.8660254037844386D;

    private static SubLevel pickupTarget;

    public static void beginPickup(SubLevel subLevel) {
        pickupTarget = subLevel;
    }

    public static void endPickup() {
        pickupTarget = null;
    }

    public static double scrollFrame() {
        double player = playerScale();
        SubLevel target = dragTarget();
        if (target == null) {
            return player;
        }

        double scale = coarsestScale(target);
        return scale > 0.0D ? Math.min(scale, player) : player;
    }

    public static double minHoldDistance(double vanillaMin, double max) {
        double player = playerScale();
        SubLevel target = dragTarget();
        if (target == null) {
            return Math.min(max, vanillaMin * player);
        }

        double largest = extent(target);
        if (!(largest > 0.0D)) {
            return Math.min(max, vanillaMin * player);
        }

        double radius = largest * PYM$HALF_DIAGONAL;
        return Math.min(max, Math.max(PYM$ABSOLUTE_MIN_HOLD * player, radius + PYM$HOLD_CLEARANCE * player));
    }

    private static double playerScale() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 1.0D;
        }

        double scale = Pym.entities().scaleOf(player);
        return scale > 0.0D && Double.isFinite(scale) ? scale : 1.0D;
    }

    private static SubLevel dragTarget() {
        if (pickupTarget != null) {
            return pickupTarget;
        }

        PhysicsStaffClientHandler handler = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER;
        if (handler == null) {
            return null;
        }

        PhysicsStaffClientHandler.ClientDragSession session = handler.getDragSession();
        return session == null ? null : session.dragSubLevel();
    }

    private static double coarsestScale(SubLevel target) {
        double coarsest = 0.0D;
        for (SubLevel member : group(target)) coarsest = Math.max(coarsest, Pym.scale().of(member));
        return coarsest > 0.0D ? coarsest : Pym.scale().of(target);
    }

    private static double extent(SubLevel target) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (SubLevel member : group(target)) {
            BoundingBox3dc box = member.boundingBox();
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

    private static List<SubLevel> group(SubLevel target) {
        SubLevelContainer container = SubLevelContainer.getContainer(target.getLevel());
        List<SubLevel> members = new ArrayList<>();
        for (UUID id : Pym.connections().coupled(target)) {
            SubLevel member = container == null ? null : container.getSubLevel(id);
            if (member != null && !member.isRemoved()) members.add(member);
        }
        if (members.isEmpty()) members.add(target);
        return members;
    }

    private PhysicsStaffScale() {}
}
