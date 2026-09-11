package com.misterblusky9.pocket.mixin.simulatedcoasters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulatedcoasters.SimulatedCoastersCartOutlineScaleContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartOutlineRenderer", remap = false)
public abstract class CoasterCartOutlineRendererScaleMixin {
    @WrapOperation(
            method = "showRotatedUnitCubeOutline",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/client/cart/CoasterCartOutlineRenderer;"
                            + "plotLocalToWorld(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniondc;DDDLorg/joml/Vector3d;)"
                            + "Lnet/minecraft/world/phys/Vec3;"
            ),
            remap = false,
            require = 2
    )
    private static Vec3 pocket$scaleCartOutlinePoint(
            final Vec3 plotOrigin,
            final Quaterniondc orientation,
            final double x,
            final double y,
            final double z,
            final Vector3d scratch,
            final Operation<Vec3> original
    ) {
        final double scale = SimulatedCoastersCartOutlineScaleContext.current();
        if (Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            return original.call(plotOrigin, orientation, x, y, z, scratch);
        }

        return original.call(
                plotOrigin,
                orientation,
                0.5D + (x - 0.5D) * scale,
                0.5D + (y - 0.5D) * scale,
                0.5D + (z - 0.5D) * scale,
                scratch
        );
    }

    @ModifyConstant(
            method = "showRotatedUnitCubeOutline",
            constant = @Constant(floatValue = 0.03125F),
            remap = false,
            require = 1
    )
    private static float pocket$scaleCartOutlineWidth(final float original) {
        return original * (float) SimulatedCoastersCartOutlineScaleContext.current();
    }
}
