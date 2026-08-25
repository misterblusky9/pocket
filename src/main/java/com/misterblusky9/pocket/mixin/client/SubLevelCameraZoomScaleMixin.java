package com.misterblusky9.pocket.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.client.CameraSubLevelScale;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Camera.class, priority = 1500)
public abstract class SubLevelCameraZoomScaleMixin {
    @WrapMethod(method = "getMaxZoom")
    private float pocket$scaleSubLevelCameraZoom(
            final float requestedDistance,
            final Operation<Float> original
    ) {
        final float sableDistance = original.call(requestedDistance);

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.cameraEntity == null) return sableDistance;

        final CameraType cameraType = minecraft.options.getCameraType();
        if (cameraType != SableCameraTypes.SUB_LEVEL_VIEW
                && cameraType != SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED) {
            return sableDistance;
        }

        final double scale = CameraSubLevelScale.current(CameraSubLevelScale.partialTick());
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) return sableDistance;
        if (!Float.isFinite(sableDistance)) return sableDistance;

        return (float) (sableDistance * scale);
    }
}
