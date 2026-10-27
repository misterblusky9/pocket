package com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.mixin;

import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRenderScale;
import com.misterblusky9.pym.internal.compat.aeronautics_utility_objects.LinkageRodCandidate;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.enxv.aerouniversaljoint.client.HydraulicRodTargeting$Candidate", remap = false)
public abstract class HydraulicRodCandidateMixin implements LinkageRodCandidate {
    @Unique private double pym$scale = 1.0D;

    @Shadow public abstract Vec3 start();

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void pym$captureScale(CallbackInfo ci) {
        this.pym$scale = LinkageRenderScale.takePendingCandidate();
    }

    @Override
    public Vec3 pym$start() {
        return this.start();
    }

    @Override
    public double pym$scale() {
        return this.pym$scale;
    }
}
