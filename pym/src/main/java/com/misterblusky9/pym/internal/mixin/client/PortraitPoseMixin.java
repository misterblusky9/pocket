package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.internal.client.PortraitScope;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class PortraitPoseMixin {
    @Inject(method = "renderPose()Ldev/ryanhcode/sable/companion/math/Pose3dc;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$portraitPose(final CallbackInfoReturnable<Pose3dc> cir) {
        if (PortraitScope.isDrawing((ClientSubLevel) (Object) this)) cir.setReturnValue(PortraitScope.pose());
    }

    @Inject(method = "renderPose(F)Ldev/ryanhcode/sable/companion/math/Pose3dc;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void pym$portraitPoseAt(final float partialTick, final CallbackInfoReturnable<Pose3dc> cir) {
        if (PortraitScope.isDrawing((ClientSubLevel) (Object) this)) cir.setReturnValue(PortraitScope.pose());
    }
}
