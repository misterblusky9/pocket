package com.misterblusky9.pocket.mixin.physics;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import com.misterblusky9.pocket.physics.InternalForceScaleContext;
import com.misterblusky9.pocket.physics.ScaledFluidForces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelForceContextMixin {
    @Inject(method = "prePhysicsTick", at = @At("HEAD"), remap = false)
    private void pocket$enterPrePhysicsForces(
            final SubLevelPhysicsSystem system,
            final RigidBodyHandle handle,
            final double timeStep,
            final CallbackInfo ci
    ) {
        InternalForceScaleContext.enter((ServerSubLevel) (Object) this);
    }

    @Inject(method = "prePhysicsTick", at = @At("RETURN"), remap = false)
    private void pocket$exitPrePhysicsForces(
            final SubLevelPhysicsSystem system,
            final RigidBodyHandle handle,
            final double timeStep,
            final CallbackInfo ci
    ) {
        final ServerSubLevel subLevel = (ServerSubLevel) (Object) this;
        try {
            ScaledFluidForces.apply(subLevel, handle, timeStep);
        } finally {
            InternalForceScaleContext.exit(subLevel);
        }
    }

    @Inject(method = "applyQueuedForces", at = @At("HEAD"), remap = false)
    private void pocket$enterQueuedForces(
            final SubLevelPhysicsSystem system,
            final RigidBodyHandle handle,
            final double timeStep,
            final CallbackInfo ci
    ) {
        InternalForceScaleContext.enter((ServerSubLevel) (Object) this);
    }

    @Inject(method = "applyQueuedForces", at = @At("RETURN"), remap = false)
    private void pocket$exitQueuedForces(
            final SubLevelPhysicsSystem system,
            final RigidBodyHandle handle,
            final double timeStep,
            final CallbackInfo ci
    ) {
        InternalForceScaleContext.exit((ServerSubLevel) (Object) this);
    }
}
