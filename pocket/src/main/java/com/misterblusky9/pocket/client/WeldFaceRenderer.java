package com.misterblusky9.pocket.client;

import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.misterblusky9.pym.api.client.RenderFrame;
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

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
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
            String key,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            WeldContact.Projection projection,
            List<WeldContactPatch.Edge> rects,
            int color
    ) {
        FACES.put(key, new Face(
                targetPos,
                targetFacing,
                new Vector3d(targetAnchor),
                projection,
                List.copyOf(rects),
                color,
                RenderFrame.frame() + 1L));
    }

    public static void showGlue(
            String key,
            BlockPos targetPos,
            Direction targetFacing,
            Vector3d targetAnchor,
            WeldContactPatch.Edge rect
    ) {
        PANELS.put(key, new Panel(
                targetPos,
                targetFacing,
                new Vector3d(targetAnchor),
                rect,
                RenderFrame.frame() + 1L));
    }

    public static void showPivot(
            String key,
            BlockPos targetPos,
            Vector3d center,
            double size,
            int color
    ) {
        PIVOTS.put(key, new Pivot(
                targetPos,
                new Vector3d(center),
                Math.max(1.0E-4D, size),
                color,
                RenderFrame.frame() + 1L));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || (FACES.isEmpty() && PANELS.isEmpty() && PIVOTS.isEmpty())) return;

        long frame = RenderFrame.frame();
        FACES.entrySet().removeIf(entry -> entry.getValue().expires() < frame);
        PANELS.entrySet().removeIf(entry -> entry.getValue().expires() < frame);
        PIVOTS.entrySet().removeIf(entry -> entry.getValue().expires() < frame);
        if (FACES.isEmpty() && PANELS.isEmpty() && PIVOTS.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        if (!PANELS.isEmpty()) {
            VertexConsumer panelConsumer = buffers.getBuffer(GLUE_PANEL);
            for (Panel panel : PANELS.values()) {
                SubLevel raw = Sable.HELPER.getContaining(minecraft.level, panel.targetPos());
                if (raw instanceof ClientSubLevel subLevel) {
                    if (subLevel.isRemoved()) continue;
                    renderPanel(minecraft, panel, subLevel.renderPose(partialTick), camera, poses, panelConsumer);
                } else {
                    renderPanel(minecraft, panel, WORLD_POSE, camera, poses, panelConsumer);
                }
            }
            buffers.endBatch(GLUE_PANEL);
        }

        VertexConsumer consumer = buffers.getBuffer(GLUE);

        for (Face face : FACES.values()) {
            SubLevel raw = Sable.HELPER.getContaining(minecraft.level, face.targetPos());
            if (raw instanceof ClientSubLevel subLevel) {
                if (subLevel.isRemoved()) continue;
                renderFace(face, subLevel.renderPose(partialTick), camera, poses, consumer);
            } else {
                renderFace(face, WORLD_POSE, camera, poses, consumer);
            }
        }

        buffers.endBatch(GLUE);

        if (!PIVOTS.isEmpty()) {
            VertexConsumer pivotConsumer = buffers.getBuffer(PocketRenderTypes.WELD_PIVOT);
            for (Pivot pivot : PIVOTS.values()) {
                SubLevel raw = Sable.HELPER.getContaining(minecraft.level, pivot.targetPos());
                if (!(raw instanceof ClientSubLevel subLevel) || subLevel.isRemoved()) continue;
                renderPivot(pivot, subLevel.renderPose(partialTick), camera, poses, pivotConsumer);
            }
            buffers.endBatch(PocketRenderTypes.WELD_PIVOT);
        }
    }

    private static void renderFace(
            Face face,
            Pose3dc pose,
            Vec3 camera,
            PoseStack poses,
            VertexConsumer consumer
    ) {
        Direction.Axis targetU = WeldContact.uAxis(face.targetFacing());
        Direction.Axis targetV = WeldContact.vAxis(face.targetFacing());
        double anchorU = WeldContact.axisOf(face.targetAnchor(), targetU);
        double anchorV = WeldContact.axisOf(face.targetAnchor(), targetV);
        double scale = Math.max(1.0E-6D, Math.abs(pose.scale().x()));
        double plane = WeldGeometry.facePlane(face.targetPos(), face.targetFacing())
                + (face.targetFacing().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D)
                * SURFACE_OFFSET / scale;

        Vector3d normal = new Vector3d(
                face.targetFacing().getStepX(),
                face.targetFacing().getStepY(),
                face.targetFacing().getStepZ());
        pose.transformNormal(normal).normalize();

        Color tint = new Color(face.color(), false);
        float red = tint.getRedAsFloat();
        float green = tint.getGreenAsFloat();
        float blue = tint.getBlueAsFloat();

        for (WeldContactPatch.Edge rect : face.rects()) {
            double[] a = WeldContact.project(rect.u0(), rect.v0(), face.projection(), targetU, targetV);
            double[] b = WeldContact.project(rect.u0(), rect.v1(), face.projection(), targetU, targetV);
            double[] c = WeldContact.project(rect.u1(), rect.v1(), face.projection(), targetU, targetV);
            double[] d = WeldContact.project(rect.u1(), rect.v0(), face.projection(), targetU, targetV);

            Vector3d pa = world(face, pose, plane, anchorU + a[0], anchorV + a[1], camera);
            Vector3d pb = world(face, pose, plane, anchorU + b[0], anchorV + b[1], camera);
            Vector3d pc = world(face, pose, plane, anchorU + c[0], anchorV + c[1], camera);
            Vector3d pd = world(face, pose, plane, anchorU + d[0], anchorV + d[1], camera);

            vertex(poses, consumer, pa, red, green, blue, (float) a[0], (float) a[1], normal);
            vertex(poses, consumer, pb, red, green, blue, (float) b[0], (float) b[1], normal);
            vertex(poses, consumer, pc, red, green, blue, (float) c[0], (float) c[1], normal);
            vertex(poses, consumer, pd, red, green, blue, (float) d[0], (float) d[1], normal);
        }
    }

    private static void renderPanel(
            Minecraft minecraft,
            Panel panel,
            Pose3dc pose,
            Vec3 camera,
            PoseStack poses,
            VertexConsumer consumer
    ) {
        Direction facing = panel.targetFacing();
        Direction.Axis targetU = WeldContact.uAxis(facing);
        Direction.Axis targetV = WeldContact.vAxis(facing);
        double anchorU = WeldContact.axisOf(panel.targetAnchor(), targetU);
        double anchorV = WeldContact.axisOf(panel.targetAnchor(), targetV);
        double scale = Math.max(1.0E-6D, Math.abs(pose.scale().x()));
        double plane = WeldGeometry.facePlane(panel.targetPos(), facing)
                + (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : -1.0D)
                * SURFACE_OFFSET / scale;

        Vector3d normal = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        pose.transformNormal(normal).normalize();
        int light = LevelRenderer.getLightColor(minecraft.level, panel.targetPos().relative(facing));

        WeldContactPatch.Edge rect = panel.rect();
        Vector3d pa = world(facing, pose, plane, anchorU + rect.u0(), anchorV + rect.v0(), camera);
        Vector3d pb = world(facing, pose, plane, anchorU + rect.u0(), anchorV + rect.v1(), camera);
        Vector3d pc = world(facing, pose, plane, anchorU + rect.u1(), anchorV + rect.v1(), camera);
        Vector3d pd = world(facing, pose, plane, anchorU + rect.u1(), anchorV + rect.v0(), camera);

        panelVertex(poses, consumer, pa, 0.0F, 0.0F, light, normal);
        panelVertex(poses, consumer, pb, 0.0F, 1.0F, light, normal);
        panelVertex(poses, consumer, pc, 1.0F, 1.0F, light, normal);
        panelVertex(poses, consumer, pd, 1.0F, 0.0F, light, normal);
    }

    private static void panelVertex(
            PoseStack poses,
            VertexConsumer consumer,
            Vector3d point,
            float u,
            float v,
            int light,
            Vector3d normal
    ) {
        PoseStack.Pose last = poses.last();
        Vector3f transformedNormal = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)
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
            Pivot pivot,
            Pose3dc pose,
            Vec3 camera,
            PoseStack poses,
            VertexConsumer consumer
    ) {
        double half = pivot.size() * 0.5D;
        Vector3d[] points = new Vector3d[8];
        int index = 0;
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Vector3d point = new Vector3d(
                            pivot.center().x + x * half,
                            pivot.center().y + y * half,
                            pivot.center().z + z * half);
                    pose.transformPosition(point);
                    points[index++] = point.sub(camera.x, camera.y, camera.z);
                }
            }
        }

        Color tint = new Color(pivot.color(), false);
        float red = tint.getRedAsFloat();
        float green = tint.getGreenAsFloat();
        float blue = tint.getBlueAsFloat();
        Matrix4f matrix = poses.last().pose();

        quad(consumer, matrix, points[0], points[4], points[6], points[2], red, green, blue);
        quad(consumer, matrix, points[1], points[3], points[7], points[5], red, green, blue);
        quad(consumer, matrix, points[0], points[1], points[5], points[4], red, green, blue);
        quad(consumer, matrix, points[2], points[6], points[7], points[3], red, green, blue);
        quad(consumer, matrix, points[0], points[2], points[3], points[1], red, green, blue);
        quad(consumer, matrix, points[4], points[5], points[7], points[6], red, green, blue);
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vector3d a,
            Vector3d b,
            Vector3d c,
            Vector3d d,
            float red,
            float green,
            float blue
    ) {
        pivotVertex(consumer, matrix, a, red, green, blue);
        pivotVertex(consumer, matrix, b, red, green, blue);
        pivotVertex(consumer, matrix, c, red, green, blue);
        pivotVertex(consumer, matrix, d, red, green, blue);
    }

    private static void pivotVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Vector3d point,
            float red,
            float green,
            float blue
    ) {
        consumer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, 1.0F);
    }

    private static Vector3d world(
            Face face,
            Pose3dc pose,
            double plane,
            double u,
            double v,
            Vec3 camera
    ) {
        return world(face.targetFacing(), pose, plane, u, v, camera);
    }

    private static Vector3d world(
            Direction facing,
            Pose3dc pose,
            double plane,
            double u,
            double v,
            Vec3 camera
    ) {
        Vector3d point = WeldGeometry.inPlane(facing, plane, u, v);
        pose.transformPosition(point);
        return point.sub(camera.x, camera.y, camera.z);
    }

    private static void vertex(
            PoseStack poses,
            VertexConsumer consumer,
            Vector3d point,
            float red,
            float green,
            float blue,
            float u,
            float v,
            Vector3d normal
    ) {
        PoseStack.Pose last = poses.last();
        Matrix4f matrix = last.pose();
        Matrix3f normalMatrix = last.normal();
        Vector3f transformedNormal = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)
                .mul(normalMatrix)
                .normalize();
        consumer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .setColor(red, green, blue, 0.5F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    public static void clear() {
        FACES.clear();
        PANELS.clear();
        PIVOTS.clear();
    }

    private WeldFaceRenderer() {}
}
