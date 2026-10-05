package com.misterblusky9.pym.internal.mixin.sable;

import com.misterblusky9.pym.internal.scale.PlotScan;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false, priority = 500)
public abstract class PlotChangeCacheMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "handleChunkSectionAddition", at = @At("HEAD"), remap = false)
    private void pym$invalidateOnSectionAddition(
            final LevelChunkSection section,
            final int x,
            final int y,
            final int z,
            final boolean uploadDataIfGlobal,
            final CallbackInfo ci
    ) {
        pym$invalidatePlot(x, z);
    }

    @Inject(method = "handleChunkSectionRemoval", at = @At("HEAD"), remap = false)
    private void pym$invalidateOnSectionRemoval(
            final int x,
            final int y,
            final int z,
            final CallbackInfo ci
    ) {
        pym$invalidatePlot(x, z);
    }

    @Inject(method = "handleBlockChange", at = @At("HEAD"), remap = false)
    private void pym$forgetOnBlockChange(
            final SectionPos sectionPos,
            final LevelChunkSection chunk,
            final int localX,
            final int localY,
            final int localZ,
            final BlockState oldState,
            final BlockState newState,
            final CallbackInfo ci
    ) {
        final BlockPos pos = new BlockPos(
                (sectionPos.x() << 4) + localX,
                (sectionPos.y() << 4) + localY,
                (sectionPos.z() << 4) + localZ);
        if (oldState.getBlock() == newState.getBlock()) return;
        if (Sable.HELPER.getContaining(this.level, pos) instanceof final ServerSubLevel subLevel) {
            PlotScan.forget(subLevel.getUniqueId());
        }
    }

    @Unique
    private void pym$invalidatePlot(final int x, final int z) {
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;
        final LevelPlot plot = container.getPlot(x, z);
        if (plot != null && plot.getSubLevel() instanceof final ServerSubLevel serverSubLevel) {
            PlotScan.forget(serverSubLevel.getUniqueId());
        }
    }
}
