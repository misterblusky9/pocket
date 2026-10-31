package com.misterblusky9.pym.internal.compat.aeroworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.InternalForces;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.physics.CoupledMass;
import com.misterblusky9.pym.internal.physics.ScaledMassData;
import com.misterblusky9.pym.internal.scale.ScaleState;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mred231.aeroworks.content.gyroscope.GyroscopeController", remap = false)
public abstract class GyroscopeControllerMixin {
    @Shadow private double lastTickedPartial;
    @Unique private long pym$lastGameTime = Long.MIN_VALUE;

    @Inject(method = "step", at = @At("HEAD"), require = 1)
    private void pym$resetTickMarker(ServerSubLevel subLevel, double partialPhysicsTick, RigidBodyHandle handle, double timeStep, CallbackInfo ci) {
        long gameTime = subLevel.getLevel().getGameTime();
        if (gameTime != this.pym$lastGameTime) {
            this.pym$lastGameTime = gameTime;
            this.lastTickedPartial = -1.0D;
        }
    }

    @WrapOperation(
            method = "step",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;getMassTracker()Ldev/ryanhcode/sable/api/physics/mass/MassData;"),
            remap = false,
            require = 1
    )
    private MassData pym$useSolverMass(ServerSubLevel subLevel, Operation<MassData> original) {
        MassData raw = original.call(subLevel);
        double scale = ScaleState.getServerScale(subLevel);
        if (raw == null || raw.isInvalid() || !ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            return raw;
        }
        return new ScaledMassData(raw, scale, CoupledMass.solverFloor(subLevel), CoupledMass.solverInertiaFloor(subLevel));
    }

    @WrapOperation(
            method = "step",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyTorqueImpulse(Lorg/joml/Vector3dc;)V"),
            remap = false,
            require = 1
    )
    private void pym$applySolverTorque(RigidBodyHandle handle, Vector3dc impulse, Operation<Void> original,
                                       ServerSubLevel subLevel, double partialPhysicsTick, RigidBodyHandle stepHandle, double timeStep) {
        double scale = ScaleState.getServerScale(subLevel);
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            original.call(handle, impulse);
            return;
        }
        try (InternalForces ignored = InternalForces.enter(subLevel)) {
            SubLevelPhysicsSystem.require(subLevel.getLevel()).getPipeline()
                    .applyLinearAndAngularImpulse(subLevel, new Vector3d(), impulse, true);
        }
    }
}
