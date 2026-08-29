package com.misterblusky9.pocket.mixin.sable;

import com.misterblusky9.pocket.moon.MoonSubLevel;
import com.misterblusky9.pocket.moon.MoonSubLevels;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// "Which sub-level is at this chunk" funnels through getPlot, and every getContaining
// overload goes through getContaining -> container.getPlot -> plot.getSubLevel().
// The moon shim is not in the grid, so this is where it has to be found.
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class MoonPlotLookupMixin {
    @Inject(method = "getPlot(II)Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$moonPlot(
            final int chunkX,
            final int chunkZ,
            final CallbackInfoReturnable<LevelPlot> cir
    ) {
        final SubLevelContainer self = (SubLevelContainer) (Object) this;
        if (!(self.getLevel() instanceof final ServerLevel level)) return;

        final MoonSubLevel shim = MoonSubLevels.get(level);
        if (shim == null) return;

        final LevelPlot plot = shim.getPlot();
        if (plot.contains(new ChunkPos(chunkX, chunkZ))) cir.setReturnValue(plot);
    }
}
