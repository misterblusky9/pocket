package com.misterblusky9.pym.internal.scale;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public final class PlotBlockEntities {
    public static <T> List<T> of(SubLevel subLevel, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (subLevel == null || subLevel.isRemoved() || subLevel.getPlot() == null) return found;
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (type.isInstance(blockEntity)) found.add(type.cast(blockEntity));
            }
        }
        return found;
    }

    private PlotBlockEntities() {}
}
