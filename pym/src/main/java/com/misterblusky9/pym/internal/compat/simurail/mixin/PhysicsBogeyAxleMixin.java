package com.misterblusky9.pym.internal.compat.simurail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.misterblusky9.pym.internal.compat.simurail.SimurailCompat;
import com.misterblusky9.pym.internal.compat.simurail.SimurailJointLimits;
import com.misterblusky9.pym.internal.compat.simurail.SimurailTrackOwnership;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.crystaelix.simurail.content.bogey.PhysicsBogeyAxle", remap = false)
public abstract class PhysicsBogeyAxleMixin {
    @Shadow protected GenericConstraintHandle trackJoint;
    @Shadow protected GenericConstraintHandle bogeyJoint;
    @Shadow protected ServerSubLevel trackSubLevel;

    @Inject(method = "updateJoint", at = @At("RETURN"), remap = false)
    private void pym$registerRailGuides(ServerSubLevel train, CallbackInfo ci) {
        if (!SimurailCompat.enabled()) return;
        SimurailJointLimits.railGuide(this.trackJoint);
        SimurailJointLimits.railGuide(this.bogeyJoint);
        SimurailTrackOwnership.observe((Object) this, train,
                this.bogeyJoint != null && this.bogeyJoint.isValid() ? this.trackSubLevel : null);
    }

    @WrapMethod(method = "updateLimits", remap = false)
    private void pym$keepRailLimitsInWorldUnits(
            ServerSubLevel train, double dt, Operation<Void> original) {
        if (!SimurailCompat.enabled()) {
            original.call(train, dt);
            return;
        }
        SimurailJointLimits.withWorldMetricLimits(() -> original.call(train, dt));
    }
}
