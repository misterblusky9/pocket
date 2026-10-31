package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan", remap = false)
public abstract class CoasterCartPlotScanOptimizationMixin {
    @Redirect(
            method = "scanBearingCells(Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;Lnet/minecraft/world/level/block/Block;)Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartPlotScan$BearingScanResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z", remap = false),
            remap = false,
            require = 0
    )
    private static boolean pym$skipSectionWithoutBearing(
            LevelChunkSection section,
            LevelPlot plot,
            Block bearingBlock
    ) {
        return pym$absent(section, bearingBlock);
    }

    @Redirect(
            method = "forEachBearingWorldCell(Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;Lnet/minecraft/world/level/block/Block;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z", remap = false),
            remap = false,
            require = 0
    )
    private static boolean pym$skipSectionWithoutWorldBearing(
            LevelChunkSection section,
            LevelPlot plot,
            Block bearingBlock,
            Consumer<BlockPos.MutableBlockPos> visitor
    ) {
        return pym$absent(section, bearingBlock);
    }

    @Unique
    private static boolean pym$absent(LevelChunkSection section, Block block) {
        return section.hasOnlyAir() || !section.maybeHas(state -> state.is(block));
    }
}
