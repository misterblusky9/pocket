package com.misterblusky9.pym.internal.mixin.physics;

import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import com.misterblusky9.pym.internal.physics.KinematicCollisionSuppression;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class RapierKinematicCollisionGateMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(
            method = "add(Ldev/ryanhcode/sable/api/sublevel/KinematicContraption;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pym$suppressFullSizeChildCollider(
            final KinematicContraption contraption,
            final CallbackInfo ci
    ) {
        if (KinematicCollisionSuppression.shouldSuppress(this.level, contraption)) {
            KinematicCollisionSuppression.markSuppressed(contraption);
            ci.cancel();
            return;
        }
    }

    @Inject(
            method = "remove(Ldev/ryanhcode/sable/api/sublevel/KinematicContraption;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void pym$forgetSuppressedChild(
            final KinematicContraption contraption,
            final CallbackInfo ci
    ) {
        KinematicCollisionSuppression.forget(contraption);
    }
}
