package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.api.Hosted;
import com.misterblusky9.pym.api.PhysicsBodies;
import com.misterblusky9.pym.internal.debug.PymTrace;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
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

    @Unique
    private static boolean pym$warnedHostedBoundary;

    @Inject(method = "addConstraint", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$refuseDegenerateConstraint(
            PhysicsPipelineBody body1,
            PhysicsPipelineBody body2,
            PhysicsConstraintConfiguration<?> configuration,
            CallbackInfoReturnable<PhysicsConstraintHandle> cir
    ) {
        if (!PhysicsBodies.available(body1) || !PhysicsBodies.available(body2)) {
            cir.setReturnValue(null);
            return;
        }
        if (pym$crossesHostedBoundary(body1, body2)) {
            if (!pym$warnedHostedBoundary) {
                pym$warnedHostedBoundary = true;
                PymTrace.warn("refusing a constraint between a hosted sublevel and a body outside its hosted group");
            }
            cir.setReturnValue(null);
            return;
        }
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

    @Unique
    private static boolean pym$crossesHostedBoundary(PhysicsPipelineBody body1, PhysicsPipelineBody body2) {
        ServerSubLevel a = body1 instanceof ServerSubLevel sub ? sub : null;
        ServerSubLevel b = body2 instanceof ServerSubLevel sub ? sub : null;
        boolean hostedA = Hosted.isHosted(a);
        boolean hostedB = Hosted.isHosted(b);
        if (!hostedA && !hostedB) return false;
        if (!hostedA || !hostedB) return true;
        ServerSubLevel root = Hosted.rootOf(a);
        return root == null || root != Hosted.rootOf(b);
    }
}
