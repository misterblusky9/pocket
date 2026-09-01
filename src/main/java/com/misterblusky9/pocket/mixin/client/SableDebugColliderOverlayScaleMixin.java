package com.misterblusky9.pocket.mixin.client;

import com.misterblusky9.pocket.PocketSized;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 800)
public abstract class SableDebugColliderOverlayScaleMixin {
    @Shadow private ClientLevel level;

    @Unique
    private static final ThreadLocal<Boolean> pocket$CORRECTED_SABLE_DEBUG =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private static void pocket$suppressUnscaledSablePlotBox(
            final PoseStack poseStack,
            final VertexConsumer consumer,
            final double minX,
            final double minY,
            final double minZ,
            final double maxX,
            final double maxY,
            final double maxZ,
            final float red,
            final float green,
            final float blue,
            final float alpha,
            final CallbackInfo ci
    ) {
        if (pocket$CORRECTED_SABLE_DEBUG.get()) return;
        if (!pocket$isSablePlotColor(red, green, blue, alpha)) return;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()
                || minecraft.showOnlyReducedInfo()) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        for (final SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final Pose3dc renderPose = subLevel.renderPose();
            final Vector3dc scale = renderPose.scale();
            if (pocket$isIdentity(scale)) continue;

            final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            final Vector3dc center = renderPose.rotationPoint();

            if (pocket$close(minX, bounds.minX() - center.x())
                    && pocket$close(minY, bounds.minY() - center.y())
                    && pocket$close(minZ, bounds.minZ() - center.z())
                    && pocket$close(maxX, bounds.maxX() + 1.0D - center.x())
                    && pocket$close(maxY, bounds.maxY() + 1.0D - center.y())
                    && pocket$close(maxZ, bounds.maxZ() + 1.0D - center.z())) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"), require = 1)
    private void pocket$drawScaledSablePlotBoxes(
            final DeltaTracker deltaTracker,
            final boolean renderBlockOutline,
            final Camera camera,
            final GameRenderer gameRenderer,
            final LightTexture lightTexture,
            final Matrix4f modelViewMatrix,
            final Matrix4f projectionMatrix,
            final CallbackInfo ci
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()
                || minecraft.showOnlyReducedInfo()) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;

        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer consumer = buffers.getBuffer(RenderType.LINES);
        final double cameraX = camera.getPosition().x;
        final double cameraY = camera.getPosition().y;
        final double cameraZ = camera.getPosition().z;

        final PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);

        pocket$CORRECTED_SABLE_DEBUG.set(Boolean.TRUE);
        try {
            for (final SubLevel raw : container.getAllSubLevels()) {
                if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

                final Pose3dc renderPose = subLevel.renderPose();
                final Vector3dc scale = renderPose.scale();
                if (pocket$isIdentity(scale)) continue;

                final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
                final Vector3dc globalCenter = renderPose.position();
                final Vector3dc localCenter = renderPose.rotationPoint();

                poseStack.pushPose();
                poseStack.translate(
                        globalCenter.x() - cameraX,
                        globalCenter.y() - cameraY,
                        globalCenter.z() - cameraZ
                );
                poseStack.mulPose(new Quaternionf(renderPose.orientation()));
                poseStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());

                LevelRenderer.renderLineBox(
                        poseStack,
                        consumer,
                        bounds.minX() - localCenter.x(),
                        bounds.minY() - localCenter.y(),
                        bounds.minZ() - localCenter.z(),
                        bounds.maxX() + 1.0D - localCenter.x(),
                        bounds.maxY() + 1.0D - localCenter.y(),
                        bounds.maxZ() + 1.0D - localCenter.z(),
                        0.9F,
                        0.5F,
                        0.5F,
                        1.0F
                );
                poseStack.popPose();
            }
        } finally {
            pocket$CORRECTED_SABLE_DEBUG.remove();
        }

        buffers.endLastBatch();
    }

    @Unique
    private static boolean pocket$isIdentity(final Vector3dc scale) {
        return Math.abs(scale.x() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.y() - 1.0D) <= PocketSized.EPSILON
                && Math.abs(scale.z() - 1.0D) <= PocketSized.EPSILON;
    }

    @Unique
    private static boolean pocket$isSablePlotColor(
            final float red,
            final float green,
            final float blue,
            final float alpha
    ) {
        return Math.abs(red - 0.9F) <= 1.0E-6F
                && Math.abs(green - 0.5F) <= 1.0E-6F
                && Math.abs(blue - 0.5F) <= 1.0E-6F
                && Math.abs(alpha - 1.0F) <= 1.0E-6F;
    }

    @Unique
    private static boolean pocket$close(final double a, final double b) {
        return Math.abs(a - b) <= 1.0E-6D;
    }
}
