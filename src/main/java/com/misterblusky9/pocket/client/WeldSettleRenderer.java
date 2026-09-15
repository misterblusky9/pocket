package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.simulated.CrossScaleWelds;
import com.misterblusky9.pocket.compat.simulated.WeldRecord;
import com.misterblusky9.pocket.scale.ScaleState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PocketSized.MOD_ID, value = Dist.CLIENT)
public final class WeldSettleRenderer {
    private static final RenderType STRAND = RenderType.entityCutout(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "textures/block/hot_glue/hot_glue_strand.png"));
    private static final Pose3d WORLD_POSE = new Pose3d();

    private static final double[][] STRANDS = {
            {-0.1875D, -0.1875D, -0.0375D, -0.15D},
            {0.075D, 0.075D, 0.1125D, 0.15D}
    };
    private static List<Endpoints> current = List.of();

    private record Endpoints(
            WeldRecord record,
            Vector3d a,
            Vector3d b,
            Vector3d rightA,
            Vector3d rightB,
            Vector3d upA,
            Vector3d upB,
            double scale
    ) {}

    @SubscribeEvent
    public static void onFrame(final RenderFrameEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            current = List.of();
            return;
        }

        current = endpoints(minecraft);
        for (final Endpoints pair : current) {
            final WeldRecord record = pair.record();
            WeldContactPatch.showGlueFace(
                    "pocket_weld_settle_a_" + record.weldId(),
                    record.smallPos(),
                    record.smallFacing(),
                    record.anchorFor(true),
                    record.smallSpan());
            WeldContactPatch.showGlueFace(
                    "pocket_weld_settle_b_" + record.weldId(),
                    record.bigPos(),
                    record.bigFacing(),
                    record.anchorFor(false),
                    record.bigSpan());
        }
    }

    @SubscribeEvent
    public static void render(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || current.isEmpty()) return;

        final Vec3 camera = event.getCamera().getPosition();
        final PoseStack poses = event.getPoseStack();
        final MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        final VertexConsumer buffer = buffers.getBuffer(STRAND);

        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (final Endpoints pair : current) render(pair, poses, buffer);
        poses.popPose();
        buffers.endBatch(STRAND);
    }

    private static List<Endpoints> endpoints(final Minecraft minecraft) {
        final List<WeldRecord> welds = CrossScaleWelds.clientWelds();
        if (welds.isEmpty() || minecraft.level == null) return List.of();

        final SubLevelContainer container = SubLevelContainer.getContainer(minecraft.level);
        if (container == null) return List.of();

        final float partialTick = minecraft.gameRenderer.getMainCamera().getPartialTickTime();
        final List<Endpoints> result = new ArrayList<>();
        for (final WeldRecord record : welds) {
            if (!(container.getSubLevel(record.smallSubLevel()) instanceof final ClientSubLevel small)
                    || small.isRemoved()) {
                continue;
            }
            final ClientSubLevel big = record.worldAnchored()
                    ? null
                    : container.getSubLevel(record.bigSubLevel()) instanceof final ClientSubLevel subLevel ? subLevel : null;
            if (!record.worldAnchored() && (big == null || big.isRemoved())) continue;

            final Pose3dc poseA = small.renderPose(partialTick);
            final Pose3dc poseB = big == null ? WORLD_POSE : big.renderPose(partialTick);
            final Vector3d a = poseA.transformPosition(record.anchorFor(true));
            final Vector3d b = poseB.transformPosition(record.anchorFor(false));
            final double rawScale = Math.min(ScaleState.getScale(small), big == null ? 1.0D : ScaleState.getScale(big));
            if (!Double.isFinite(rawScale)) continue;
            final double scale = PocketSized.clampScale(rawScale);
            if (a.distance(b) <= Math.max(1.0E-4D, scale * 0.02D)) continue;

            final Quaterniond toBig = new Quaterniond(record.orientation()).invert();
            final Vector3d rightA = tangent(record.smallFacing(), true);
            final Vector3d upA = tangent(record.smallFacing(), false);
            final Vector3d rightB = toBig.transform(new Vector3d(rightA));
            final Vector3d upB = toBig.transform(new Vector3d(upA));
            poseA.transformNormal(rightA);
            poseA.transformNormal(upA);
            poseB.transformNormal(rightB);
            poseB.transformNormal(upB);
            normalize(rightA);
            normalize(upA);
            normalize(rightB);
            normalize(upB);

            result.add(new Endpoints(record, a, b, rightA, rightB, upA, upB, scale));
        }
        return result;
    }

    private static void render(final Endpoints pair, final PoseStack poses, final VertexConsumer buffer) {
        final double half = pair.scale() * 0.5D;
        for (final double[] strand : STRANDS) {
            final Vector3d a = new Vector3d(pair.a())
                    .fma(strand[0] * pair.scale(), pair.rightA())
                    .fma(strand[1] * pair.scale(), pair.upA());
            final Vector3d b = new Vector3d(pair.b())
                    .fma(strand[2] * pair.scale(), pair.rightB())
                    .fma(strand[3] * pair.scale(), pair.upB());
            cross(a, pair.upA(), pair.rightA(), b, pair.upB(), pair.rightB(), half, poses, buffer);
        }
    }

    private static void cross(
            final Vector3dc a,
            final Vector3dc upA,
            final Vector3dc rightA,
            final Vector3dc b,
            final Vector3dc upB,
            final Vector3dc rightB,
            final double half,
            final PoseStack poses,
            final VertexConsumer buffer
    ) {
        quad(a, upA, b, upB, half, poses, buffer);
        quad(a, rightA, b, rightB, half, poses, buffer);
    }

    private static void quad(
            final Vector3dc a,
            final Vector3dc axisA,
            final Vector3dc b,
            final Vector3dc axisB,
            final double half,
            final PoseStack poses,
            final VertexConsumer buffer
    ) {
        final Vector3d a0 = new Vector3d(a).fma(-half, axisA);
        final Vector3d a1 = new Vector3d(a).fma(half, axisA);
        final Vector3d b1 = new Vector3d(b).fma(half, axisB);
        final Vector3d b0 = new Vector3d(b).fma(-half, axisB);
        face(a0, a1, b1, b0, poses, buffer);
        face(b0, b1, a1, a0, poses, buffer);
    }

    private static void face(
            final Vector3dc a,
            final Vector3dc b,
            final Vector3dc c,
            final Vector3dc d,
            final PoseStack poses,
            final VertexConsumer buffer
    ) {
        vertex(buffer, poses, a, 0.0F, 0.0F);
        vertex(buffer, poses, b, 0.0F, 1.0F);
        vertex(buffer, poses, c, 1.0F, 1.0F);
        vertex(buffer, poses, d, 1.0F, 0.0F);
    }

    private static void vertex(
            final VertexConsumer buffer,
            final PoseStack poses,
            final Vector3dc pos,
            final float u,
            final float v
    ) {
        final Matrix4f pose = poses.last().pose();
        buffer.addVertex(pose, (float) pos.x(), (float) pos.y(), (float) pos.z())
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(poses.last(), 0.0F, 1.0F, 0.0F);
    }

    private static Vector3d tangent(final Direction facing, final boolean right) {
        if (facing.getAxis().isHorizontal()) {
            if (right) {
                final Direction direction = facing.getClockWise();
                return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            }
            return new Vector3d(0.0D, 1.0D, 0.0D);
        }
        return right ? new Vector3d(0.0D, 0.0D, 1.0D) : new Vector3d(1.0D, 0.0D, 0.0D);
    }

    public static void clear() {
        current = List.of();
    }

    private static void normalize(final Vector3d vector) {
        if (vector.lengthSquared() > 1.0E-12D) vector.normalize();
    }

    private WeldSettleRenderer() {}
}
