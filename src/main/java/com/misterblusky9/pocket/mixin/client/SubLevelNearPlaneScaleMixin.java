package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.CameraSubLevelScale;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(GameRenderer.class)
public abstract class SubLevelNearPlaneScaleMixin {
    @Unique
    private static final float pocket$MIN_NEAR_PLANE = 0.0005F;

    @ModifyConstant(
            method = "getProjectionMatrix",
            constant = @Constant(floatValue = 0.05F)
    )
    private float pocket$scaleProjectionNearPlane(final float vanillaNearPlane) {
        final double scale = CameraSubLevelScale.current(CameraSubLevelScale.partialTick());
        if (scale >= 1.0D) return vanillaNearPlane;

        return Math.max(pocket$MIN_NEAR_PLANE, (float) (vanillaNearPlane * scale));
    }
}
