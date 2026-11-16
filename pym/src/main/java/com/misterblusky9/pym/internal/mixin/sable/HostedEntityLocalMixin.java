package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelHelper.class, remap = false)
public abstract class HostedEntityLocalMixin {
    @WrapOperation(
            method = {
                    "pushEntityLocal(Ldev/ryanhcode/sable/sublevel/SubLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;)V",
                    "popEntityLocal(Ldev/ryanhcode/sable/sublevel/SubLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/commands/arguments/EntityAnchorArgument$Anchor;)V"
            },
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/SubLevel;logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;"),
            remap = false,
            require = 7
    )
    private static Pose3d pym$presented(SubLevel subLevel, Operation<Pose3d> original) {
        Pose3d shown = Hosted.presented(subLevel);
        return shown != null ? shown : original.call(subLevel);
    }
}
