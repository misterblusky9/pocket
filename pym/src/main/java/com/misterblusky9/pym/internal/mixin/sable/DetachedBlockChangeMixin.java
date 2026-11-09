package com.misterblusky9.pym.internal.mixin.sable;

import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class DetachedBlockChangeMixin {
    @Inject(method = "handleBlockChange", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$skipDetached(SectionPos section, LevelChunkSection chunkSection, int x, int y, int z,
                                  BlockState previous, BlockState current, CallbackInfo ci) {
        SubLevelContainer container = SubLevelContainer.getContainer(((SubLevelPhysicsSystem) (Object) this).getLevel());
        LevelPlot plot = container == null ? null : container.getPlot(section.chunk());
        if (plot == null) return;
        if (!previous.isAir() && current.isAir()) Hosted.broke(plot.getSubLevel());
        if (!Detached.isDetached(plot.getSubLevel())) return;
        if (!previous.isAir() && current.isAir() && plot.getSubLevel() instanceof ServerSubLevel detached)
            Detached.broke(detached);
        ci.cancel();
    }
}
