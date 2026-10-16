package com.misterblusky9.pocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;

public final class MagnifyingGlassIndicator {
    private static final double HEIGHT = 0.5D;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !MagnifyingGlassZoom.isViewing()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) return;

        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        double limit = MagnifyingGlassZoom.viewerScale(minecraft.player) / 16.0D;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        boolean drawn = false;

        for (SubLevel candidate : container.getAllSubLevels()) {
            if (!(candidate instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            Pose3dc subLevelPose = subLevel.renderPose(partialTick);
            double scale = subLevelPose.scale().x();
            if (!Double.isFinite(scale) || scale <= 0.0D || scale >= limit) continue;

            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (bounds == null || bounds.minX() > bounds.maxX()
                    || bounds.minY() > bounds.maxY() || bounds.minZ() > bounds.maxZ()) continue;

            Vector3d top = subLevelPose.transformPosition(new Vector3d(
                    (bounds.minX() + (double) bounds.maxX() + 1.0D) * 0.5D,
                    bounds.maxY() + 1.0D,
                    (bounds.minZ() + (double) bounds.maxZ() + 1.0D) * 0.5D));
            if (!Double.isFinite(top.x) || !Double.isFinite(top.y) || !Double.isFinite(top.z)) continue;

            float x = (float) (top.x - camera.x);
            float y = (float) (top.y - camera.y);
            float z = (float) (top.z - camera.z);
            lines.addVertex(pose, x, y, z)
                    .setColor(1.0F, 0.1F, 0.1F, 0.9F)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
            lines.addVertex(pose, x, (float) (top.y + HEIGHT - camera.y), z)
                    .setColor(1.0F, 0.1F, 0.1F, 0.9F)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
            drawn = true;
        }

        if (drawn) buffers.endBatch(RenderType.lines());
    }

    private MagnifyingGlassIndicator() {}
}
