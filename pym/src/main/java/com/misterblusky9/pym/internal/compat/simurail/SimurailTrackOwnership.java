package com.misterblusky9.pym.internal.compat.simurail;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public final class SimurailTrackOwnership {
    private static final SimurailTrainFollowIndex INDEX = new SimurailTrainFollowIndex();

    public static void observe(
            Object axle, ServerSubLevel carriage, ServerSubLevel track
    ) {
        if (carriage == null || carriage.getUniqueId() == null) return;
        UUID trackId = track == null || track.isRemoved() ? null : track.getUniqueId();
        INDEX.record(axle, carriage.getUniqueId(), trackId,
                carriage.getLevel().dimension(), carriage.getLevel().getGameTime());
    }

    public static Collection<UUID> carriagesOnTrack(ServerSubLevel track) {
        if (track == null || track.isRemoved() || track.getUniqueId() == null) return Set.of();
        return INDEX.followers(track.getUniqueId(), track.getLevel().dimension(),
                track.getLevel().getGameTime());
    }

    private SimurailTrackOwnership() {}
}
