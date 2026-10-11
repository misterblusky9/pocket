package com.misterblusky9.pym.internal.compat.simulated.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.simulated.PhysicsBeamOwnerScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler$PhysicsBeam", remap = false)
public abstract class PhysicsBeamOwnerScaleMixin implements PhysicsBeamOwnerScale {
    @Shadow(remap = false) private double length;
    @Shadow(remap = false) private double currentNodeRadius;

    @Unique
    private double pym$ownerScale = ScaleBounds.FULL;

    @Unique
    private double pym$targetScale = ScaleBounds.FULL;

    @Override
    public void pym$targetScale(final double scale) {
        this.pym$targetScale = ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    @Override
    public double pym$targetScale() {
        return this.pym$targetScale;
    }

    @Override
    public void pym$ownerScale(final double scale) {
        this.pym$ownerScale = ScaleBounds.isValid(scale) ? scale : ScaleBounds.FULL;
    }

    @WrapMethod(method = "update", remap = false)
    private void pym$updateInOwnerFrame(final Operation<Void> original) {
        final double scale = this.pym$ownerScale;
        if (ScaleBounds.same(scale, ScaleBounds.FULL)) {
            original.call();
            return;
        }

        final double worldLength = this.length;
        this.length = worldLength / scale;
        try {
            original.call();
        } finally {
            this.length = worldLength;
        }
        this.currentNodeRadius *= scale;
    }
}
