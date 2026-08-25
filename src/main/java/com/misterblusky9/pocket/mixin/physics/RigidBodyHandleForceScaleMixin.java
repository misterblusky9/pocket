package com.misterblusky9.pocket.mixin.physics;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import com.misterblusky9.pocket.physics.InternalForceScaleContext;
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
    private void pocket$scaleInternalImpulse(
            final Vector3dc impulse,
            final Vector3dc torque,
            final boolean wakeUp,
            final CallbackInfo ci
    ) {
        final double[] factors = InternalForceScaleContext.forceFactors(this.body);
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
    private void pocket$scaleInternalPointImpulse(
            final Vector3dc position,
            final Vector3dc force,
            final CallbackInfo ci
    ) {
        if (!(this.body instanceof final ServerSubLevel subLevel)) return;

        final double[] factors = InternalForceScaleContext.forceFactors(this.body);
        if (factors[0] == 1.0D && factors[1] == 1.0D) return;

        final Vector3dc center = subLevel.getMassTracker().getCenterOfMass();
        if (center == null) return;

        final Vector3d originalLever = new Vector3d(position).sub(center);
        final Vector3d originalTorque = originalLever.cross(force, new Vector3d());

        this.physicsSystem.getPipeline().applyLinearAndAngularImpulse(
                this.body,
                new Vector3d(force).mul(factors[0]),
                originalTorque.mul(factors[1]),
                true
        );
        ci.cancel();
    }
}
