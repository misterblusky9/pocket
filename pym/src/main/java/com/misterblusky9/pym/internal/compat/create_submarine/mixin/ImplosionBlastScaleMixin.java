package com.misterblusky9.pym.internal.compat.create_submarine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.create_submarine.DeepSeasScale;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.system.SubmarineSinkingSystem", remap = false)
public abstract class ImplosionBlastScaleMixin {
    @WrapOperation(
            method = {"onCrashed", "implodeCompartment"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/maxenonyme/createsubmarine/submarine/system/ImplosionSequence;"
                            + "blast(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;)V"
            ),
            remap = false
    )
    private static void pym$blastAtHullScale(
            ServerLevel level,
            Vec3 center,
            Predicate<?> crushed,
            Operation<Void> original,
            @Local(argsOnly = true) SubLevelAccess sub
    ) {
        DeepSeasScale.blastAt(DeepSeasScale.of(sub), () -> original.call(level, center, crushed));
    }
}
