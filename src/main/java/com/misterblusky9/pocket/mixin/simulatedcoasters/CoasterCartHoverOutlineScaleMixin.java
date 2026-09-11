package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartOutlineScaleContext;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersScaleLookup;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {
        "dev.silvergold.simulatedcoasters.client.cart.CoasterCartHoverOutlineClient",
        "dev.silvergold.simulatedcoasters.client.cart.CoasterCartLinkSelectionOutlineRenderer",
        "dev.silvergold.simulatedcoasters.client.cart.CoasterCartShearUnlinkOutlineClient"
}, remap = false)
public abstract class CoasterCartHoverOutlineScaleMixin {
    @WrapOperation(
            method = "renderFrame(Lnet/minecraft/client/Minecraft;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/client/cart/CoasterCartOutlineRenderer;"
                            + "showRotatedUnitCubeOutline(Lnet/createmod/catnip/outliner/Outliner;Ljava/lang/String;"
                            + "Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniondc;I)V"
            ),
            remap = false,
            require = 1
    )
    private static void pocket$scaleCartOutline(
            final Outliner outliner,
            final String key,
            final Vec3 plotOrigin,
            final Quaterniondc orientation,
            final int color,
            final Operation<Void> original,
            @Local(ordinal = 0) final SubLevel cart
    ) {
        SimulatedCoastersCartOutlineScaleContext.push(SimulatedCoastersScaleLookup.scaleOf(cart, null));
        try {
            original.call(outliner, key, plotOrigin, orientation, color);
        } finally {
            SimulatedCoastersCartOutlineScaleContext.pop();
        }
    }
}
