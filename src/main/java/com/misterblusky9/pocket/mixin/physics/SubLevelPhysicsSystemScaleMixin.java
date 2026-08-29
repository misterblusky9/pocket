package com.misterblusky9.pocket.mixin.physics;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import com.misterblusky9.pocket.physics.ScaledColliderRebuildQueue;
import com.misterblusky9.pocket.physics.ScalePhysicsTransitions;
import com.misterblusky9.pocket.scale.ScaleController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SubLevelPhysicsSystemScaleMixin {
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;tick()V",
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void pocket$tickScaleServo(
            final SubLevelContainer sidelessContainer,
            final CallbackInfo ci
    ) {
        if (sidelessContainer instanceof final ServerSubLevelContainer container) {
            ScaleController.tickServer(container);
            ScaledColliderRebuildQueue.flush(container);
            ScalePhysicsTransitions.afterColliderFlush(container);
        }
    }
}
