package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.Detached;
import com.misterblusky9.pym.internal.scale.SplitInheritance;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyRemovedBodyGuardMixin {
    @Inject(
            method = "kickFromContainingSubLevel(Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3d;Lorg/joml/Vector3d;"
                    + "Ldev/ryanhcode/sable/companion/math/Pose3d;Ldev/ryanhcode/sable/sublevel/SubLevel;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1
    )
    private static void pym$skipRemovedKick(
            PhysicsPipeline pipeline,
            ServerSubLevel subLevel,
            Vector3d linearVelocity,
            Vector3d angularVelocity,
            Pose3d containingPose,
            SubLevel containingSubLevel,
            CallbackInfo ci
    ) {
        if (subLevel == null || subLevel.isRemoved()) ci.cancel();
    }

    @WrapOperation(
            method = "assembleBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"
                            + "teleport(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;"
                            + "Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V"
            ),
            remap = false,
            require = 1
    )
    private static void pym$skipRemovedTeleport(
            PhysicsPipeline pipeline,
            PhysicsPipelineBody body,
            Vector3dc position,
            Quaterniondc orientation,
            Operation<Void> original
    ) {
        if (body == null || body.isRemoved()) return;
        original.call(pipeline, body, position, orientation);
    }

    @WrapOperation(
            method = "assembleBlocks",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;"
                    + "getLinearVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false,
            require = 1
    )
    private static Vector3d pym$stillDetachedLinear(RigidBodyHandle handle, Vector3d into,
                                                    Operation<Vector3d> original, @Local SubLevel containing) {
        return Detached.isDetached(containing) ? into : original.call(handle, into);
    }

    @WrapOperation(
            method = "assembleBlocks",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;"
                    + "getAngularVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            remap = false,
            require = 1
    )
    private static Vector3d pym$stillDetachedAngular(RigidBodyHandle handle, Vector3d into,
                                                     Operation<Vector3d> original, @Local SubLevel containing) {
        return Detached.isDetached(containing) ? into : original.call(handle, into);
    }

    @WrapOperation(
            method = "kickFromContainingSubLevel(Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lorg/joml/Vector3d;Lorg/joml/Vector3d;"
                    + "Ldev/ryanhcode/sable/companion/math/Pose3d;Ldev/ryanhcode/sable/sublevel/SubLevel;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "setSplitFrom(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Ldev/ryanhcode/sable/companion/math/Pose3d;)V"),
            remap = false,
            require = 1
    )
    private static void pym$noSplitFromDetached(ServerSubLevel subLevel, ServerSubLevel from, Pose3d pose,
                                                Operation<Void> original) {
        if (Detached.isDetached(from)) SplitInheritance.adoptLive(subLevel, from);
        else original.call(subLevel, from, pose);
    }
}
