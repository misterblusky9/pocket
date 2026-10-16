package com.misterblusky9.pym.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record PlotContents(
        int blocks,
        int blockEntities,
        @Nullable BlockPos noShrinkPos,
        @Nullable ResourceLocation noShrink
) {
    public static final PlotContents EMPTY = new PlotContents(0, 0, null, null);

    public boolean hasNoShrinkBlock() {
        return this.noShrink != null;
    }
}
