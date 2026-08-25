package com.misterblusky9.pocket.mixin.physics;

import com.misterblusky9.pocket.tweezers.TweezerSessions;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class TweezerGripDriveMixin {
    @Inject(method = "prePhysicsTick", at = @At("HEAD"), remap = false)
    private void pocket$aimTweezerGrip(
            final SubLevelPhysicsSystem system,
            final RigidBodyHandle body,
            final double timeStep,
            final CallbackInfo ci
    ) {
        if (system == null) return;

        TweezerSessions.drive(
                (ServerSubLevel) (Object) this,
                system.getPipeline(),
                system.getPartialPhysicsTick());
    }
}
