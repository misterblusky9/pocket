package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.RivetPlacementPreview;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.item.RivetPlacementOutlineClient", remap = false)
public abstract class RivetPlacementPreviewScaleMixin {
    @WrapOperation(
            method = "clientTick",
            at = @At(value = "INVOKE", target =
                    "Ldev/silvergold/simulatedcoasters/client/track/OrientedBoxOutlineRenderer;show("
                            + "Lnet/createmod/catnip/outliner/Outliner;Ljava/lang/String;"
                            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;"
                            + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DDDI)V"),
            require = 1
    )
    private static void pym$preview(
            Outliner outliner, String key, Vec3 center, Vec3 right, Vec3 up, Vec3 forward,
            double halfRight, double halfUp, double halfForward, int color, Operation<Void> original,
            @Local(argsOnly = true) Minecraft minecraft, @Local BlockHitResult hit
    ) {
        if (Sable.HELPER.getContaining(minecraft.level, hit.getBlockPos()) instanceof ClientSubLevel subLevel) {
            RivetPlacementPreview.show(subLevel, center, right, up, forward,
                    halfRight, halfUp, halfForward, color);
            return;
        }
        RivetPlacementPreview.clear();
        original.call(outliner, key, center, right, up, forward, halfRight, halfUp, halfForward, color);
    }

    @Inject(method = "clearPreview", at = @At("HEAD"), require = 1)
    private static void pym$clearPreview(Outliner outliner, CallbackInfo ci) {
        RivetPlacementPreview.clear();
    }
}
