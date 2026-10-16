package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.create.InteractiveContraption;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class SwitchBearingOutlineRenderer {
    private static final double SURFACE_EPSILON = 0.002D;

    private static final float RED = 0.0F;
    private static final float GREEN = 0.0F;
    private static final float BLUE = 0.0F;
    private static final float ALPHA = 0.40F;

    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) {
            return;
        }

        IntegratedContraptionRaycast.Hit hit = IntegratedContraptionRaycast.pick(player, ignored -> true);
        if (hit == null) {
            return;
        }

        AbstractContraptionEntity selectedEntity = hit.entity();
        InteractiveContraption selectedContraption = hit.contraption();
        AABB bounds = selectedContraption.getInteractionBounds();
        if (bounds == null) {
            return;
        }

        AABB renderBounds = bounds.inflate(SURFACE_EPSILON);
        Vec3 camera = event.getCamera().getPosition();
        Vector3d[] corners = corners(renderBounds);

        for (Vector3d corner : corners) {
            Vec3 world = IntegratedContraptionRaycast.toWorld(
                    selectedEntity,
                    new Vec3(corner.x, corner.y, corner.z),
                    hit.partialTick()
            );
            if (world == null) {
                return;
            }
            corner.set(world.x - camera.x, world.y - camera.y, world.z - camera.z);
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (int[] edge : EDGES) {
            line(poseStack, lines, corners[edge[0]], corners[edge[1]]);
        }

        buffers.endBatch(RenderType.lines());
    }

    private static Vector3d[] corners(AABB bounds) {
        Vector3d[] corners = new Vector3d[8];
        for (int i = 0; i < corners.length; i++) {
            corners[i] = new Vector3d(
                    (i & 1) == 0 ? bounds.minX : bounds.maxX,
                    (i & 2) == 0 ? bounds.minY : bounds.maxY,
                    (i & 4) == 0 ? bounds.minZ : bounds.maxZ
            );
        }
        return corners;
    }

    private static void line(
            PoseStack poseStack,
            VertexConsumer lines,
            Vector3d from,
            Vector3d to
    ) {
        PoseStack.Pose last = poseStack.last();
        Vector3f normal = new Vector3f(
                (float) (to.x - from.x),
                (float) (to.y - from.y),
                (float) (to.z - from.z)
        );
        if (normal.lengthSquared() <= 1.0E-12F) {
            return;
        }
        normal.normalize();

        lines.addVertex(last, (float) from.x, (float) from.y, (float) from.z)
                .setColor(RED, GREEN, BLUE, ALPHA)
                .setNormal(last, normal.x, normal.y, normal.z);
        lines.addVertex(last, (float) to.x, (float) to.y, (float) to.z)
                .setColor(RED, GREEN, BLUE, ALPHA)
                .setNormal(last, normal.x, normal.y, normal.z);
    }

    private SwitchBearingOutlineRenderer() {
    }
}
