package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.MoonClientSubLevel;
import com.misterblusky9.pocket.client.MoonClientSubLevels;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Client half of MoonPlotLookupMixin: this is what makes getContainingClient find the
// moon, which is the first thing Simulated's staff handler asks for.
@Mixin(value = SubLevelContainer.class, remap = false)
public abstract class MoonClientPlotLookupMixin {
    @Inject(method = "getPlot(II)Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void pocket$moonClientPlot(
            final int chunkX,
            final int chunkZ,
            final CallbackInfoReturnable<LevelPlot> cir
    ) {
        final SubLevelContainer self = (SubLevelContainer) (Object) this;
        if (!(self.getLevel() instanceof ClientLevel)) return;

        final MoonClientSubLevel shim = MoonClientSubLevels.get();
        if (shim == null || shim.getLevel() != self.getLevel()) return;

        final LevelPlot plot = shim.getPlot();
        if (plot.contains(new ChunkPos(chunkX, chunkZ))) cir.setReturnValue(plot);
    }
}
