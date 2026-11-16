package com.misterblusky9.pocket.compat.vista.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.misterblusky9.pocket.client.HeadwearUnderHat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.vista.client.renderer.TvItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = TvItemRenderer.class, remap = false)
public abstract class VistaTvHeadUnderHatMixin {
    @Unique
    private static final float POCKET$SMILE_PULL = 0.9999F;

    @Unique
    private static final float POCKET$SMILE_GUI_PULL = 0.02F;

    @Unique
    private static boolean pocket$layered(MultiBufferSource buffers) {
        return HeadwearUnderHat.active() && buffers instanceof MultiBufferSource.BufferSource;
    }

    @ModifyConstant(method = "renderTvHead", constant = @Constant(doubleValue = -0.004999999888241291D), remap = false)
    private static double pocket$flushSmile(double lift, @Local(argsOnly = true) MultiBufferSource buffers) {
        return pocket$layered(buffers) ? 0.0D : lift;
    }

    @WrapOperation(
            method = "renderTvHead",
            at = @At(value = "INVOKE", target =
                    "Lnet/mehvahdjukaar/vista/client/renderer/TvBlockEntityRenderer;addQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFFI)V"),
            remap = false)
    private static void pocket$layerSmile(VertexConsumer consumer, PoseStack pose, float x, float y, float width,
                                          float height, int light, Operation<Void> original,
                                          @Local(argsOnly = true) MultiBufferSource buffers) {
        if (!pocket$layered(buffers)) {
            original.call(consumer, pose, x, y, width, height, light);
            return;
        }
        pose.pushPose();
        if (RenderSystem.getProjectionMatrix().m33() == 0.0F) pose.last().pose().scaleLocal(POCKET$SMILE_PULL);
        else pose.last().pose().translateLocal(0.0F, 0.0F, POCKET$SMILE_GUI_PULL);
        original.call(consumer, pose, x, y, width, height, light);
        pose.popPose();
        HeadwearUnderHat.withoutPolygonOffset(((MultiBufferSource.BufferSource) buffers)::endBatch);
    }
}
