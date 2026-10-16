package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.internal.scale.PlotBlockEntities;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SwivelBearingHeads {
    public static List<UUID> of(ServerSubLevel body) {
        List<UUID> heads = new ArrayList<>();
        for (SwivelBearingBlockEntity swivel : PlotBlockEntities.of(body, SwivelBearingBlockEntity.class)) {
            if (swivel.getSubLevelID() != null) heads.add(swivel.getSubLevelID());
        }
        return heads;
    }

    private SwivelBearingHeads() {}
}
