package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.internal.debug.PymTrace;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class DegenerateConstraintGuardMixin {
    @Unique
    private static boolean pym$warnedWorldToWorld;

    @Unique
    private static boolean pym$warnedSameBody;

    @Inject(method = "addConstraint", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$refuseDegenerateConstraint(
            final PhysicsPipelineBody body1,
            final PhysicsPipelineBody body2,
            final PhysicsConstraintConfiguration<?> configuration,
            final CallbackInfoReturnable<PhysicsConstraintHandle> cir
    ) {
        if (body1 != body2) return;

        if (body1 == null) {
            if (!pym$warnedWorldToWorld) {
                pym$warnedWorldToWorld = true;
                PymTrace.warn(
                        "refusing a constraint between the static world and itself; "
                                + "the caller may retry after its body association updates");
            }
        } else if (!pym$warnedSameBody) {
            pym$warnedSameBody = true;
            PymTrace.warn(
                    "refusing a constraint whose two ends resolved to the same Sable body; "
                            + "the caller may retry after its body association updates");
        }

        cir.setReturnValue(null);
    }
}
