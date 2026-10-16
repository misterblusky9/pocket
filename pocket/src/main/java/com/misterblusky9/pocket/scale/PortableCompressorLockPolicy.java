package com.misterblusky9.pocket.scale;

import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.misterblusky9.pym.api.ScaleDriver;
import com.misterblusky9.pym.api.spi.ResizePolicy;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PortableCompressorLockPolicy implements ResizePolicy {
    @Override
    public String refuse(ServerSubLevel subLevel, double from, double to) {
        return null;
    }

    @Override
    public String refuse(ServerSubLevel subLevel, double from, double to,
                         ScaleDriver driver) {
        if (!(driver instanceof SurvivalScaleSource) && !ResizeActor.isSurvival()) return null;
        if (subLevel == null || subLevel.getPlot() == null) return null;
        for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof PortableSubspaceCompressorBlockEntity compressor
                        && compressor.blocksExternalResize()) {
                    return "pocket.message.portable_compressor_locked";
                }
            }
        }
        return null;
    }
}
