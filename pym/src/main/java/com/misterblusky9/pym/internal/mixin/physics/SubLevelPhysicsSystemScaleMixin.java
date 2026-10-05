package com.misterblusky9.pym.internal.mixin.physics;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import com.misterblusky9.pym.internal.physics.ScaledColliderRebuildQueue;
import com.misterblusky9.pym.internal.physics.ScalePhysicsTransitions;
import com.misterblusky9.pym.internal.scale.ScaleTicker;
import com.misterblusky9.pym.internal.scale.ScaleLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SubLevelPhysicsSystemScaleMixin {
    @Inject(method = "onSubLevelRemoved", at = @At("HEAD"), remap = false, require = 1)
    private void pym$releaseRuntimeState(
            final SubLevel subLevel,
            final SubLevelRemovalReason reason,
            final CallbackInfo ci
    ) {
        if (subLevel instanceof final ServerSubLevel serverSubLevel) {
            ScaleLifecycle.release(serverSubLevel, reason);
        }
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;tick()V",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void pym$tickScaleServo(
            final SubLevelContainer sidelessContainer,
            final CallbackInfo ci
    ) {
        if (sidelessContainer instanceof final ServerSubLevelContainer container) {
            ScaleTicker.tick(container);
            ScaledColliderRebuildQueue.flush(container);
            ScalePhysicsTransitions.afterColliderFlush(container);
        }
    }
}
