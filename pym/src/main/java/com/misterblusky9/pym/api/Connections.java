package com.misterblusky9.pym.api;

import com.misterblusky9.pym.internal.extension.PymExtensions;
import com.misterblusky9.pym.internal.scale.SubLevelParentage;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public final class Connections {
    static final Connections INSTANCE = new Connections();

    private Connections() {}

    public boolean areJoined(final ServerSubLevel first, final ServerSubLevel second) {
        return SubLevelParentage.areJoined(first, second);
    }

    public boolean isJoinedToAnother(final ServerSubLevel subLevel) {
        if (subLevel == null) return false;
        return SubLevelParentage.isJoinedToAnother(ServerSubLevelContainer.getContainer(subLevel.getLevel()), subLevel);
    }

    public Set<UUID> coupled(final SubLevel subLevel) {
        if (subLevel == null || subLevel.getUniqueId() == null) return Set.of();
        final SubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
        return PymExtensions.couplingGraph(container).component(subLevel.getUniqueId());
    }

    @Nullable
    public UUID ownerOf(final UUID subLevelId) {
        return subLevelId == null ? null : SubLevelParentage.parentOf(subLevelId);
    }
}
