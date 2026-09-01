package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4fc;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(value = EntityRenderDispatcher.class, priority = 800)
public abstract class CreateContraptionDebugHitboxScaleMixin {
    @Unique
    private static final ThreadLocal<ArrayDeque<Boolean>> pocket$DEBUG_SCALE_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/entity/Entity;FFFF)V",
            at = @At("HEAD"),
            require = 1
    )
    private static void pocket$pushCreateDebugScale(
            final PoseStack poseStack,
            final VertexConsumer consumer,
            final Entity entity,
            final float partialTick,
            final float red,
            final float green,
            final float blue,
            final CallbackInfo ci
    ) {
        final ArrayDeque<Boolean> stack = pocket$DEBUG_SCALE_STACK.get();

        if (!(entity instanceof AbstractContraptionEntity)) {
            stack.push(Boolean.FALSE);
            return;
        }

        final SubLevel raw = Sable.HELPER.getContaining(entity);
        if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) {
            stack.push(Boolean.FALSE);
            return;
        }

        final Vector3dc scale = subLevel.renderPose(partialTick).scale();
        if (!PocketSized.isValidScale(scale.x())
                || Math.abs(scale.x() - scale.y()) > PocketSized.EPSILON
                || Math.abs(scale.x() - scale.z()) > PocketSized.EPSILON
                || Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON) {
            stack.push(Boolean.FALSE);
            return;
        }

        final double matrixScale = pocket$matrixScale(poseStack.last().pose());
        if (Math.abs(matrixScale - scale.x()) <= 1.0E-3D
                || Math.abs(matrixScale - 1.0D) > 1.0E-3D) {
            stack.push(Boolean.FALSE);
            return;
        }

        final float s = (float) scale.x();
        poseStack.pushPose();
        poseStack.scale(s, s, s);
        stack.push(Boolean.TRUE);
    }

    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/entity/Entity;FFFF)V",
            at = @At("RETURN"),
            require = 1
    )
    private static void pocket$popCreateDebugScale(
            final PoseStack poseStack,
            final VertexConsumer consumer,
            final Entity entity,
            final float partialTick,
            final float red,
            final float green,
            final float blue,
            final CallbackInfo ci
    ) {
        final ArrayDeque<Boolean> stack = pocket$DEBUG_SCALE_STACK.get();
        if (!stack.isEmpty() && stack.pop()) {
            poseStack.popPose();
        }
        if (stack.isEmpty()) {
            pocket$DEBUG_SCALE_STACK.remove();
        }
    }

    @Unique
    private static double pocket$matrixScale(final Matrix4fc matrix) {
        return Math.sqrt(
                (double) matrix.m00() * matrix.m00()
                        + (double) matrix.m01() * matrix.m01()
                        + (double) matrix.m02() * matrix.m02()
        );
    }
}
