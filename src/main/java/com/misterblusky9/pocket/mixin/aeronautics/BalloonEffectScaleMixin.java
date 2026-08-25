package com.misterblusky9.pocket.mixin.aeronautics;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.PocketSized;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect.HeatedCulledRenderRegion", remap = false)
public abstract class BalloonEffectScaleMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;rotate(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;",
                    ordinal = 0
            ),
            remap = false,
            require = 0
    )
    private Matrix4f pocket$scaleEnvelopeMesh(
            final Matrix4f matrix,
            final Quaternionfc rotation,
            @Local final ClientSubLevel subLevel
    ) {
        matrix.rotate(rotation);
        if (subLevel == null || subLevel.isRemoved()) return matrix;

        final Vector3dc scale = subLevel.renderPose().scale();
        if (Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.y() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.z() - 1.0D) <= PocketSized.EPSILON) {
            return matrix;
        }

        return matrix.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }
}
