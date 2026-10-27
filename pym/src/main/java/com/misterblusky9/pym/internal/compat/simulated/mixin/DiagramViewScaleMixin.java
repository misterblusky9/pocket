package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = DiagramScreen.class, remap = false)
public abstract class DiagramViewScaleMixin {
    @ModifyArg(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/util/SimpleSubLevelGroupRenderer;renderChain(Ldev/ryanhcode/sable/sublevel/SubLevel;Lfoundry/veil/api/client/render/framebuffer/AdvancedFbo;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Vector3d;Lorg/joml/Quaternionf;F)V"
            ),
            index = 3,
            remap = false
    )
    private static Matrix4f pym$viewAtPlotScale(
            Matrix4f projection,
            @Local(argsOnly = true) SubLevel subLevel,
            @Local(argsOnly = true, ordinal = 0) float partialTicks
    ) {
        if (!(subLevel instanceof ClientSubLevel client)) return projection;
        double scale = client.renderPose(partialTicks).scale().x();
        if (!Double.isFinite(scale) || scale <= 0.0D || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            return projection;
        }
        return new Matrix4f(projection).scale((float) (1.0D / scale));
    }
}
