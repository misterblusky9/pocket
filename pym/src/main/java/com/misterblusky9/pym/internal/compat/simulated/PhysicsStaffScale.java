package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PhysicsStaffScale {
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
        double frame = playerScale();
        SubLevel target = dragTarget();
        if (target != null) frame = Math.max(frame, coarsestScale(target));
        return Math.min(max, vanillaMin * frame);
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
