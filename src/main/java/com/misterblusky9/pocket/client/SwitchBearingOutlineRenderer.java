package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.create.SwitchContraption;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import dev.ryanhcode.sable.ActiveSableCompanion;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Optional;

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

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) {
            return;
        }

        final Couple<Vec3> rayInputs = ContraptionHandlerClient.getRayInputs(player);
        final Vec3 origin = rayInputs.getFirst();
        final Vec3 target = rayInputs.getSecond();

        AbstractContraptionEntity selectedEntity = null;
        SwitchContraption selectedContraption = null;
        double selectedDistanceSqr = Double.MAX_VALUE;

        for (final Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof final AbstractContraptionEntity contraptionEntity)
                    || contraptionEntity.isRemoved()
                    || !(contraptionEntity.getContraption() instanceof final SwitchContraption switchContraption)) {
                continue;
            }

            final AABB bounds = switchContraption.getInteractionBounds();
            if (bounds == null) {
                continue;
            }

            final Vec3 localOrigin = toContraptionLocal(contraptionEntity, origin);
            final Vec3 localTarget = toContraptionLocal(contraptionEntity, target);
            final Optional<Vec3> localHit = bounds.clip(localOrigin, localTarget);
            if (localHit.isEmpty()) {
                continue;
            }

            final Vec3 plotHit = contraptionEntity.toGlobalVector(localHit.get(), 1.0F);
            final Vec3 worldHit = projectOutOfContainingSubLevel(contraptionEntity, plotHit, false, 1.0F);
            final double distanceSqr = Sable.HELPER.distanceSquaredWithSubLevels(
                    minecraft.level,
                    origin,
                    worldHit
            );

            if (distanceSqr >= selectedDistanceSqr) {
                continue;
            }

            selectedDistanceSqr = distanceSqr;
            selectedEntity = contraptionEntity;
            selectedContraption = switchContraption;
        }

        if (selectedEntity == null || selectedContraption == null) {
            return;
        }

        final AABB renderBounds = selectedContraption.getInteractionBounds().inflate(SURFACE_EPSILON);
        final float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        final Vector3d[] corners = corners(renderBounds);
        final SubLevel containing = Sable.HELPER.getContaining(selectedEntity);
        final Pose3dc renderPose = containing instanceof final ClientSubLevel clientSubLevel && !clientSubLevel.isRemoved()
                ? clientSubLevel.renderPose(partialTick)
                : null;

        for (final Vector3d corner : corners) {
            final Vec3 plotPosition = selectedEntity.toGlobalVector(
                    new Vec3(corner.x, corner.y, corner.z),
                    partialTick
            );
            corner.set(plotPosition.x, plotPosition.y, plotPosition.z);

            if (renderPose != null) {
                renderPose.transformPosition(corner);
            }

            corner.sub(camera.x, camera.y, camera.z);
        }

        for (final int[] edge : EDGES) {
            line(poseStack, lines, corners[edge[0]], corners[edge[1]]);
        }

        buffers.endBatch(RenderType.lines());
    }

    private static Vec3 toContraptionLocal(
            final AbstractContraptionEntity contraptionEntity,
            Vec3 point
    ) {
        final ActiveSableCompanion helper = Sable.HELPER;
        final SubLevel pointSubLevel = helper.getContaining(contraptionEntity.level(), point);
        final SubLevel contraptionSubLevel = helper.getContaining(contraptionEntity);

        if (contraptionSubLevel != pointSubLevel) {
            if (pointSubLevel != null) {
                point = pointSubLevel.logicalPose().transformPosition(point);
            }
            if (contraptionSubLevel != null) {
                point = contraptionSubLevel.logicalPose().transformPositionInverse(point);
            }
        }

        return contraptionEntity.toLocalVector(point, 1.0F);
    }

    private static Vec3 projectOutOfContainingSubLevel(
            final AbstractContraptionEntity contraptionEntity,
            final Vec3 point,
            final boolean renderPose,
            final float partialTick
    ) {
        final SubLevel containing = Sable.HELPER.getContaining(contraptionEntity);
        if (containing == null) {
            return point;
        }

        final Vector3d projected = new Vector3d(point.x, point.y, point.z);
        if (renderPose && containing instanceof final ClientSubLevel clientSubLevel) {
            clientSubLevel.renderPose(partialTick).transformPosition(projected);
        } else {
            containing.logicalPose().transformPosition(projected);
        }
        return new Vec3(projected.x, projected.y, projected.z);
    }

    private static Vector3d[] corners(final AABB bounds) {
        final Vector3d[] corners = new Vector3d[8];
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
            final PoseStack poseStack,
            final VertexConsumer lines,
            final Vector3d from,
            final Vector3d to
    ) {
        final PoseStack.Pose last = poseStack.last();
        final Vector3f normal = new Vector3f(
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

    private SwitchBearingOutlineRenderer() {}
}
