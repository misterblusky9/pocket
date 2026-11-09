package com.misterblusky9.pym.internal.mixin.physics;

import com.misterblusky9.pym.api.PhysicsBodies;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.sublevel.KinematicContraption;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false, priority = 100)
public abstract class UnavailableBodyGuardMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "getLinearVelocity", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$linearVelocity(PhysicsPipelineBody body, Vector3d dest, CallbackInfoReturnable<Vector3d> cir) {
        if (!PhysicsBodies.available(body)) cir.setReturnValue(dest.zero());
    }

    @Inject(method = "getAngularVelocity", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$angularVelocity(PhysicsPipelineBody body, Vector3d dest, CallbackInfoReturnable<Vector3d> cir) {
        if (!PhysicsBodies.available(body)) cir.setReturnValue(dest.zero());
    }

    @Inject(method = "applyImpulse", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$impulse(PhysicsPipelineBody body, Vector3dc position, Vector3dc force, CallbackInfo ci) {
        if (!PhysicsBodies.available(body)) ci.cancel();
    }

    @Inject(method = "applyLinearAndAngularImpulse", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$linearAngularImpulse(PhysicsPipelineBody body, Vector3dc force, Vector3dc torque, boolean wakeUp,
                                          CallbackInfo ci) {
        if (!PhysicsBodies.available(body)) ci.cancel();
    }

    @Inject(method = "addLinearAndAngularVelocity", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$addVelocity(PhysicsPipelineBody body, Vector3dc linear, Vector3dc angular, CallbackInfo ci) {
        if (!PhysicsBodies.available(body)) ci.cancel();
    }

    @Inject(method = "wakeUp", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$wakeUp(PhysicsPipelineBody body, CallbackInfo ci) {
        if (!PhysicsBodies.available(body)) ci.cancel();
    }

    @Inject(method = "onStatsChanged", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$stats(ServerSubLevel subLevel, CallbackInfo ci) {
        if (!PhysicsBodies.available(subLevel)) ci.cancel();
    }

    @Inject(method = "readPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$readPose(ServerSubLevel subLevel, Pose3d dest, CallbackInfoReturnable<Pose3d> cir) {
        if (!PhysicsBodies.available(subLevel)) {
            dest.set(subLevel.logicalPose());
            cir.setReturnValue(dest);
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$teleport(PhysicsPipelineBody body, Vector3dc position, Quaterniondc orientation, CallbackInfo ci) {
        if (PhysicsBodies.available(body)) return;
        ServerSubLevel subLevel = (ServerSubLevel) body;
        subLevel.logicalPose().position().set(position);
        subLevel.logicalPose().orientation().set(orientation);
        ci.cancel();
    }

    @Inject(method = "add(Ldev/ryanhcode/sable/api/sublevel/KinematicContraption;)V", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void pym$contraption(KinematicContraption contraption, CallbackInfo ci) {
        if (Sable.HELPER.getContaining(this.level, contraption.sable$getPosition()) instanceof ServerSubLevel mount
                && !PhysicsBodies.available(mount)) ci.cancel();
    }
}
