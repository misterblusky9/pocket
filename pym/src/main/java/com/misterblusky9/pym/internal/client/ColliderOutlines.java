package com.misterblusky9.pym.internal.client;

import com.misterblusky9.pym.api.Pym;
import com.misterblusky9.pym.api.SubLevelShape;
import com.misterblusky9.pym.api.client.DebugOverlay;
import com.misterblusky9.pym.internal.collision.CollisionTrace;
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
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ColliderOutlines {
    public static final double RANGE = 64.0D;

    @FunctionalInterface
    public interface Extra {
        void draw(Minecraft minecraft, Vec3 camera, float partialTick, PoseStack poseStack, VertexConsumer lines);
    }

    private static final List<Extra> EXTRAS = new CopyOnWriteArrayList<>();

    private static final float[][] LOD_COLORS = {
            {1.0F, 0.10F, 0.10F}, {0.15F, 1.0F, 0.15F}, {0.10F, 0.85F, 1.0F},
            {0.35F, 0.45F, 1.0F}, {1.0F, 0.30F, 0.90F}, {1.0F, 0.85F, 0.10F}
    };

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
        boolean playerLod = DebugOverlay.showingPlayerLod();

        SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container != null) {
            for (SubLevel raw : container.getAllSubLevels()) {
                if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;
                var bounds = subLevel.boundingBox();
                if (bounds == null) continue;
                if (!playerLod && camera.distanceToSqr(
                        (bounds.minX() + bounds.maxX()) * 0.5D,
                        (bounds.minY() + bounds.maxY()) * 0.5D,
                        (bounds.minZ() + bounds.maxZ()) * 0.5D) > RANGE * RANGE) continue;

                Pose3dc pose = subLevel.renderPose();
                if (pose == null) continue;
                Iterable<SubLevelShape.Box> boxes;
                Map<SubLevelShape.Box, Integer> lods = Map.of();
                if (playerLod) {
                    lods = CollisionTrace.outlines(subLevel, minecraft.level);
                    boxes = lods.keySet();
                } else {
                    SubLevelShape shape = Pym.shape(subLevel);
                    if (shape == null) continue;
                    boxes = shape.boxes();
                }

                double ox = Math.floor(bounds.minX()), oy = Math.floor(bounds.minY()), oz = Math.floor(bounds.minZ());
                Vector3d origin = pose.transformPositionInverse(new Vector3d(ox, oy, oz));
                ox = Math.floor(origin.x);
                oy = Math.floor(origin.y);
                oz = Math.floor(origin.z);
                Matrix4d matrix = new Matrix4d().translation(-camera.x, -camera.y, -camera.z)
                        .mul(pose.bakeIntoMatrix(new Matrix4d())).translate(ox, oy, oz);
                poseStack.pushPose();
                poseStack.last().pose().mul(new Matrix4f(matrix));
                poseStack.last().normal().rotate(new Quaternionf(pose.orientation()));
                PoseStack.Pose last = poseStack.last();
                for (SubLevelShape.Box box : boxes) {
                    float[] color = LOD_COLORS[Math.max(0, Math.min(5, lods.getOrDefault(box, -1) + 1))];
                    local(last, lines, (float) (box.minX() - ox), (float) (box.minY() - oy), (float) (box.minZ() - oz),
                            (float) (box.maxX() - ox), (float) (box.maxY() - oy), (float) (box.maxZ() - oz), color);
                }
                poseStack.popPose();
            }
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        for (Extra extra : EXTRAS) extra.draw(minecraft, camera, partialTick, poseStack, lines);
        buffers.endBatch(RenderType.lines());
    }

    private static void local(PoseStack.Pose last, VertexConsumer lines, float x0, float y0, float z0,
                              float x1, float y1, float z1, float[] color) {
        for (int i = 0; i < 4; i++) {
            float y = (i & 1) == 0 ? y0 : y1, z = (i & 2) == 0 ? z0 : z1;
            line(last, lines, x0, y, z, x1, y, z, 1.0F, 0.0F, 0.0F, color);
            float x = (i & 1) == 0 ? x0 : x1;
            line(last, lines, x, y0, z, x, y1, z, 0.0F, 1.0F, 0.0F, color);
            line(last, lines, x, (i & 2) == 0 ? y0 : y1, z0, x, (i & 2) == 0 ? y0 : y1, z1, 0.0F, 0.0F, 1.0F, color);
        }
    }

    private static void line(PoseStack.Pose last, VertexConsumer lines, float x0, float y0, float z0,
                             float x1, float y1, float z1, float nx, float ny, float nz, float[] color) {
        lines.addVertex(last, x0, y0, z0).setColor(color[0], color[1], color[2], 0.92F).setNormal(last, nx, ny, nz);
        lines.addVertex(last, x1, y1, z1).setColor(color[0], color[1], color[2], 0.92F).setNormal(last, nx, ny, nz);
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
