package com.misterblusky9.pocket.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ryanhcode.sable.util.SublevelRenderOffsetHelper", remap = false)
public abstract class SubLevelRenderOffsetScaleMixin {
    @Redirect(
            method = "posePlotToProjected",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
                    ordinal = 1
            ),
            remap = false
    )
    private static void pocket$scaleCameraOffsetWithTheCraft(
            final PoseStack poseStack,
            final double x,
            final double y,
            final double z,
            final SubLevel subLevel,
            final PoseStack unused
    ) {
        final double scale = scaleOf(subLevel);
        poseStack.translate(x * scale, y * scale, z * scale);
    }

    private static double scaleOf(final SubLevel subLevel) {
        if (subLevel == null) return 1.0D;

        if (!(subLevel instanceof final ClientSubLevel client)) return 1.0D;

        final Vector3dc scale = client.renderPose().scale();
        if (scale == null) return 1.0D;

        final double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D ? uniform : 1.0D;
    }
}
