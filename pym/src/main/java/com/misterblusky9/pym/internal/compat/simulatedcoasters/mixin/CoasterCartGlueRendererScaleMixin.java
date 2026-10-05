package com.misterblusky9.pym.internal.compat.simulatedcoasters.mixin;

import com.misterblusky9.pym.internal.compat.simulatedcoasters.CoasterCartScaleContext;
import dev.ryanhcode.sable.companion.math.Pose3d;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "dev.silvergold.simulatedcoasters.client.cart.CoasterCartGlueRenderer", remap = false)
public abstract class CoasterCartGlueRendererScaleMixin {
    private static final String PYM$RENDER_CELL =
            "renderCell("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Ldev/silvergold/simulatedcoasters/client/cart/CoasterCartClientFrameCache$RenderFrame;"
                    + "DDD"
                    + "Lnet/minecraft/core/BlockPos$MutableBlockPos;)V";

    private static final String PYM$RENDER_CROSS =
            "renderGlueCross("
                    + "Lorg/joml/Matrix4f;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;I)V";

    private static final String PYM$RENDER_STRANDS =
            "renderGlueStrands("
                    + "Lorg/joml/Matrix4f;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;I)V";

    @Inject(method = PYM$RENDER_CELL, at = @At("HEAD"), remap = false, require = 1)
    private static void pym$enterGlueRender(
            final PoseStack poseStack,
            final MultiBufferSource.BufferSource buffers,
            final Level level,
            @Coerce final Object renderFrame,
            final double cameraX,
            final double cameraY,
            final double cameraZ,
            final BlockPos.MutableBlockPos cellPos,
            final CallbackInfo ci
    ) {
        CoasterCartScaleContext.push(pym$frameScale(renderFrame));
    }

    @ModifyConstant(
            method = PYM$RENDER_CELL,
            constant = @Constant(floatValue = 0.25F),
            remap = false,
            require = 1
    )
    private static float pym$scaleRailSeamOffset(final float original) {
        return (float) (original * CoasterCartScaleContext.current());
    }

    @ModifyConstant(
            method = PYM$RENDER_CELL,
            constant = @Constant(floatValue = 0.5F),
            remap = false,
            require = 2
    )
    private static float pym$scaleBodySeamPlanarOffset(final float original) {
        return (float) (original * CoasterCartScaleContext.current());
    }

    @ModifyConstant(
            method = PYM$RENDER_CELL,
            constant = @Constant(floatValue = 0.75F),
            remap = false,
            require = 1
    )
    private static float pym$scaleBodySeamHeight(final float original) {
        return (float) (original * CoasterCartScaleContext.current());
    }

    @ModifyConstant(
            method = PYM$RENDER_STRANDS,
            constant = @Constant(doubleValue = 0.75D),
            remap = false,
            require = 4
    )
    private static double pym$scaleStrandSpread(final double original) {
        return original * CoasterCartScaleContext.current();
    }

    @ModifyArg(
            method = PYM$RENDER_CROSS,
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/silvergold/simulatedcoasters/client/cart/CoasterCartGlueRenderer;scaled(Lnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 1,
            remap = false,
            require = 4
    )
    private static double pym$scaleGlueHalfWidth(final double original) {
        return original * CoasterCartScaleContext.current();
    }

    @Inject(method = PYM$RENDER_CELL, at = @At("RETURN"), remap = false, require = 1)
    private static void pym$exitGlueRender(
            final PoseStack poseStack,
            final MultiBufferSource.BufferSource buffers,
            final Level level,
            @Coerce final Object renderFrame,
            final double cameraX,
            final double cameraY,
            final double cameraZ,
            final BlockPos.MutableBlockPos cellPos,
            final CallbackInfo ci
    ) {
        CoasterCartScaleContext.pop();
    }

    private static double pym$frameScale(final Object renderFrame) {
        if (renderFrame == null) return 1.0D;
        try {
            final Method visualPose = renderFrame.getClass().getMethod("visualPose");
            final Object value = visualPose.invoke(renderFrame);
            if (!(value instanceof Pose3d pose)) return 1.0D;
            final double scale = pose.scale().x();
            return Double.isFinite(scale) && scale > 0.0D ? scale : 1.0D;
        } catch (ReflectiveOperationException ignored) {
            return 1.0D;
        }
    }
}
