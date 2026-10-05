package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.internal.physics.RepointableConstraint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle", remap = false)
public abstract class ConstraintHandleSafeRemoveMixin {
    @Shadow
    public abstract boolean isValid();

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$skipRemovingWhatIsAlreadyGone(final CallbackInfo ci) {
        final RepointableConstraint tracked = (RepointableConstraint) (Object) this;

        if (tracked.pym$isKnownRemoved() || !tracked.pym$isSceneLive()) {
            tracked.pym$markRemoved();
            ci.cancel();
            return;
        }

        if (!isValid()) {
            tracked.pym$markRemoved();
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("RETURN"), remap = false)
    private void pym$rememberSuccessfulRemoval(final CallbackInfo ci) {
        ((RepointableConstraint) (Object) this).pym$markRemoved();
    }
}
