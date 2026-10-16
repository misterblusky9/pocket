package com.misterblusky9.pym.internal.compat.simulated;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.scale.PlotBlockEntities;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SimulatedSpringScaleBoundary {
    public static boolean canConnect(Level level, BlockPos first, BlockPos second) {
        if (level == null || first == null || second == null) return false;
        return ScaleBounds.same(scaleAt(level, first), scaleAt(level, second));
    }

    public static List<UUID> linkedTo(ServerSubLevel subLevel) {
        List<UUID> linked = new ArrayList<>();
        for (SpringBlockEntity spring : PlotBlockEntities.of(subLevel, SpringBlockEntity.class)) {
            if (spring.getPartnerSubLevelID() != null) linked.add(spring.getPartnerSubLevelID());
        }
        return linked;
    }

    public static boolean crossesBoundary(SpringBlockEntity spring) {
        Level level = spring.getLevel();
        if (level == null) return false;
        SubLevel own = Sable.HELPER.getContaining(spring);
        UUID partner = spring.getPartnerSubLevelID();
        if (own != null && Objects.equals(partner, own.getUniqueId())) return false;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        double ownScale = own == null ? ScaleBounds.FULL : Pym.scale().of(own);
        double partnerScale = scaleOf(container, partner);
        return Double.isFinite(partnerScale) && !ScaleBounds.same(ownScale, partnerScale);
    }

    private static double scaleAt(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel == null ? ScaleBounds.FULL : Pym.scale().of(subLevel);
    }

    private static double scaleOf(SubLevelContainer container, UUID id) {
        if (id == null) return ScaleBounds.FULL;
        SubLevel subLevel = container.getSubLevel(id);
        return subLevel == null || subLevel.isRemoved() ? Double.NaN : Pym.scale().of(subLevel);
    }

    private SimulatedSpringScaleBoundary() {}
}
