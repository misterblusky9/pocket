package com.misterblusky9.pocket.mixin.client;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import com.misterblusky9.pocket.PocketSized;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen",
        remap = false
)
public abstract class DiagramScaleOverlayMixin {
    private static final String SCREEN_COORDS =
            "Ldev/simulated_team/simulated/content/entities/diagram/screen/DiagramScreen;" +
            "getScreenCoords(Lorg/joml/Vector3d;Lorg/joml/Quaternionfc;" +
            "Lorg/joml/Vector3dc;Lorg/joml/Matrix4fc;II)Lorg/joml/Vector2d;";

    @Shadow @Final
    public ClientSubLevel subLevel;

    @Redirect(
            method = "renderCenterOfMass",
            at = @At(value = "INVOKE", target = SCREEN_COORDS),
            remap = false
    )
    private Vector2d pocket$scaleCenterOfMassProjection(
            final Vector3d plotSpacePoint,
            final Quaternionfc orientation,
            final Vector3dc localCamera,
            final Matrix4fc projection,
            final int width,
            final int height
    ) {
        return pocket$projectScaledPoint(
                plotSpacePoint, orientation, localCamera, projection, width, height
        );
    }

    @Redirect(
            method = "renderForceArrow",
            at = @At(value = "INVOKE", target = SCREEN_COORDS),
            remap = false
    )
    private Vector2d pocket$scaleForceProjection(
            final Vector3d plotSpacePoint,
            final Quaternionfc orientation,
            final Vector3dc localCamera,
            final Matrix4fc projection,
            final int width,
            final int height
    ) {
        return pocket$projectScaledPoint(
                plotSpacePoint, orientation, localCamera, projection, width, height
        );
    }

    private Vector2d pocket$projectScaledPoint(
            final Vector3d plotSpacePoint,
            final Quaternionfc orientation,
            final Vector3dc localCamera,
            final Matrix4fc projection,
            final int width,
            final int height
    ) {
        double scale = this.subLevel.renderPose().scale().x();
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            scale = 1.0D;
        }

        final Vector3d adjusted;
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            adjusted = new Vector3d(plotSpacePoint);
        } else {
            adjusted = new Vector3d(plotSpacePoint)
                    .sub(localCamera)
                    .mul(scale)
                    .add(localCamera);
        }

        adjusted.sub(localCamera);
        orientation.transformInverse(adjusted);

        final Vector4f clip = new Vector4f(
                (float) adjusted.x,
                (float) adjusted.y,
                (float) adjusted.z,
                1.0F
        );
        clip.mul(projection);
        clip.div(clip.w);

        return new Vector2d(
                (clip.x() * 0.5F + 0.5F) * width,
                (-clip.y() * 0.5F + 0.5F) * height
        );
    }
}
