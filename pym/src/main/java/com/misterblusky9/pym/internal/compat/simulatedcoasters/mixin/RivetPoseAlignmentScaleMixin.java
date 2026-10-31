package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.rivet.RivetPoseAlignment", remap = false)
public abstract class RivetPoseAlignmentScaleMixin {
    @ModifyArg(
            method = "alignToNominal",
            at = @At(value = "INVOKE", target =
                    "Lorg/joml/Quaterniond;transform(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),
            index = 0,
            require = 1
    )
    private static Vector3d pym$scaledAnchorOffset(
            Vector3d offset, @Local(argsOnly = true) ServerSubLevel rivet
    ) {
        return offset.mul(rivet.logicalPose().scale());
    }
}
