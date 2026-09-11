package com.misterblusky9.pocket.mixin.simulated;

import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.compat.simulated.MergingGlueScaleGate;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.network.packets.PlaceMergingGluePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlaceMergingGluePacket.class, remap = false)
public abstract class PlaceMergingGluePacketScaleMixin {
    @Inject(
            method = "handle",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/simulated_team/simulated/network/packets/PlaceMergingGluePacket;addMergingGlue(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;ZF)Ldev/simulated_team/simulated/content/blocks/merging_glue/MergingGlueBlockEntity;",
                    ordinal = 0,
                    remap = false
            ),
            cancellable = true,
            remap = false
    )
    private void pocket$requireMatchingScale(
            final CallbackInfo ci,
            @Local(ordinal = 0) final SubLevel parentSubLevel,
            @Local(ordinal = 1) final SubLevel childSubLevel
    ) {
        if (MergingGlueScaleGate.check(
                parentSubLevel.getLevel(), parentSubLevel, childSubLevel) != MergingGlueScaleGate.Refusal.NONE) {
            ci.cancel();
        }
    }
}
