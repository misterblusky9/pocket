package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageLengthScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.AeroUniversalJointConfig", remap = false)
public abstract class HydraulicImpulseCapMixin {
    @ModifyReturnValue(method = {
            "hydraulicRodMaxLengthLimitImpulse",
            "hydraulicRodMaxExpectedReturnImpulse",
            "hydraulicRodMaxCombinedLengthControlImpulse"
    }, at = @At("RETURN"), remap = false)
    private static double pym$nominalImpulseCap(double cap) {
        return cap / LinkageLengthScale.activeImpulseScale();
    }
}
