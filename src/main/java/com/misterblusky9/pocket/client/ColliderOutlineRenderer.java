package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.item.ColliderWandItem;
import com.misterblusky9.pocket.physics.PlotShape;
import com.misterblusky9.pocket.physics.PlotShapeCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ColliderOutlineRenderer {
    private static final double RANGE = 64.0D;

    private static final float RED = 1.0F;
    private static final float GREEN = 0.10F;
    private static final float BLUE = 0.10F;

    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !holdingWand(player)) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        final Vector3d[] corners = corners();
        for (final SubLevel raw : container.getAllSubLevels()) {
            if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;

            final var bounds = subLevel.boundingBox();
            if (bounds == null) continue;
            if (camera.distanceToSqr(
                    (bounds.minX() + bounds.maxX()) * 0.5D,
                    (bounds.minY() + bounds.maxY()) * 0.5D,
                    (bounds.minZ() + bounds.maxZ()) * 0.5D) > RANGE * RANGE) {
                continue;
            }

            final PlotShape shape = PlotShapeCache.get(subLevel);
            final Pose3dc pose = subLevel.renderPose();
            if (shape == null || pose == null) continue;

            for (final PlotShape.Box box : shape.boxes()) {
                fill(corners, box);
                for (final Vector3d corner : corners) {
                    pose.transformPosition(corner).sub(camera.x, camera.y, camera.z);
                }
                for (final int[] edge : EDGES) {
                    line(poseStack, lines, corners[edge[0]], corners[edge[1]]);
                }
            }
        }

        buffers.endBatch(RenderType.lines());
    }

    private static Vector3d[] corners() {
        final Vector3d[] result = new Vector3d[8];
        for (int i = 0; i < result.length; i++) result[i] = new Vector3d();
        return result;
    }

    private static void fill(final Vector3d[] corners, final PlotShape.Box box) {
        for (int i = 0; i < corners.length; i++) {
            corners[i].set(
                    (i & 1) == 0 ? box.minX() : box.maxX(),
                    (i & 2) == 0 ? box.minY() : box.maxY(),
                    (i & 4) == 0 ? box.minZ() : box.maxZ());
        }
    }

    private static void line(
            final PoseStack poseStack,
            final VertexConsumer lines,
            final Vector3d from,
            final Vector3d to
    ) {
        final PoseStack.Pose last = poseStack.last();
        final Vector3f normal = new Vector3f(
                (float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() <= 1.0E-12F) return;
        normal.normalize();

        lines.addVertex(last, (float) from.x, (float) from.y, (float) from.z)
                .setColor(RED, GREEN, BLUE, 0.92F)
                .setNormal(last, normal.x, normal.y, normal.z);
        lines.addVertex(last, (float) to.x, (float) to.y, (float) to.z)
                .setColor(RED, GREEN, BLUE, 0.92F)
                .setNormal(last, normal.x, normal.y, normal.z);
    }

    private static boolean holdingWand(final LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof ColliderWandItem
                || player.getOffhandItem().getItem() instanceof ColliderWandItem;
    }

    public static void clear() {}

    private ColliderOutlineRenderer() {}
}
