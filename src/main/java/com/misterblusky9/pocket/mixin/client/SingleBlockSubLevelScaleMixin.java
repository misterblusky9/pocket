package com.misterblusky9.pocket.mixin.client;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import org.joml.Matrix4f;
import org.joml.Quaterniondc;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VanillaSingleSubLevelRenderData.class, remap = false)
public abstract class SingleBlockSubLevelScaleMixin {
    @Shadow @Final private ClientSubLevel subLevel;

    @Redirect(
            method = "renderSingleBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaterniondc;transform(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
                    ordinal = 0
            ),
            remap = false
    )
    private Vector3d pocket$scaleBlockOriginOffset(
            final Quaterniondc rotation,
            final Vector3d vector
    ) {
        final Vector3dc scale = pocket$renderScale();
        vector.mul(scale.x(), scale.y(), scale.z());
        return rotation.transform(vector);
    }

    @Redirect(
            method = "renderSingleBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;rotate(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;",
                    ordinal = 0
            ),
            remap = false
    )
    private Matrix4f pocket$applyPoseScaleToSingleBlock(
            final Matrix4f matrix,
            final Quaternionfc rotation
    ) {
        matrix.rotate(rotation);
        final Vector3dc scale = pocket$renderScale();
        matrix.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        return matrix;
    }

    @Unique
    private Vector3dc pocket$renderScale() {
        final Pose3dc pose = this.subLevel.renderPose();
        return pose.scale();
    }
}
