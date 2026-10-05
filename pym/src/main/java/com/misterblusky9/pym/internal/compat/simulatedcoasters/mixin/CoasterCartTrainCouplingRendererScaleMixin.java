package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterLinkScale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartTrainCouplingRenderer", remap = false)
public abstract class CoasterCartTrainCouplingRendererScaleMixin {
    private static final String PYM$LAMBDA =
            "lambda$render$0("
                    + "Ljava/util/Map;"
                    + "Lnet/minecraft/client/multiplayer/ClientLevel;"
                    + "Ldev/silvergold/simulatedcoasters/track/graph/CoasterPathGraph;"
                    + "Ljava/util/List;"
                    + "F"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Ldev/ryanhcode/sable/sublevel/SubLevel;)V";

    private static final String PYM$RENDER_COUPLING =
            "renderCoupling("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;DII)V";

    @WrapOperation(
            method = PYM$LAMBDA,
            at = @At(value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/client/cart/CoasterCartTrainCouplingRenderer;"
                            + PYM$RENDER_COUPLING),
            remap = false,
            require = 1
    )
    private static void pym$couplingAtSmallerCartScale(
            final PoseStack poseStack,
            final VertexConsumer buffer,
            final Vec3 cameraPos,
            final BlockState state,
            final Vec3 headWorldA,
            final Vec3 headWorldB,
            final double centerDistance,
            final int lightA,
            final int lightB,
            final Operation<Void> original,
            @Local(argsOnly = true, index = 4) final float partialTick,
            @Local(argsOnly = true, index = 9) final SubLevel cart,
            @Local(index = 15) final SubLevel partner
    ) {
        CoasterLinkScale.pushRenderScale(
                CoasterLinkScale.minScale(cart, partner, (double) partialTick));
        try {
            original.call(
                    poseStack, buffer, cameraPos, state,
                    headWorldA, headWorldB, centerDistance, lightA, lightB);
        } finally {
            CoasterLinkScale.popRenderScale();
        }
    }

    @ModifyConstant(
            method = PYM$RENDER_COUPLING,
            constant = @Constant(doubleValue = 0.1875D),
            remap = false,
            require = 1
    )
    private static double pym$scaleHeadInset(final double original) {
        return original * CoasterLinkScale.renderScale();
    }

    @ModifyConstant(
            method = PYM$RENDER_COUPLING,
            constant = @Constant(doubleValue = -0.1875D),
            remap = false,
            require = 1
    )
    private static double pym$scaleTrailingHeadInset(final double original) {
        return original * CoasterLinkScale.renderScale();
    }

    @ModifyExpressionValue(
            method = PYM$RENDER_COUPLING,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false,
            require = 1
    )
    private static double pym$headSpanToNominal(final double world) {
        return CoasterLinkScale.toNominal(world, CoasterLinkScale.renderScale());
    }

    @WrapOperation(
            method = PYM$RENDER_COUPLING,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"),
            remap = false,
            require = 2
    )
    private static void pym$shrinkCouplingModels(
            final PoseStack poseStack,
            final double x,
            final double y,
            final double z,
            final Operation<Void> original
    ) {
        original.call(poseStack, x, y, z);

        final float scale = (float) CoasterLinkScale.renderScale();
        if (scale != 1.0F) poseStack.scale(scale, scale, scale);
    }
}
