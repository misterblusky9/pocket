package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.internal.physics.ScaleFrame;
import com.misterblusky9.pym.internal.physics.ScaledImpulseLimits;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class RapierExternalImpulseMixin implements PhysicsPipeline {
    @Unique
    private static final ThreadLocal<Boolean> pym$reissuing = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "applyImpulse", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$correctExternalPointImpulse(
            PhysicsPipelineBody body,
            Vector3dc position,
            Vector3dc force,
            CallbackInfo ci
    ) {
        if (pym$reissuing.get()) return;
        if (!ScaledImpulseLimits.bounds(body)) return;

        ServerSubLevel subLevel = (ServerSubLevel) body;

        Vector3dc contact = ScaleFrame.toBodyMetric(body, position);

        Vector3d linear = new Vector3d(force);
        Vector3d angular = new Vector3d();
        Vector3dc pivot = ScaleFrame.pivot(subLevel);
        if (pivot != null) {
            new Vector3d(contact).sub(pivot).cross(force, angular);
        }

        double factor = ScaledImpulseLimits.boundImpulse(subLevel, linear, angular);

        ci.cancel();
        pym$reissuing.set(Boolean.TRUE);
        try {
            this.applyImpulse(body, contact, factor >= 1.0D ? force : new Vector3d(force).mul(factor));
        } finally {
            pym$reissuing.set(Boolean.FALSE);
        }
    }

    @Inject(method = "applyLinearAndAngularImpulse", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$boundExternalImpulse(
            PhysicsPipelineBody body,
            Vector3dc impulse,
            Vector3dc torque,
            boolean wakeUp,
            CallbackInfo ci
    ) {
        if (pym$reissuing.get()) return;
        if (!ScaledImpulseLimits.bounds(body)) return;

        ServerSubLevel subLevel = (ServerSubLevel) body;
        Vector3d linear = new Vector3d(impulse);
        Vector3d angular = new Vector3d(torque);

        if (ScaledImpulseLimits.boundImpulse(subLevel, linear, angular) >= 1.0D) return;

        ci.cancel();
        pym$reissuing.set(Boolean.TRUE);
        try {
            this.applyLinearAndAngularImpulse(body, linear, angular, wakeUp);
        } finally {
            pym$reissuing.set(Boolean.FALSE);
        }
    }
}
