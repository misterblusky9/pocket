package com.misterblusky9.pocket.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.entity.EntityScaleTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(EntityRenderDispatcher.class)
public abstract class SubLevelEntityRenderScaleMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> pocket$SCALE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void pocket$pushInheritedScale(
            final Entity entity,
            final double x,
            final double y,
            final double z,
            final float entityYaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final int packedLight,
            final CallbackInfo ci
    ) {
        final ArrayDeque<Boolean> stack = pocket$SCALE_STACK.get();
        final double scale = EntityScaleTracker.renderScale(entity, partialTick);

        if (!Double.isFinite(scale)
                || Math.abs(scale - 1.0D) <= PocketSized.EPSILON) {
            stack.push(Boolean.FALSE);
            return;
        }

        final double pivotX = x;
        final double pivotY = entity.isPassenger() ? y + entity.getEyeHeight() : y;
        final double pivotZ = z;

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);
        poseStack.scale((float) scale, (float) scale, (float) scale);
        poseStack.translate(-pivotX, -pivotY, -pivotZ);
        stack.push(Boolean.TRUE);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void pocket$popInheritedScale(
            final Entity entity,
            final double x,
            final double y,
            final double z,
            final float entityYaw,
            final float partialTick,
            final PoseStack poseStack,
            final MultiBufferSource bufferSource,
            final int packedLight,
            final CallbackInfo ci
    ) {
        final ArrayDeque<Boolean> stack = pocket$SCALE_STACK.get();
        if (!stack.isEmpty() && stack.pop()) {
            poseStack.popPose();
        }
        if (stack.isEmpty()) {
            pocket$SCALE_STACK.remove();
        }
    }
}
