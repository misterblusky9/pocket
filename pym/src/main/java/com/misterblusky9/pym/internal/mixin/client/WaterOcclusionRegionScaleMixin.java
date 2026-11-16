package com.misterblusky9.pym.internal.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ryanhcode.sable.render.region.SimpleCulledRenderRegion", remap = false)
public abstract class WaterOcclusionRegionScaleMixin {
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
    private Matrix4f pym$scaleRegionMesh(
            Matrix4f matrix,
            Quaternionfc rotation,
            @Local SubLevel subLevel
    ) {
        matrix.rotate(rotation);
        if (!(subLevel instanceof ClientSubLevel client) || client.isRemoved()) return matrix;

        Vector3dc scale = client.renderPose().scale();
        if (Math.abs(scale.x() - 1.0D) <= ScaleBounds.EPSILON
                && Math.abs(scale.y() - 1.0D) <= ScaleBounds.EPSILON
                && Math.abs(scale.z() - 1.0D) <= ScaleBounds.EPSILON) {
            return matrix;
        }

        return matrix.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
    }
}
