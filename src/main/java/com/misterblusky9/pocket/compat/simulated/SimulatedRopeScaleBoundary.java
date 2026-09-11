package com.misterblusky9.pocket.compat.simulated;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.scale.ScaleState;
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
            final Level level,
            final RopeStrandHolderBehavior start,
            final RopeStrandHolderBehavior end
    ) {
        if (level == null || start == null || end == null) return false;

        final Double startScale = scaleAt(level, start.getAttachmentPoint());
        final Double endScale = scaleAt(level, end.getAttachmentPoint());
        return startScale != null && endScale != null && sameScale(startScale, endScale);
    }

    public static boolean blocksTransition(final ServerSubLevel subLevel, final double targetScale) {
        if (subLevel == null || subLevel.isRemoved() || !validScale(targetScale)) return true;

        final UUID selfId = subLevel.getUniqueId();
        if (selfId == null) return true;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(subLevel.getLevel());
        if (container == null) return true;

        for (final var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof final RopeStrandHolderBlockEntity holder)) continue;

            final RopeStrandHolderBehavior behavior = holder.getBehavior();
            if (behavior == null) continue;

            final ServerRopeStrand strand = behavior.getAttachedStrand();
            if (strand == null) continue;

            for (final RopeAttachment attachment : strand.getAttachments()) {
                final UUID endpointId = attachment.subLevelID();
                if (Objects.equals(endpointId, selfId)) continue;

                final Double endpointScale = scaleForEndpoint(container, endpointId);
                if (endpointScale == null || !sameScale(targetScale, endpointScale)) return true;
            }
        }

        return false;
    }

    public static boolean crossesBoundary(final ServerLevel level, final ServerRopeStrand strand) {
        if (level == null || strand == null) return false;

        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        if (container == null) return false;

        Double reference = null;
        for (final RopeAttachment attachment : strand.getAttachments()) {
            final Double scale = scaleForEndpoint(container, attachment.subLevelID());
            if (scale == null) return false;
            if (reference == null) {
                reference = scale;
                continue;
            }
            if (!sameScale(reference, scale)) return true;
        }

        return false;
    }

    private static Double scaleAt(final Level level, final Vec3 point) {
        if (point == null) return null;
        final SubLevel subLevel = Sable.HELPER.getContaining(level, point);
        if (subLevel == null) return 1.0D;
        if (subLevel instanceof final ServerSubLevel server
                && !ScaleState.isSettled(server.getUniqueId())) return null;
        final double scale = ScaleState.getScale(subLevel);
        return validScale(scale) ? scale : null;
    }

    private static Double scaleForEndpoint(final ServerSubLevelContainer container, final UUID id) {
        if (id == null) return 1.0D;
        final SubLevel subLevel = container.getSubLevel(id);
        if (subLevel == null || subLevel.isRemoved()) return null;
        final double scale = ScaleState.getScale(subLevel);
        return validScale(scale) ? scale : null;
    }

    private static boolean sameScale(final double first, final double second) {
        return Math.abs(first - second) <= PocketSized.EPSILON;
    }

    private static boolean validScale(final double scale) {
        return Double.isFinite(scale) && scale > 0.0D;
    }

    private SimulatedRopeScaleBoundary() {}
}
