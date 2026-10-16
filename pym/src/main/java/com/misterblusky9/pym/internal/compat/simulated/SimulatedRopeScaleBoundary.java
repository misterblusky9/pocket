package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.internal.scale.PlotBlockEntities;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

public final class SimulatedRopeScaleBoundary {
    public static boolean canConnect(
            Level level,
            RopeStrandHolderBehavior start,
            RopeStrandHolderBehavior end
    ) {
        if (level == null || start == null || end == null) return false;

        Double startScale = scaleAt(level, start.getAttachmentPoint());
        Double endScale = scaleAt(level, end.getAttachmentPoint());
        return startScale != null && endScale != null && sameScale(startScale, endScale);
    }

    public static boolean blocksTransition(ServerSubLevel subLevel, double targetScale) {
        if (subLevel == null || subLevel.isRemoved() || !validScale(targetScale)) return true;

        UUID selfId = subLevel.getUniqueId();
        if (selfId == null) return true;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return true;

        for (RopeStrandHolderBlockEntity holder : PlotBlockEntities.of(subLevel, RopeStrandHolderBlockEntity.class)) {
            RopeStrandHolderBehavior behavior = holder.getBehavior();
            if (behavior == null) continue;

            ServerRopeStrand strand = behavior.getAttachedStrand();
            if (strand == null) continue;

            for (RopeAttachment attachment : strand.getAttachments()) {
                UUID endpointId = attachment.subLevelID();
                if (Objects.equals(endpointId, selfId)) continue;

                Double endpointScale = scaleForEndpoint(container, endpointId);
                if (endpointScale == null || !sameScale(targetScale, endpointScale)) return true;
            }
        }

        return false;
    }

    public static boolean crossesBoundary(ServerLevel level, ServerRopeStrand strand) {
        if (level == null || strand == null) return false;

        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return false;

        Double reference = null;
        for (RopeAttachment attachment : strand.getAttachments()) {
            Double scale = scaleForEndpoint(container, attachment.subLevelID());
            if (scale == null) return false;
            if (reference == null) {
                reference = scale;
                continue;
            }
            if (!sameScale(reference, scale)) return true;
        }

        return false;
    }

    private static Double scaleAt(Level level, Vec3 point) {
        if (point == null) return null;
        SubLevel subLevel = Sable.HELPER.getContaining(level, point);
        if (subLevel == null) return 1.0D;
        if (subLevel instanceof ServerSubLevel server
                && !Pym.scale().isSettled(server.getUniqueId())) return null;
        double scale = Pym.scale().of(subLevel);
        return validScale(scale) ? scale : null;
    }

    private static Double scaleForEndpoint(ServerSubLevelContainer container, UUID id) {
        if (id == null) return 1.0D;
        SubLevel subLevel = container.getSubLevel(id);
        if (subLevel == null || subLevel.isRemoved()) return null;
        double scale = Pym.scale().of(subLevel);
        return validScale(scale) ? scale : null;
    }

    private static boolean sameScale(double first, double second) {
        return Math.abs(first - second) <= ScaleBounds.EPSILON;
    }

    private static boolean validScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0D;
    }

    private SimulatedRopeScaleBoundary() {}
}
