package com.misterblusky9.pocket.compat.vista.mixin;

import com.misterblusky9.pocket.compat.vista.VistaViewFinderScale;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ViewFinderController.class, remap = false)
public abstract class VistaViewFinderScaleMixin {
    @Shadow
    protected static ViewFinderBlockEntity viewFinder;

    @ModifyArg(
            method = "setupCamera",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"),
            index = 1,
            remap = false)
    private static double pocket$scaledEyeHeight(double y) {
        ViewFinderBlockEntity camera = viewFinder;
        if (camera == null) return y;
        return y * VistaViewFinderScale.of(camera);
    }
}
