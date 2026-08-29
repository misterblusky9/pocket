package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.client.MoonCompressionFieldRenderer;
import com.misterblusky9.pocket.client.MoonPhysicsClient;
import com.misterblusky9.pocket.client.MoonScaleClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MoonScaleMixin {
    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 20.0F, ordinal = 0),
            require = 1
    )
    private float pocket$scaleMoonHalfSize(final float original) {
        if (MoonPhysicsClient.isActive()) return 0.0F;
        return MoonScaleClient.isPresent() ? original * MoonScaleClient.get() : 0.0F;
    }

    @Inject(method = "renderSky", at = @At("TAIL"), require = 1)
    private void pocket$renderMoonCompressionField(
            final Matrix4f frustumMatrix,
            final Matrix4f projectionMatrix,
            final float partialTick,
            final Camera camera,
            final boolean isFoggy,
            final Runnable skyFogSetup,
            final CallbackInfo ci
    ) {
        if (!isFoggy && MoonScaleClient.isPresent()) {
            MoonCompressionFieldRenderer.render(frustumMatrix, projectionMatrix, partialTick);
        }
    }
}
