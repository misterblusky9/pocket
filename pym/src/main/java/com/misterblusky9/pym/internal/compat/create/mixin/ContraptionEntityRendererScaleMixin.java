package com.misterblusky9.pym.internal.compat.create.mixin;

import com.misterblusky9.pym.api.ScaleBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(value = ContraptionEntityRenderer.class, remap = false)
public abstract class ContraptionEntityRendererScaleMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> pym$SCALE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            require = 1
    )
    private void pym$pushSubLevelScale(AbstractContraptionEntity entity, float yaw, float partialTicks,
                                       PoseStack poseStack, MultiBufferSource buffers, int overlay,
                                       CallbackInfo ci) {
        ArrayDeque<Boolean> stack = pym$SCALE_STACK.get();
        double scale = pym$subLevelScale(entity, partialTicks);
        if (Math.abs(scale - 1.0D) <= ScaleBounds.EPSILON
                || Math.abs(pym$matrixScale(poseStack.last().pose()) - 1.0D) > 1.0E-3D) {
            stack.push(Boolean.FALSE);
            return;
        }
        float s = (float) scale;
        poseStack.pushPose();
        poseStack.scale(s, s, s);
        stack.push(Boolean.TRUE);
    }

    @Inject(
            method = "render(Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"),
            require = 1
    )
    private void pym$popSubLevelScale(AbstractContraptionEntity entity, float yaw, float partialTicks,
                                      PoseStack poseStack, MultiBufferSource buffers, int overlay,
                                      CallbackInfo ci) {
        ArrayDeque<Boolean> stack = pym$SCALE_STACK.get();
        if (!stack.isEmpty() && stack.pop()) poseStack.popPose();
        if (stack.isEmpty()) pym$SCALE_STACK.remove();
    }

    @Unique
    private static double pym$subLevelScale(AbstractContraptionEntity entity, float partialTicks) {
        SubLevel raw = Sable.HELPER.getContaining(entity);
        if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) return 1.0D;
        Vector3dc scale = subLevel.renderPose(partialTicks).scale();
        if (!ScaleBounds.isValid(scale.x())
                || Math.abs(scale.x() - scale.y()) > ScaleBounds.EPSILON
                || Math.abs(scale.x() - scale.z()) > ScaleBounds.EPSILON) {
            return 1.0D;
        }
        return scale.x();
    }

    @Unique
    private static double pym$matrixScale(Matrix4fc matrix) {
        return Math.sqrt((double) matrix.m00() * matrix.m00()
                + (double) matrix.m01() * matrix.m01()
                + (double) matrix.m02() * matrix.m02());
    }
}
