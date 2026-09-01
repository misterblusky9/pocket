package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.track.CoasterCurveSelectionOutlineRenderer", remap = false)
public abstract class CoasterCurveSelectionScaleMixin {
    @WrapOperation(
            method = "toRenderSample",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/client/track/CoasterAnchorClientSpace;"
                            + "toRenderDirection(Ldev/ryanhcode/sable/companion/math/Pose3dc;"
                            + "Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            ),
            remap = false,
            require = 2
    )
    private static Vec3 pocket$scaleOutlineBasisV58(
            final Pose3dc pose,
            final Vec3 direction,
            final Operation<Vec3> original
    ) {
        final Vec3 rendered = original.call(pose, direction);
        if (pose == null) return rendered;

        try {
            final var scaleVector = pose.scale();
            if (scaleVector == null) return rendered;

            final double scale = scaleVector.x();
            if (!PocketSized.isValidScale(scale)) return rendered;

            final double clamped = PocketSized.clampScale(scale);
            if (Math.abs(clamped - 1.0D) <= PocketSized.EPSILON) return rendered;
            return rendered.scale(clamped);
        } catch (RuntimeException ignored) {
            return rendered;
        }
    }
}
