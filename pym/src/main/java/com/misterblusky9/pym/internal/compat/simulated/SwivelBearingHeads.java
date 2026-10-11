package com.misterblusky9.pym.internal.compat.simulated;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SwivelBearingHeads {
    public static List<UUID> of(final ServerSubLevel body) {
        final List<UUID> heads = new ArrayList<>();
        for (final BlockEntitySubLevelActor actor : body.getPlot().getBlockEntityActors()) {
            if (actor instanceof final SwivelBearingBlockEntity swivel && swivel.getSubLevelID() != null) {
                heads.add(swivel.getSubLevelID());
            }
        }
        return heads;
    }

    private SwivelBearingHeads() {}
}
