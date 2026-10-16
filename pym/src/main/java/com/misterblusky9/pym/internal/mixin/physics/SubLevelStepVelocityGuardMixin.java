package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.internal.physics.ScaledSweepGuard;
import com.misterblusky9.pym.internal.physics.ScaledVelocityGuard;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SubLevelStepVelocityGuardMixin {
    @Inject(method = "updatePose", at = @At("HEAD"), remap = false)
    private void pym$boundStepVelocity(ServerSubLevel subLevel, CallbackInfo ci) {
        if (subLevel == null || subLevel.isRemoved()) return;

        PhysicsPipeline pipeline = ((SubLevelPhysicsSystem) (Object) this).getPipeline();
        if (pipeline == null) return;

        ScaledVelocityGuard.afterStep(pipeline, subLevel);
    }

    @Inject(method = "updatePose", at = @At("RETURN"), remap = false)
    private void pym$sweepStepPath(ServerSubLevel subLevel, CallbackInfo ci) {
        if (subLevel == null || subLevel.isRemoved()) return;

        PhysicsPipeline pipeline = ((SubLevelPhysicsSystem) (Object) this).getPipeline();
        if (pipeline == null) return;

        ScaledSweepGuard.afterStep(pipeline, subLevel);
    }
}
