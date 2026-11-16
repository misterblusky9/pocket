package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.CameraSubLevelScale;
import com.misterblusky9.pym.internal.client.ProjectionNearPlanePolicy;
import com.misterblusky9.pym.internal.debug.PymTrace;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class SubLevelNearPlaneScaleMixin {
    @ModifyArg(
            method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"
            ),
            index = 2
    )
    private float pym$composeCameraNearPlane(float fovRadians, float aspect, float nearFromOtherMods, float far) {
        double subLevelScale = CameraSubLevelScale.current(CameraSubLevelScale.partialTick());
        float composedNear = ProjectionNearPlanePolicy.compose(nearFromOtherMods, subLevelScale);
        float resultingNear = ProjectionNearPlanePolicy.fovCorrected(composedNear, fovRadians, aspect);
        PymTrace.render(
                "camera-near-plane",
                "cameraProjection nearFromOtherMods={} subLevelScale={} fov={} aspect={} composedNear={} resultingNear={}",
                nearFromOtherMods, subLevelScale, Math.toDegrees(fovRadians), aspect, composedNear, resultingNear
        );
        return resultingNear;
    }
}
