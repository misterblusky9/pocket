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
    // Read the final zNear operand passed to JOML, not Minecraft's original
    // 0.05F constant. Pehkui also modifies that constant for small entities.
    // Multiplying the same constant by both scales creates an unnecessarily
    // tiny near plane and ruins distant-world depth-buffer precision.
    @ModifyArg(
            method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"
            ),
            index = 2
    )
    private float pym$composeCameraNearPlane(float nearFromOtherMods) {
        double subLevelScale = CameraSubLevelScale.current(CameraSubLevelScale.partialTick());
        float composedNear = ProjectionNearPlanePolicy.compose(nearFromOtherMods, subLevelScale);
        PymTrace.render(
                "camera-near-plane",
                "cameraProjection nearFromOtherMods={} subLevelScale={} resultingNear={}",
                nearFromOtherMods, subLevelScale, composedNear
        );
        return composedNear;
    }
}
