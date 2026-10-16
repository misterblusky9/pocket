package com.misterblusky9.pym.internal.mixin.client;

import com.misterblusky9.pym.api.ScaleBounds;
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
    private static final ThreadLocal<Boolean> pym$CORRECTED_SABLE_DEBUG =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(
            method = "renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFF)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private static void pym$suppressUnscaledSablePlotBox(
            PoseStack poseStack,
            VertexConsumer consumer,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo ci
    ) {
        if (pym$CORRECTED_SABLE_DEBUG.get()) return;
        if (!pym$isSablePlotColor(red, green, blue, alpha)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()
                || minecraft.showOnlyReducedInfo()) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        for (SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            Pose3dc renderPose = subLevel.renderPose();
            Vector3dc scale = renderPose.scale();
            if (pym$isIdentity(scale)) continue;

            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            Vector3dc center = renderPose.rotationPoint();

            if (pym$close(minX, bounds.minX() - center.x())
                    && pym$close(minY, bounds.minY() - center.y())
                    && pym$close(minZ, bounds.minZ() - center.z())
                    && pym$close(maxX, bounds.maxX() + 1.0D - center.x())
                    && pym$close(maxY, bounds.maxY() + 1.0D - center.y())
                    && pym$close(maxZ, bounds.maxZ() + 1.0D - center.z())) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"), require = 1)
    private void pym$drawScaledSablePlotBoxes(
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f modelViewMatrix,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes()
                || minecraft.showOnlyReducedInfo()) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container == null) return;

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.LINES);
        double cameraX = camera.getPosition().x;
        double cameraY = camera.getPosition().y;
        double cameraZ = camera.getPosition().z;

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(modelViewMatrix);

        pym$CORRECTED_SABLE_DEBUG.set(Boolean.TRUE);
        try {
            for (SubLevel raw : container.getAllSubLevels()) {
                if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

                Pose3dc renderPose = subLevel.renderPose();
                Vector3dc scale = renderPose.scale();
                if (pym$isIdentity(scale)) continue;

                BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
                Vector3dc globalCenter = renderPose.position();
                Vector3dc localCenter = renderPose.rotationPoint();

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
            pym$CORRECTED_SABLE_DEBUG.remove();
        }

        buffers.endLastBatch();
    }

    @Unique
    private static boolean pym$isIdentity(Vector3dc scale) {
        return Math.abs(scale.x() - 1.0D) <= ScaleBounds.EPSILON
                && Math.abs(scale.y() - 1.0D) <= ScaleBounds.EPSILON
                && Math.abs(scale.z() - 1.0D) <= ScaleBounds.EPSILON;
    }

    @Unique
    private static boolean pym$isSablePlotColor(
            float red,
            float green,
            float blue,
            float alpha
    ) {
        return Math.abs(red - 0.9F) <= 1.0E-6F
                && Math.abs(green - 0.5F) <= 1.0E-6F
                && Math.abs(blue - 0.5F) <= 1.0E-6F
                && Math.abs(alpha - 1.0F) <= 1.0E-6F;
    }

    @Unique
    private static boolean pym$close(double a, double b) {
        return Math.abs(a - b) <= 1.0E-6D;
    }
}
