package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.WeldContact;
import com.misterblusky9.pocket.compat.simulated.WeldGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllSpecialTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeldFaceRenderer {
    private static final double SURFACE_OFFSET = 1.0D / 128.0D;
    private static final RenderType GLUE = PonderRenderTypes.outlineTranslucent(
            AllSpecialTextures.GLUE.getLocation(), false);
    private static final RenderType GLUE_PANEL = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/block/hot_glue/hot_glue.png"));
    private static final Map<String, Face> FACES = new LinkedHashMap<>();
    private static final Map<String, Panel> PANELS = new LinkedHashMap<>();
    private static final Map<String, Pivot> PIVOTS = new LinkedHashMap<>();
    private static final Pose3d WORLD_POSE = new Pose3d();

    private record Panel(
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            WeldContactPatch.Edge rect,
            long expires
    ) {}

    private record Face(
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            WeldContact.Projection projection,
            List<WeldContactPatch.Edge> rects,
            int color,
            long expires
    ) {}

    private record Pivot(
            BlockPos targetPos,
            Vector3d center,
            double size,
            int color,
            long expires
    ) {}

    public static void show(
            final String key,
            final BlockPos targetPos,
            final Direction targetFacing,
            final Vector3d targetAnchor,
            final WeldContact.Projection projection,
            final List<WeldContactPatch.Edge> rects,
            final int color
    ) {
        FACES.put(key, new Face(
                targetPos,
                targetFacing,
                new Vector3d(targetAnchor),
                projection,
                List.copyOf(rects),
                color,
                PocketClientFrame.frame() + 1L));
    }

    public static void showGlue(
            final String key,
            final BlockPos targetPos,
            final Direction targetFacing,
            final Vector3d targetAnchor,
            final WeldContactPatch.Edge rect
    ) {
        PANELS.put(key, new Panel(
                targetPos,
                targetFacing,
                new Vector3d(targetAnchor),
                rect,
                PocketClientFrame.frame() + 1L));
    }

    public static void showPivot(
            final String key,
            final BlockPos targetPos,
            final Vector3d center,
            final double size,
            final int color
    ) {
        PIVOTS.put(key, new Pivot(
                targetPos,
                new Vector3d(center),
                Math.max(1.0E-4D, size),
                color,
                PocketClientFrame.frame() + 1L));
    }

    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || (FACES.isEmpty() && PANELS.isEmpty() && PIVOTS.isEmpty())) return;

        final long frame = PocketClientFrame.frame();
        FACES.entrySet().removeIf(entry -> entry.getValue().expires() < frame);
        PANELS.entrySet().removeIf(entry -> entry.getValue().expires() < frame);
        PIVOTS.entrySet().removeIf(entry -> entry.getValue().expires() < frame);
        if (FACES.isEmpty() && PANELS.isEmpty() && PIVOTS.isEmpty()) return;

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poses = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        if (!PANELS.isEmpty()) {
            final VertexConsumer panelConsumer = buffers.getBuffer(GLUE_PANEL);
            for (final Panel panel : PANELS.values()) {
                final SubLevel raw = Sable.HELPER.getContaining(minecraft.level, panel.targetPos());
                if (raw instanceof final ClientSubLevel subLevel) {
                    if (subLevel.isRemoved()) continue;
                    renderPanel(minecraft, panel, subLevel.renderPose(partialTick), camera, poses, panelConsumer);
                } else {
                    renderPanel(minecraft, panel, WORLD_POSE, camera, poses, panelConsumer);
                }
            }
            buffers.endBatch(GLUE_PANEL);
        }

        final VertexConsumer consumer = buffers.getBuffer(GLUE);

        for (final Face face : FACES.values()) {
            final SubLevel raw = Sable.HELPER.getContaining(minecraft.level, face.targetPos());
            if (raw instanceof final ClientSubLevel subLevel) {
                if (subLevel.isRemoved()) continue;
                renderFace(face, subLevel.renderPose(partialTick), camera, poses, consumer);
            } else {
                renderFace(face, WORLD_POSE, camera, poses, consumer);
            }
        }

        buffers.endBatch(GLUE);

        if (!PIVOTS.isEmpty()) {
            final VertexConsumer pivotConsumer = buffers.getBuffer(PocketRenderTypes.WELD_PIVOT);
            for (final Pivot pivot : PIVOTS.values()) {
                final SubLevel raw = Sable.HELPER.getContaining(minecraft.level, pivot.targetPos());
                if (!(raw instanceof final ClientSubLevel subLevel) || subLevel.isRemoved()) continue;
                renderPivot(pivot, subLevel.renderPose(partialTick), camera, poses, pivotConsumer);
            }
            buffers.endBatch(PocketRenderTypes.WELD_PIVOT);
        }
    }

    private static void renderFace(
            final Face face,
            final Pose3dc pose,
            final Vec3 camera,
            final PoseStack poses,
            final VertexConsumer consumer
    ) {
        final Direction.Axis targetU = WeldContact.uAxis(face.targetFacing());
        final Direction.Axis targetV = WeldContact.vAxis(face.targetFacing());
        final double anchorU = WeldContact.axisOf(face.targetAnchor(), targetU);
        final double anchorV = WeldContact.axisOf(face.targetAnchor(), targetV);
        final double scale = Math.max(1.0E-6D, Math.abs(pose.scale().x()));
        final double plane = WeldGeometry.facePlane(face.targetPos(), face.targetFacing())
                + (face.targetFacing().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D)
                * SURFACE_OFFSET / scale;

        final Vector3d normal = new Vector3d(
                face.targetFacing().getStepX(),
                face.targetFacing().getStepY(),
                face.targetFacing().getStepZ());
        pose.transformNormal(normal).normalize();

        final Color tint = new Color(face.color(), false);
        final float red = tint.getRedAsFloat();
        final float green = tint.getGreenAsFloat();
        final float blue = tint.getBlueAsFloat();

        for (final WeldContactPatch.Edge rect : face.rects()) {
            final double[] a = WeldContact.project(rect.u0(), rect.v0(), face.projection(), targetU, targetV);
            final double[] b = WeldContact.project(rect.u0(), rect.v1(), face.projection(), targetU, targetV);
            final double[] c = WeldContact.project(rect.u1(), rect.v1(), face.projection(), targetU, targetV);
            final double[] d = WeldContact.project(rect.u1(), rect.v0(), face.projection(), targetU, targetV);

            final Vector3d pa = world(face, pose, plane, anchorU + a[0], anchorV + a[1], camera);
            final Vector3d pb = world(face, pose, plane, anchorU + b[0], anchorV + b[1], camera);
            final Vector3d pc = world(face, pose, plane, anchorU + c[0], anchorV + c[1], camera);
            final Vector3d pd = world(face, pose, plane, anchorU + d[0], anchorV + d[1], camera);

            vertex(poses, consumer, pa, red, green, blue, (float) a[0], (float) a[1], normal);
            vertex(poses, consumer, pb, red, green, blue, (float) b[0], (float) b[1], normal);
            vertex(poses, consumer, pc, red, green, blue, (float) c[0], (float) c[1], normal);
            vertex(poses, consumer, pd, red, green, blue, (float) d[0], (float) d[1], normal);
        }
    }

    private static void renderPanel(
            final Minecraft minecraft,
            final Panel panel,
            final Pose3dc pose,
            final Vec3 camera,
            final PoseStack poses,
            final VertexConsumer consumer
    ) {
        final Direction facing = panel.targetFacing();
        final Direction.Axis targetU = WeldContact.uAxis(facing);
        final Direction.Axis targetV = WeldContact.vAxis(facing);
        final double anchorU = WeldContact.axisOf(panel.targetAnchor(), targetU);
        final double anchorV = WeldContact.axisOf(panel.targetAnchor(), targetV);
        final double scale = Math.max(1.0E-6D, Math.abs(pose.scale().x()));
        final double plane = WeldGeometry.facePlane(panel.targetPos(), facing)
                + (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D)
                * SURFACE_OFFSET / scale;

        final Vector3d normal = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        pose.transformNormal(normal).normalize();
        final int light = LevelRenderer.getLightColor(minecraft.level, panel.targetPos().relative(facing));

        final WeldContactPatch.Edge rect = panel.rect();
        final Vector3d pa = world(facing, pose, plane, anchorU + rect.u0(), anchorV + rect.v0(), camera);
        final Vector3d pb = world(facing, pose, plane, anchorU + rect.u0(), anchorV + rect.v1(), camera);
        final Vector3d pc = world(facing, pose, plane, anchorU + rect.u1(), anchorV + rect.v1(), camera);
        final Vector3d pd = world(facing, pose, plane, anchorU + rect.u1(), anchorV + rect.v0(), camera);

        panelVertex(poses, consumer, pa, 0.0F, 0.0F, light, normal);
        panelVertex(poses, consumer, pb, 0.0F, 1.0F, light, normal);
        panelVertex(poses, consumer, pc, 1.0F, 1.0F, light, normal);
        panelVertex(poses, consumer, pd, 1.0F, 0.0F, light, normal);
    }

    private static void panelVertex(
            final PoseStack poses,
            final VertexConsumer consumer,
            final Vector3d point,
            final float u,
            final float v,
            final int light,
            final Vector3d normal
    ) {
        final PoseStack.Pose last = poses.last();
        final Vector3f transformedNormal = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)
                .mul(last.normal())
                .normalize();
        consumer.addVertex(last.pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
    }

    private static void renderPivot(
            final Pivot pivot,
            final Pose3dc pose,
            final Vec3 camera,
            final PoseStack poses,
            final VertexConsumer consumer
    ) {
        final double half = pivot.size() * 0.5D;
        final Vector3d[] points = new Vector3d[8];
        int index = 0;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    final Vector3d point = new Vector3d(
                            pivot.center().x + x * half,
                            pivot.center().y + y * half,
                            pivot.center().z + z * half);
                    pose.transformPosition(point);
                    points[index++] = point.sub(camera.x, camera.y, camera.z);
                }
            }
        }

        final Color tint = new Color(pivot.color(), false);
        final float red = tint.getRedAsFloat();
        final float green = tint.getGreenAsFloat();
        final float blue = tint.getBlueAsFloat();
        final Matrix4f matrix = poses.last().pose();

        quad(consumer, matrix, points[0], points[4], points[6], points[2], red, green, blue);
        quad(consumer, matrix, points[1], points[3], points[7], points[5], red, green, blue);
        quad(consumer, matrix, points[0], points[1], points[5], points[4], red, green, blue);
        quad(consumer, matrix, points[2], points[6], points[7], points[3], red, green, blue);
        quad(consumer, matrix, points[0], points[2], points[3], points[1], red, green, blue);
        quad(consumer, matrix, points[4], points[5], points[7], points[6], red, green, blue);
    }

    private static void quad(
            final VertexConsumer consumer,
            final Matrix4f matrix,
            final Vector3d a,
            final Vector3d b,
            final Vector3d c,
            final Vector3d d,
            final float red,
            final float green,
            final float blue
    ) {
        pivotVertex(consumer, matrix, a, red, green, blue);
        pivotVertex(consumer, matrix, b, red, green, blue);
        pivotVertex(consumer, matrix, c, red, green, blue);
        pivotVertex(consumer, matrix, d, red, green, blue);
    }

    private static void pivotVertex(
            final VertexConsumer consumer,
            final Matrix4f matrix,
            final Vector3d point,
            final float red,
            final float green,
            final float blue
    ) {
        consumer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, 1.0F);
    }

    private static Vector3d world(
            final Face face,
            final Pose3dc pose,
            final double plane,
            final double u,
            final double v,
            final Vec3 camera
    ) {
        return world(face.targetFacing(), pose, plane, u, v, camera);
    }

    private static Vector3d world(
            final Direction facing,
            final Pose3dc pose,
            final double plane,
            final double u,
            final double v,
            final Vec3 camera
    ) {
        final Vector3d point = WeldGeometry.inPlane(facing, plane, u, v);
        pose.transformPosition(point);
        return point.sub(camera.x, camera.y, camera.z);
    }

    private static void vertex(
            final PoseStack poses,
            final VertexConsumer consumer,
            final Vector3d point,
            final float red,
            final float green,
            final float blue,
            final float u,
            final float v,
            final Vector3d normal
    ) {
        final PoseStack.Pose last = poses.last();
        final Matrix4f matrix = last.pose();
        final Matrix3f normalMatrix = last.normal();
        final Vector3f transformedNormal = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)
                .mul(normalMatrix)
                .normalize();
        consumer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, 0.5F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
    }

    public static void clear() {
        FACES.clear();
        PANELS.clear();
        PIVOTS.clear();
    }

    private WeldFaceRenderer() {}
}
