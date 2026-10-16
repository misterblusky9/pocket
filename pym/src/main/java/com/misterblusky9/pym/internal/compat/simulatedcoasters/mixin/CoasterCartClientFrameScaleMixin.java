package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartClientFrameCache", remap = false)
public abstract class CoasterCartClientFrameScaleMixin {
    @Inject(method = "clientVisualPose", at = @At("RETURN"), remap = false, require = 0)
    private static void pym$restoreVisualPoseScale(
            SubLevel subLevel,
            float partialTick,
            CallbackInfoReturnable<Pose3d> cir
    ) {
        Pose3d visualPose = cir.getReturnValue();
        if (visualPose == null) return;
        if (subLevel instanceof ClientSubLevel client) {
            Pose3dc source = client.renderPose(partialTick);
            visualPose.scale().set(source.scale());
        }
    }
}
