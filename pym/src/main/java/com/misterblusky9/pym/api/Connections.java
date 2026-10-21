package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.misterblusky9.pym.internal.physics.ConstraintRefresh;
import com.misterblusky9.pym.internal.scale.SubLevelParentage;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Connections {
    static final Connections INSTANCE = new Connections();

    private Connections() {}

    public boolean areJoined(ServerSubLevel first, ServerSubLevel second) {
        return SubLevelParentage.areJoined(first, second);
    }

    public boolean isJoinedToAnother(ServerSubLevel subLevel) {
        if (subLevel == null) return false;
        return SubLevelParentage.isJoinedToAnother(ServerSubLevelContainer.getContainer(subLevel.getLevel()), subLevel);
    }

    public Set<UUID> coupled(SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return Set.of();
        SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        return PymExtensions.couplingGraph(container).component(subLevel.getUniqueId());
    }

    public Set<ServerSubLevel> assembly(final ServerSubLevel origin) {
        final Set<ServerSubLevel> members = new LinkedHashSet<>();
        if (origin == null || origin.isRemoved()) return members;
        final ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(origin.getLevel());
        if (container == null) return members;

        final Deque<ServerSubLevel> queue = new ArrayDeque<>();
        members.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            final ServerSubLevel current = queue.poll();
            final UUID id = current.getUniqueId();

            final Set<UUID> next = new LinkedHashSet<>(coupled(current));
            final UUID parent = SubLevelParentage.parentOf(id);
            if (parent != null) next.add(parent);
            next.addAll(SubLevelParentage.childrenOf(id));
            next.addAll(ConstraintRefresh.neighbours(id));
            for (final ServerSubLevel other : container.getAllSubLevels()) {
                if (!other.isRemoved() && SubLevelParentage.areJoined(current, other)) next.add(other.getUniqueId());
            }

            for (final UUID candidate : next) {
                if (container.getSubLevel(candidate) instanceof final ServerSubLevel member
                        && !member.isRemoved() && members.add(member)) {
                    queue.add(member);
                }
            }
        }
        return members;
    }

    @Nullable
    public UUID ownerOf(UUID subLevelId) {
        return subLevelId == null ? null : SubLevelParentage.parentOf(subLevelId);
    }
}
