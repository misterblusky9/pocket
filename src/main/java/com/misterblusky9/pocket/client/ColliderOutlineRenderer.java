package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.debug.ColliderDebugLayers;
import com.misterblusky9.pocket.item.ColliderWandItem;
import com.misterblusky9.pocket.physics.ColliderCoordinator;
import com.misterblusky9.pocket.physics.CompiledCollider;
import com.misterblusky9.pocket.physics.ColliderShapeKey;
import com.misterblusky9.pocket.physics.PlotShape;
import com.misterblusky9.pocket.physics.PlotShapeCache;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

public final class ColliderOutlineRenderer {
    private static final double RANGE = 64.0D;

    private static final double WHOLE_BLOCK_EPSILON = 1.0E-6D;

    private static final int[][] EDGES = {
            {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
            {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (!holdingWand(player)) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return;

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

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

            LevelRenderer.renderLineBox(
                    poseStack, lines,
                    bounds.minX() - camera.x, bounds.minY() - camera.y, bounds.minZ() - camera.z,
                    bounds.maxX() - camera.x, bounds.maxY() - camera.y, bounds.maxZ() - camera.z,
                    1.0F, 0.85F, 0.1F, 0.35F);

            drawShape(poseStack, lines, subLevel, camera);
        }

        buffers.endBatch(RenderType.lines());
    }

    private static boolean holdingWand(final LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof ColliderWandItem
                || player.getOffhandItem().getItem() instanceof ColliderWandItem;
    }

    private static void drawShape(
            final PoseStack poseStack,
            final VertexConsumer lines,
            final ClientSubLevel subLevel,
            final Vec3 camera
    ) {
        final ColliderDebugLayers layers = ColliderDebugLayers.current();
        final PlotShape shape = PlotShapeCache.get(subLevel);
        final Pose3dc renderPose = subLevel.renderPose();
        final Pose3dc logicalPose = subLevel.logicalPose();
        if (renderPose == null || logicalPose == null) return;

        final Vector3d[] corners = new Vector3d[8];
        for (int i = 0; i < corners.length; i++) corners[i] = new Vector3d();

        if (shape != null && layers.showsIdeal()) for (final PlotShape.Box box : shape.boxes()) {
            for (int i = 0; i < corners.length; i++) {
                corners[i].set(
                        (i & 1) == 0 ? box.minX() : box.maxX(),
                        (i & 2) == 0 ? box.minY() : box.maxY(),
                        (i & 4) == 0 ? box.minZ() : box.maxZ());

                renderPose.transformPosition(corners[i]).sub(camera.x, camera.y, camera.z);
            }

            final boolean partial = !wholeBlocks(box);
            final float red = partial ? 0.2F : 0.25F;
            final float green = partial ? 0.95F : 1.0F;
            final float blue = partial ? 1.0F : 0.35F;

            for (final int[] edge : EDGES) {
                line(poseStack, lines, corners[edge[0]], corners[edge[1]], red, green, blue);
            }
        }

        final boolean posesDiffer = layers == ColliderDebugLayers.ENTITY
                || !samePose(renderPose, logicalPose);
        if (shape != null && layers.showsEntity() && posesDiffer) for (final PlotShape.Box box : shape.boxes()) {
            for (int i = 0; i < corners.length; i++) {
                corners[i].set(
                        (i & 1) == 0 ? box.minX() : box.maxX(),
                        (i & 2) == 0 ? box.minY() : box.maxY(),
                        (i & 4) == 0 ? box.minZ() : box.maxZ());
                logicalPose.transformPosition(corners[i]).sub(camera.x, camera.y, camera.z);
            }
            for (final int[] edge : EDGES) {
                line(poseStack, lines, corners[edge[0]], corners[edge[1]], 1.0F, 0.55F, 0.08F);
            }
        }

        if (!layers.showsCollider()) return;

        final CompiledCollider real = ColliderCoordinator.current(subLevel.getUniqueId());
        if (real == null) {
            drawFallbackBounds(poseStack, lines, subLevel, logicalPose, corners, camera);
            return;
        }

        final boolean prism = real.mode() == CompiledCollider.Mode.PRISM_FALLBACK;
        final float red = 1.0F;
        final float green = prism ? 1.0F : 0.2F;
        final float blue = prism ? 1.0F : 0.15F;

        for (final CompiledCollider.Cell cell : real.cells()) {
            final float cellRed = cell.approximate() ? 1.0F : red;
            final float cellGreen = cell.approximate() ? 0.55F : green;
            final float cellBlue = cell.approximate() ? 0.05F : blue;
            for (final ColliderShapeKey.Face face : cell.shape().faces()) {
                for (int i = 0; i < corners.length; i++) {
                    corners[i].set(
                            cell.x() + ((i & 1) == 0 ? face.minXd() : face.maxXd()) - real.pivotX(),
                            cell.y() + ((i & 2) == 0 ? face.minYd() : face.maxYd()) - real.pivotY(),
                            cell.z() + ((i & 4) == 0 ? face.minZd() : face.maxZd()) - real.pivotZ());
                    logicalPose.orientation().transform(corners[i]);
                    corners[i].add(logicalPose.position()).sub(camera.x, camera.y, camera.z);
                }
                for (final int[] edge : EDGES) {
                    line(poseStack, lines, corners[edge[0]], corners[edge[1]], cellRed, cellGreen, cellBlue);
                }
            }
        }
    }

    private static void drawFallbackBounds(
            final PoseStack poseStack,
            final VertexConsumer lines,
            final ClientSubLevel subLevel,
            final Pose3dc logicalPose,
            final Vector3d[] corners,
            final Vec3 camera
    ) {
        final BoundingBox3ic bounds = subLevel.getPlot() == null ? null : subLevel.getPlot().getBoundingBox();
        if (bounds == null) return;
        for (int i = 0; i < corners.length; i++) {
            corners[i].set(
                    (i & 1) == 0 ? bounds.minX() : bounds.maxX() + 1.0D,
                    (i & 2) == 0 ? bounds.minY() : bounds.maxY() + 1.0D,
                    (i & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1.0D);
            logicalPose.transformPosition(corners[i]).sub(camera.x, camera.y, camera.z);
        }
        for (final int[] edge : EDGES) {
            line(poseStack, lines, corners[edge[0]], corners[edge[1]], 1.0F, 0.1F, 0.9F);
        }
    }

    private static boolean samePose(final Pose3dc render, final Pose3dc logical) {
        final Vector3dc renderAt = render.position();
        final Vector3dc logicalAt = logical.position();
        return renderAt != null && logicalAt != null
                && renderAt.distanceSquared(logicalAt) < 1.0E-8D
                && Math.abs(render.scale().x() - logical.scale().x()) < 1.0E-9D;
    }

    public static void clear() {
    }

    private static void line(
            final PoseStack poseStack,
            final VertexConsumer lines,
            final Vector3d from,
            final Vector3d to,
            final float red,
            final float green,
            final float blue
    ) {
        final PoseStack.Pose last = poseStack.last();

        final Vector3f normal = new Vector3f(
                (float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
        if (normal.lengthSquared() <= 1.0E-12F) return;
        normal.normalize();

        lines.addVertex(last, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, 0.9F)
                .setNormal(last, normal.x, normal.y, normal.z);
        lines.addVertex(last, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, 0.9F)
                .setNormal(last, normal.x, normal.y, normal.z);
    }

    private static boolean wholeBlocks(final PlotShape.Box box) {
        return whole(box.minX()) && whole(box.minY()) && whole(box.minZ())
                && whole(box.maxX()) && whole(box.maxY()) && whole(box.maxZ());
    }

    private static boolean whole(final double value) {
        return Math.abs(value - Math.round(value)) <= WHOLE_BLOCK_EPSILON;
    }

    private ColliderOutlineRenderer() {}
}
