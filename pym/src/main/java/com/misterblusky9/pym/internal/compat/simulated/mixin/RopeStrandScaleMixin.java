package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer", remap = false)
public abstract class RopeStrandScaleMixin {
    @Unique private static double pym$ownerScale = 1.0D;

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0),
            remap = false
    )
    private static void pym$beginStrand(
            final PoseStack poseStack,
            @Local final Pose3dc containingPose
    ) {
        poseStack.pushPose();
        pym$ownerScale = pym$uniformScale(containingPose);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"
            ),
            remap = false
    )
    private static void pym$segmentLength(
            final PoseStack poseStack,
            final float x,
            final float y,
            final float z
    ) {
        final double divisor = pym$ownerScale;

        if (!(divisor > 0.0D) || !Double.isFinite(divisor)) {
            poseStack.scale(x, y, z);
            return;
        }

        poseStack.scale(
                (float) (x / divisor),
                (float) (y / divisor),
                (float) (z / divisor)
        );
    }

    @Unique
    private static double pym$uniformScale(final Pose3dc pose) {
        if (pose == null) return 1.0D;
        final var scale = pose.scale();
        if (Math.abs(scale.x() - scale.y()) > ScaleBounds.EPSILON
                || Math.abs(scale.x() - scale.z()) > ScaleBounds.EPSILON) {
            return 1.0D;
        }
        final double uniform = scale.x();
        return Double.isFinite(uniform) && uniform > 0.0D ? uniform : 1.0D;
    }
}
