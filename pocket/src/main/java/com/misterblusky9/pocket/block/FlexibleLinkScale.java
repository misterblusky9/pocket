package com.misterblusky9.pocket.block;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class FlexibleLinkScale {
    public static boolean sameScale(Level level, BlockPos first, BlockPos second) {
        return ScaleBounds.same(scaleAt(level, first), scaleAt(level, second));
    }

    public static boolean crossesBoundary(FlexibleCouplingBlockEntity coupling) {
        Level level = coupling.getLevel();
        if (level == null || !coupling.isLinked()) return false;
        SubLevel own = Sable.HELPER.getContaining(coupling);
        UUID partner = coupling.getPartnerSubLevelID();
        if (own != null && Objects.equals(partner, own.getUniqueId())) return false;
        double partnerScale = scaleOf(SubLevelContainer.getContainer(level), partner);
        double ownScale = own == null ? ScaleBounds.FULL : Pym.scale().of(own);
        return Double.isFinite(partnerScale) && !ScaleBounds.same(ownScale, partnerScale);
    }

    public static List<UUID> linkedTo(ServerSubLevel subLevel) {
        List<UUID> linked = new ArrayList<>();
        if (subLevel == null || subLevel.isRemoved() || subLevel.getPlot() == null) return linked;
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof FlexibleCouplingBlockEntity coupling && coupling.isLinked()
                        && coupling.getPartnerSubLevelID() != null) {
                    linked.add(coupling.getPartnerSubLevelID());
                }
            }
        }
        return linked;
    }

    private static double scaleAt(Level level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel == null ? ScaleBounds.FULL : Pym.scale().of(subLevel);
    }

    private static double scaleOf(SubLevelContainer container, UUID id) {
        if (id == null) return ScaleBounds.FULL;
        SubLevel subLevel = container == null ? null : container.getSubLevel(id);
        return subLevel == null || subLevel.isRemoved() ? Double.NaN : Pym.scale().of(subLevel);
    }
}
