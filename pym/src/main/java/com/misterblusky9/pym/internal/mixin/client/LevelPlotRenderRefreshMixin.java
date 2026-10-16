package com.misterblusky9.pym.internal.mixin.client;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import com.misterblusky9.pym.internal.scale.ScaleState;
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
    private void pym$rebuildScaledTerrainOnBlockChange(
            BlockPos pos,
            BlockState state,
            CallbackInfo ci
    ) {
        SubLevel subLevel = this.getSubLevel();
        if (!(subLevel instanceof ClientSubLevel clientSubLevel)) return;
        if (!ScaleState.isScaled(clientSubLevel)) return;

        SubLevelRenderData renderData = clientSubLevel.getRenderData();
        if (renderData == null) return;

        int sectionX = SectionPos.blockToSectionCoord(pos.getX());
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
        renderData.setDirty(sectionX, sectionY, sectionZ, false);

        int stepX = pym$boundaryStep(pos.getX());
        int stepY = pym$boundaryStep(pos.getY());
        int stepZ = pym$boundaryStep(pos.getZ());

        if (stepX != 0) renderData.setDirty(sectionX + stepX, sectionY, sectionZ, false);
        if (stepY != 0) renderData.setDirty(sectionX, sectionY + stepY, sectionZ, false);
        if (stepZ != 0) renderData.setDirty(sectionX, sectionY, sectionZ + stepZ, false);
    }

    @Unique
    private static int pym$boundaryStep(int coordinate) {
        int local = coordinate & 15;
        if (local == 0) return -1;
        if (local == 15) return 1;
        return 0;
    }
}
