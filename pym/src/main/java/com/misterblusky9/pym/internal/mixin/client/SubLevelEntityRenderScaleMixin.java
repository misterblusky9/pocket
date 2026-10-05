package com.misterblusky9.pym.internal.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.misterblusky9.pym.api.ScaleBounds;
import com.misterblusky9.pym.internal.entity.EntityScaleTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(value = EntityRenderDispatcher.class, priority = 900)
public abstract class SubLevelEntityRenderScaleMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> pym$SCALE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD")
    )
    private void pym$pushInheritedScale(
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
        final ArrayDeque<Boolean> stack = pym$SCALE_STACK.get();
        final double scale = EntityScaleTracker.renderScale(entity, partialTick) * pym$containingScale(entity, partialTick);

        final boolean applyScale = Double.isFinite(scale)
                && Math.abs(scale - 1.0D) > ScaleBounds.EPSILON;

        if (!applyScale) {
            stack.push(Boolean.FALSE);
            return;
        }

        poseStack.pushPose();

        final double pivotX = x;
        final double pivotY = entity.isPassenger() ? y + entity.getEyeHeight() : y;
        final double pivotZ = z;

        poseStack.translate(pivotX, pivotY, pivotZ);
        poseStack.scale((float) scale, (float) scale, (float) scale);
        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        stack.push(Boolean.TRUE);
    }

    @Unique
    private static double pym$containingScale(final Entity entity, final float partialTick) {
        if (!(entity instanceof ArmorStand || entity instanceof HangingEntity || entity instanceof Display)) return 1.0D;
        if (!(Sable.HELPER.getContaining(entity) instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
            return 1.0D;
        }
        final double scale = subLevel.renderPose(partialTick).scale().x();
        return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFF" +
                    "Lcom/mojang/blaze3d/vertex/PoseStack;" +
                    "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN")
    )
    private void pym$popInheritedScale(
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
        final ArrayDeque<Boolean> stack = pym$SCALE_STACK.get();
        if (!stack.isEmpty() && stack.pop()) poseStack.popPose();
        if (stack.isEmpty()) pym$SCALE_STACK.remove();
    }
}
