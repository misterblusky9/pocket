package com.misterblusky9.pym.internal.mixin.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import com.misterblusky9.pym.internal.physics.InternalForceScaleContext;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RigidBodyHandle.class, remap = false)
public abstract class RigidBodyHandleForceScaleMixin {
    @Shadow @Final private PhysicsPipelineBody body;
    @Shadow @Final private SubLevelPhysicsSystem physicsSystem;

    @Inject(
            method = "applyLinearAndAngularImpulse(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pym$scaleInternalImpulse(
            Vector3dc impulse,
            Vector3dc torque,
            boolean wakeUp,
            CallbackInfo ci
    ) {
        double[] factors = InternalForceScaleContext.forceFactors(this.body);
        if (factors[0] == 1.0D && factors[1] == 1.0D) return;

        this.physicsSystem.getPipeline().applyLinearAndAngularImpulse(
                this.body,
                new Vector3d(impulse).mul(factors[0]),
                new Vector3d(torque).mul(factors[1]),
                wakeUp
        );
        ci.cancel();
    }

    @Inject(
            method = "applyImpulseAtPoint(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void pym$scaleInternalPointImpulse(
            Vector3dc position,
            Vector3dc force,
            CallbackInfo ci
    ) {
        if (!(this.body instanceof ServerSubLevel subLevel)) return;

        double[] factors = InternalForceScaleContext.forceFactors(this.body);
        if (factors[0] == 1.0D && factors[1] == 1.0D) return;

        Vector3dc center = subLevel.getMassTracker().getCenterOfMass();
        if (center == null) return;

        Vector3d originalLever = new Vector3d(position).sub(center);
        Vector3d originalTorque = originalLever.cross(force, new Vector3d());

        this.physicsSystem.getPipeline().applyLinearAndAngularImpulse(
                this.body,
                new Vector3d(force).mul(factors[0]),
                originalTorque.mul(factors[1]),
                true
        );
        ci.cancel();
    }
}
