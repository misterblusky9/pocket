package com.misterblusky9.pym.internal.compat.simurail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.compat.simurail.SimurailCompat;
import com.misterblusky9.pym.internal.compat.simurail.SimurailJointLimits;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.automatic_coupler.AutomaticCouplerBlockEntity", remap = false)
public abstract class AutomaticCouplerBlockEntityMixin {
    @WrapMethod(method = "sable$physicsTick", remap = false)
    private void pym$keepCouplerLimitsInWorldUnits(
            ServerSubLevel train, RigidBodyHandle body, double dt,
            Operation<Void> original) {
        if (!SimurailCompat.enabled()) {
            original.call(train, body, dt);
            return;
        }
        SimurailJointLimits.withWorldMetricLimits(() -> original.call(train, body, dt));
    }
}
