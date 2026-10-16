package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterCartScaleContext;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartWheelAxisRenderer",
        remap = false
)
public abstract class CoasterCartWheelAxisScaleMixin {
    @Inject(
            method = "renderCell",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void pym$enterWheelScale(
            CallbackInfo ci,
            @Local(argsOnly = true) SubLevel subLevel
    ) {
        double scale = subLevel instanceof ClientSubLevel client
                ? client.renderPose().scale().x()
                : subLevel.logicalPose().scale().x();
        CoasterCartScaleContext.push(scale);
    }

    @Inject(
            method = "renderCell",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void pym$exitWheelScale(
            CallbackInfo ci
    ) {
        CoasterCartScaleContext.pop();
    }

    @WrapOperation(
            method = "renderOneAxis",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V",
                    ordinal = 0
            ),
            remap = false,
            require = 0
    )
    private static void pym$scaleWheelLocalFrame(
            PoseStack poseStack,
            Quaternionf rotation,
            Operation<Void> original
    ) {
        original.call(poseStack, rotation);

        double scale = CoasterCartScaleContext.current();
        if (!ScaleBounds.isValid(scale) || Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON) return;

        float s = (float) ScaleBounds.clampValid(scale);
        poseStack.scale(s, s, s);
    }
}
