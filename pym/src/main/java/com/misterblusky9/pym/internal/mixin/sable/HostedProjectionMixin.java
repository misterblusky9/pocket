package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class HostedProjectionMixin {
    @WrapOperation(
            method = "projectOutOfSubLevel*",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/SubLevel;logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;"),
            remap = false,
            require = 2
    )
    private Pose3d pym$presented(SubLevel subLevel, Operation<Pose3d> original) {
        Pose3d shown = Hosted.presented(subLevel);
        return shown != null ? shown : original.call(subLevel);
    }

    @WrapOperation(
            method = "projectOutOfSubLevel*",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/mixinterface/clip_overwrite/LevelPoseProviderExtension;sable$getPose(Ldev/ryanhcode/sable/sublevel/SubLevel;)Ldev/ryanhcode/sable/companion/math/Pose3dc;"),
            remap = false,
            require = 2
    )
    private Pose3dc pym$presentedThroughLevel(LevelPoseProviderExtension level, SubLevel subLevel,
                                              Operation<Pose3dc> original) {
        Pose3d shown = Hosted.presented(subLevel);
        return shown != null ? shown : original.call(level, subLevel);
    }
}
