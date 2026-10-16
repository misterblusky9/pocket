package com.misterblusky9.pym.internal.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.SubLevelShape;
import com.misterblusky9.pym.api.client.DebugOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ColliderOutlines {
    public static final double RANGE = 64.0D;

    @FunctionalInterface
    public interface Extra {
        void draw(Minecraft minecraft, Vec3 camera, float partialTick, PoseStack poseStack, VertexConsumer lines);
    }

    private static final List<Extra> EXTRAS = new CopyOnWriteArrayList<>();

    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    public static void extend(Extra extra) {
        EXTRAS.add(extra);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !DebugOverlay.showingColliders()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vector3d[] corners = corners();

        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container != null) {
            for (SubLevel raw : container.getAllSubLevels()) {
                if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;
                var bounds = subLevel.boundingBox();
                if (bounds == null || camera.distanceToSqr(
                        (bounds.minX() + bounds.maxX()) * 0.5D,
                        (bounds.minY() + bounds.maxY()) * 0.5D,
                        (bounds.minZ() + bounds.maxZ()) * 0.5D) > RANGE * RANGE) continue;

                SubLevelShape shape = Pym.shape(subLevel);
                Pose3dc pose = subLevel.renderPose();
                if (shape == null || pose == null) continue;

                for (SubLevelShape.Box box : shape.boxes()) {
                    for (int i = 0; i < corners.length; i++) {
                        corners[i].set(
                                (i & 1) == 0 ? box.minX() : box.maxX(),
                                (i & 2) == 0 ? box.minY() : box.maxY(),
                                (i & 4) == 0 ? box.minZ() : box.maxZ());
                        pose.transformPosition(corners[i]).sub(camera.x, camera.y, camera.z);
                    }
                    box(poseStack, lines, corners, 1.0F, 0.10F, 0.10F);
                }
            }
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        for (Extra extra : EXTRAS) extra.draw(minecraft, camera, partialTick, poseStack, lines);
        buffers.endBatch(RenderType.lines());
    }

    public static Vector3d[] corners() {
        Vector3d[] result = new Vector3d[8];
        for (int i = 0; i < result.length; i++) result[i] = new Vector3d();
        return result;
    }

    public static void box(
            PoseStack poseStack,
            VertexConsumer lines,
            Vector3d[] corners,
            float red,
            float green,
            float blue
    ) {
        PoseStack.Pose last = poseStack.last();
        for (int[] edge : EDGES) {
            Vector3d from = corners[edge[0]];
            Vector3d to = corners[edge[1]];
            Vector3f normal = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
            if (normal.lengthSquared() <= 1.0E-12F) continue;
            normal.normalize();
            lines.addVertex(last, (float) from.x, (float) from.y, (float) from.z)
                    .setColor(red, green, blue, 0.92F)
                    .setNormal(last, normal.x, normal.y, normal.z);
            lines.addVertex(last, (float) to.x, (float) to.y, (float) to.z)
                    .setColor(red, green, blue, 0.92F)
                    .setNormal(last, normal.x, normal.y, normal.z);
        }
    }

    private ColliderOutlines() {}
}
