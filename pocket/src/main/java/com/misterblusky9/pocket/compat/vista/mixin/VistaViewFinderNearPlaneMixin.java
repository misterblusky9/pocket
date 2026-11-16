package com.misterblusky9.pocket.compat.vista.mixin;

import com.misterblusky9.pocket.compat.vista.VistaViewFinderScale;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GameRenderer.class)
public abstract class VistaViewFinderNearPlaneMixin {
    @ModifyArg(
            method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFF)Lorg/joml/Matrix4f;"),
            index = 2)
    private float pocket$viewFinderNearPlane(float near) {
        if (!ViewFinderController.isActive()) return near;
        return VistaViewFinderScale.near(near,
                VistaViewFinderScale.of(ViewFinderControllerAccessor.pocket$viewFinder()));
    }
}
