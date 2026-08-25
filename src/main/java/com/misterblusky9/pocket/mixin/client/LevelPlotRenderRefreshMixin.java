package com.misterblusky9.pocket.mixin.client;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import com.misterblusky9.pocket.scale.ScaleState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelPlot.class, remap = false)
public abstract class LevelPlotRenderRefreshMixin {
    @Shadow public abstract SubLevel getSubLevel();

    @Inject(method = "onBlockChange", at = @At("RETURN"), remap = false)
    private void pocket$rebuildScaledTerrainOnBlockChange(
            final BlockPos pos,
            final BlockState state,
            final CallbackInfo ci
    ) {
        final SubLevel subLevel = this.getSubLevel();
        if (!(subLevel instanceof final ClientSubLevel clientSubLevel)) return;
        if (!ScaleState.isScaled(clientSubLevel)) return;

        final SubLevelRenderData renderData = clientSubLevel.getRenderData();
        if (renderData == null) return;

        final int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        final int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        final int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        renderData.setDirty(sectionX, sectionY, sectionZ, false);

        final int stepX = pocket$boundaryStep(pos.getX());
        final int stepY = pocket$boundaryStep(pos.getY());
        final int stepZ = pocket$boundaryStep(pos.getZ());

        if (stepX != 0) renderData.setDirty(sectionX + stepX, sectionY, sectionZ, false);
        if (stepY != 0) renderData.setDirty(sectionX, sectionY + stepY, sectionZ, false);
        if (stepZ != 0) renderData.setDirty(sectionX, sectionY, sectionZ + stepZ, false);
    }

    @Unique
    private static int pocket$boundaryStep(final int coordinate) {
        final int local = coordinate & 15;
        if (local == 0) return -1;
        if (local == 15) return 1;
        return 0;
    }
}
