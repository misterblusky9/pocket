package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterPlacementScaleContext;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartPlacementPreviewRenderer", remap = false)
public abstract class CoasterCartPlacementPreviewScaleMixin {
    private static final int[][] PYM$EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    @Inject(
            method = "renderFrame(Lnet/minecraft/client/Minecraft;F)V",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private static void pym$beginPreview(
            final Minecraft minecraft,
            final float partialTick,
            final CallbackInfo ci
    ) {
        CoasterPlacementScaleContext.reset();
    }

    @WrapOperation(
            method = "renderFrame(Lnet/minecraft/client/Minecraft;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/client/cart/CoasterCartOutlineRenderer;showRotatedUnitCubeOutline(Lnet/createmod/catnip/outliner/Outliner;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Quaterniondc;I)V"
            ),
            remap = false,
            require = 1
    )
    private static void pym$scaledPlacementBounds(
            final Outliner outliner,
            final String key,
            final Vec3 plotOrigin,
            final Quaterniondc orientation,
            final int color,
            final Operation<Void> original
    ) {
        final double scale = CoasterPlacementScaleContext.remembered();
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) {
            original.call(outliner, key, plotOrigin, orientation, color);
            return;
        }

        final Vec3 center = pym$worldPoint(plotOrigin, orientation, 0.5D, 0.5D, 0.5D, 1.0D);
        final Vec3[] corners = new Vec3[8];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = pym$worldPoint(
                    center,
                    orientation,
                    (i & 1) == 0 ? -0.5D : 0.5D,
                    (i & 2) == 0 ? -0.5D : 0.5D,
                    (i & 4) == 0 ? -0.5D : 0.5D,
                    scale
            );
        }

        final float width = (float) (0.03125D * scale);
        for (int i = 0; i < PYM$EDGES.length; i++) {
            final int[] edge = PYM$EDGES[i];
            outliner.showLine(Pair.of(key, i), corners[edge[0]], corners[edge[1]])
                    .colored(color)
                    .lineWidth(width);
        }
    }

    @Inject(
            method = "renderFrame(Lnet/minecraft/client/Minecraft;F)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private static void pym$endPreview(
            final Minecraft minecraft,
            final float partialTick,
            final CallbackInfo ci
    ) {
        CoasterPlacementScaleContext.reset();
    }

    private static Vec3 pym$worldPoint(
            final Vec3 origin,
            final Quaterniondc orientation,
            final double x,
            final double y,
            final double z,
            final double scale
    ) {
        final Vector3d point = new Vector3d(x * scale, y * scale, z * scale);
        orientation.transform(point);
        return origin.add(point.x, point.y, point.z);
    }

    private CoasterCartPlacementPreviewScaleMixin() {}
}
