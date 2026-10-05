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

    public static void extend(final Extra extra) {
        EXTRAS.add(extra);
    }

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !DebugOverlay.showingColliders()) return;

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        final Vector3d[] corners = corners();

        final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container != null) {
            for (final SubLevel raw : container.getAllSubLevels()) {
                if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;
                final var bounds = subLevel.boundingBox();
                if (bounds == null || camera.distanceToSqr(
                        (bounds.minX() + bounds.maxX()) * 0.5D,
                        (bounds.minY() + bounds.maxY()) * 0.5D,
                        (bounds.minZ() + bounds.maxZ()) * 0.5D) > RANGE * RANGE) continue;

                final SubLevelShape shape = Pym.shape(subLevel);
                final Pose3dc pose = subLevel.renderPose();
                if (shape == null || pose == null) continue;

                for (final SubLevelShape.Box box : shape.boxes()) {
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

        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        for (final Extra extra : EXTRAS) extra.draw(minecraft, camera, partialTick, poseStack, lines);
        buffers.endBatch(RenderType.lines());
    }

    public static Vector3d[] corners() {
        final Vector3d[] result = new Vector3d[8];
        for (int i = 0; i < result.length; i++) result[i] = new Vector3d();
        return result;
    }

    public static void box(
            final PoseStack poseStack,
            final VertexConsumer lines,
            final Vector3d[] corners,
            final float red,
            final float green,
            final float blue
    ) {
        final PoseStack.Pose last = poseStack.last();
        for (final int[] edge : EDGES) {
            final Vector3d from = corners[edge[0]];
            final Vector3d to = corners[edge[1]];
            final Vector3f normal = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
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
