package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.internal.scale.PlotBlockEntities;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SimulatedSpringScaleBoundary {
    public static List<UUID> linkedTo(ServerSubLevel subLevel) {
        List<UUID> linked = new ArrayList<>();
        for (SpringBlockEntity spring : PlotBlockEntities.of(subLevel, SpringBlockEntity.class)) {
            if (spring.getPartnerSubLevelID() != null) linked.add(spring.getPartnerSubLevelID());
        }
        return linked;
    }

    private SimulatedSpringScaleBoundary() {}
}
