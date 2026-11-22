package com.misterblusky9.pym.internal.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.Hosted;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Matrix4d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevel.class, remap = false)
public abstract class HostedBoundsMixin {
    @WrapOperation(
            method = "updateBoundingBox",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/companion/math/BoundingBox3d;transform(Ldev/ryanhcode/sable/companion/math/Pose3dc;Lorg/joml/Matrix4d;Ldev/ryanhcode/sable/companion/math/BoundingBox3d;)Ldev/ryanhcode/sable/companion/math/BoundingBox3d;"),
            remap = false,
            require = 1
    )
    private BoundingBox3d pym$intangibleStaysAtSite(BoundingBox3d bounds, Pose3dc pose, Matrix4d transform,
                                                   BoundingBox3d dest, Operation<BoundingBox3d> original) {
        if ((Object) this instanceof ServerSubLevel subLevel && Hosted.rootOf(subLevel) != null
                && !Hosted.tangible(subLevel)) {
            return original.call(bounds, Hosted.physical(subLevel), transform, dest);
        }
        return original.call(bounds, pose, transform, dest);
    }
}
