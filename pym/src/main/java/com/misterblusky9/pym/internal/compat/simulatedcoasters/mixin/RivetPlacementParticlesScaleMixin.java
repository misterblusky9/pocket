package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.client.ParticleEmitterSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.rivet.RivetPlacementParticlesClient", remap = false)
public abstract class RivetPlacementParticlesScaleMixin {
    @ModifyArg(
            method = "onAnimationComplete",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;", ordinal = 2),
            index = 0,
            require = 1
    )
    private static double pym$ringRadius(double radius) {
        double scale = ParticleEmitterSource.currentScale();
        return radius * (ScaleBounds.isValid(scale) ? scale : 1.0D);
    }
}
