package com.misterblusky9.pym.internal.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.client.RenderDetail;
import com.misterblusky9.pym.internal.collision.PlotScanBounds;
import dev.ryanhcode.sable.mixinhelpers.entity.entity_rendering.shadows.SubLevelEntityShadowRenderer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = SubLevelEntityShadowRenderer.class, remap = false)
public abstract class SubLevelShadowScanBoundsMixin {
    @Redirect(
            method = "renderEntityShadowOnSubLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(IIIIII)Ljava/lang/Iterable;"
            ),
            remap = false
    )
    private static Iterable<BlockPos> pym$clampShadowScanToPlot(
            final int minX,
            final int minY,
            final int minZ,
            final int maxX,
            final int maxY,
            final int maxZ,
            @Local(argsOnly = true) final Entity entity,
            @Local(name = "subLevel") final SubLevel subLevel
    ) {
        if (subLevel != null && !RenderDetail.worthDrawing(Pym.scale().of(subLevel), Pym.entities().scaleOf(entity))) {
            return List.of();
        }
        return PlotScanBounds.betweenClosed(subLevel, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
