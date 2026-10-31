package com.misterblusky9.pym.internal.compat.aeroworks.mixin;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LevelPlot.class, remap = false)
public interface LevelPlotActorAccessor {
    @Accessor("blockEntityActors")
    Object2ObjectOpenHashMap<BlockPos, BlockEntitySubLevelActor> pym$getActors();
}
