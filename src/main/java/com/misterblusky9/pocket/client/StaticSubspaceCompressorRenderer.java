package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.StaticSubspaceCompressorBlock;
import com.misterblusky9.pocket.block.StaticSubspaceCompressorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.util.SableDistUtil;
import dev.simulated_team.simulated.content.blocks.lasers.LaserBehaviour;
import dev.simulated_team.simulated.index.SimRenderTypes;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

public final class StaticSubspaceCompressorRenderer
        extends KineticBlockEntityRenderer<StaticSubspaceCompressorBlockEntity> {
    private static final int R = 0;
    private static final int G = 1;
    private static final int B = 2;

    private static final float[] SHRINK = {
            0x9A / 255.0F,
            0xF0 / 255.0F,
            0xFF / 255.0F
    };

    private static final float[] GROW = {
            0xFF / 255.0F,
            0xE0 / 255.0F,
            0x4A / 255.0F
    };

    private static final float IDLE_ALPHA = 0.55F;
    private static final float ENGAGED_ALPHA = 0.95F;
    private static final float IDLE_SCALE = 0.6F;
    private static final float ENGAGED_SCALE = 1.0F;

    private static final PartialModel LENS_OFF =
            partial("pocket", "block/static_subspace_compressor/lens_off");
    private static final PartialModel LENS_ON =
            partial("pocket", "block/static_subspace_compressor/lens_on");
    private static final PartialModel QUARTER_SHAFT =
            partial("simulated", "block/quarter_shaft");

    public StaticSubspaceCompressorRenderer(
            final BlockEntityRendererProvider.Context context
    ) {
        super(context);
    }

    private static PartialModel partial(final String namespace, final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static float[] tint(final StaticSubspaceCompressorBlockEntity blockEntity) {
        return blockEntity.mode().isGrowing() ? GROW : SHRINK;
    }

    @Override
    protected void renderSafe(
            final StaticSubspaceCompressorBlockEntity blockEntity,
            final float partialTicks,
            final PoseStack pose,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        renderQuarterShaft(blockEntity, pose, buffer, light);
        renderLens(blockEntity, pose, buffer, light);
        renderLaser(blockEntity, partialTicks, pose, buffer);
    }

    private static void renderQuarterShaft(
            final StaticSubspaceCompressorBlockEntity blockEntity,
            final PoseStack pose,
            final MultiBufferSource buffer,
            final int light
    ) {
        final BlockState state = blockEntity.getBlockState();
        final Direction inputFace = StaticSubspaceCompressorBlock.mechanicalInputFace(state);

        final SuperByteBuffer shaft =
                CachedBuffers.partialFacing(QUARTER_SHAFT, state, inputFace);

        KineticBlockEntityRenderer.renderRotatingBuffer(
                blockEntity,
                shaft,
                pose,
                buffer.getBuffer(RenderType.solid()),
                light
        );
    }

    private static void renderLens(
            final StaticSubspaceCompressorBlockEntity blockEntity,
            final PoseStack pose,
            final MultiBufferSource buffer,
            final int light
    ) {
        final boolean casting = blockEntity.shouldCast();

        final SuperByteBuffer lens = CachedBuffers.partial(
                casting ? LENS_ON : LENS_OFF,
                blockEntity.getBlockState()
        );

        lens.translate(0.5, 0.5, 0.5);
        lens.rotateToFace(
                blockEntity.getBlockState().getValue(StaticSubspaceCompressorBlock.FACING)
        );
        lens.translate(-0.5, -0.5, -0.5);
        lens.light(casting ? LightTexture.FULL_BRIGHT : light);
        lens.disableDiffuse();

        final float[] tint = tint(blockEntity);
        lens.color(
                (int) (tint[R] * 255),
                (int) (tint[G] * 255),
                (int) (tint[B] * 255),
                255
        );
        lens.renderInto(pose, buffer.getBuffer(SimRenderTypes.lens()));
    }

    private void renderLaser(
            final StaticSubspaceCompressorBlockEntity blockEntity,
            final float partialTicks,
            final PoseStack pose,
            final MultiBufferSource buffer
    ) {
        final LaserBehaviour laser = blockEntity.getAllBehaviours()
                .stream()
                .filter(behaviour -> behaviour instanceof LaserBehaviour)
                .map(behaviour -> (LaserBehaviour) behaviour)
                .findFirst()
                .orElse(null);

        if (laser == null || !laser.shouldCast()) {
            return;
        }

        final Vector4f colors = getColors(blockEntity);
        if (colors.w <= 0.0F) {
            return;
        }

        pose.pushPose();
        transformPose(blockEntity, pose);

        final float distance = getLaserLength(laser);
        createLaser(colors, pose, buffer, laser.getRange(), distance);

        pose.popPose();
    }

    private static float getLaserLength(final LaserBehaviour laser) {
        float laserRange = laser.getRange();
        final HitResult hitResult = laser.getClosestHitResult();
        final Couple<Vec3> positions = laser.getLaserPositions().get();

        if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hitResult.getLocation();
            if (laser.getVirtualHitPos() != Vec3.ZERO) {
                hitPos = laser.getVirtualHitPos();
            }

            laserRange = (float) Math.sqrt(
                    Sable.HELPER.distanceSquaredWithSubLevels(
                            SableDistUtil.getClientLevel(),
                            positions.getFirst(),
                            hitPos
                    )
            ) - 0.1F;
        } else if (laser.getVirtualHitPos() != Vec3.ZERO) {
            final Vec3 hitPos = laser.getVirtualHitPos();
            laserRange = (float) Math.sqrt(
                    Sable.HELPER.distanceSquaredWithSubLevels(
                            SableDistUtil.getClientLevel(),
                            positions.getFirst(),
                            hitPos
                    )
            ) - 0.1F;
        }

        return laserRange;
    }

    private static void transformPose(
            final StaticSubspaceCompressorBlockEntity blockEntity,
            final PoseStack pose
    ) {
        final Direction facing = blockEntity.getDirection();

        pose.translate(0.5, 0.5, 0.5);
        TransformStack.of(pose)
                .rotate(facing.getRotation())
                .rotateXDegrees(-90)
                .translate(0, 0, 0.5 - 0.0625);

        final float scale = blockEntity.isEngaged() ? ENGAGED_SCALE : IDLE_SCALE;
        pose.scale(scale, scale, 1);
        pose.translate(-0.5, -0.5, 0.0);
    }

    private static void createLaser(
            final Vector4f color,
            final PoseStack pose,
            final MultiBufferSource buffer,
            final float maxLength,
            final float length
    ) {
        final VertexConsumer builder;

        if (buffer instanceof final SuperRenderTypeBuffer superRenderTypeBuffer) {
            builder = superRenderTypeBuffer.getLateBuffer(SimRenderTypes.laser());
        } else {
            builder = buffer.getBuffer(SimRenderTypes.laser());
        }

        final float lengthFrac = length / maxLength;
        final float offset = lengthFrac / 10.0F;
        final float endU = 1.0F + 1.0F / length;

        final float red = color.x();
        final float blue = color.y();
        final float green = color.z();
        final float alpha = color.w();
        final float endAlpha = alpha * (1.0F - lengthFrac);

        pose.pushPose();
        final Quaternionf rotationQuat = Axis.ZN.rotationDegrees(90);

        for (int i = 0; i < 4; i++) {
            final Matrix4f matrix = pose.last().pose();

            builder.addVertex(matrix, 0, 0.0F, 0)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, endU)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0.0F, 1.0F, 0.0F);

            builder.addVertex(matrix, 1, 0.0F, 0)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, endU)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0.0F, 1.0F, 0.0F);

            builder.addVertex(matrix, 1 + offset, -offset, length + 0.5F)
                    .setColor(red, green, blue, endAlpha)
                    .setUv(endU, endU)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0.0F, 1.0F, 0.0F);

            builder.addVertex(matrix, -offset, -offset, length + 0.5F)
                    .setColor(red, green, blue, endAlpha)
                    .setUv(endU, endU)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setNormal(0.0F, 1.0F, 0.0F);

            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(rotationQuat);
            pose.translate(-0.5, -0.5, -0.5);
        }

        pose.popPose();
    }

    private static Vector4f getColors(
            final StaticSubspaceCompressorBlockEntity blockEntity
    ) {
        final float alpha = blockEntity.isEngaged() ? ENGAGED_ALPHA : IDLE_ALPHA;
        final float[] tint = tint(blockEntity);

        return new Vector4f(tint[R], tint[B], tint[G], alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(
            final @NotNull StaticSubspaceCompressorBlockEntity blockEntity
    ) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.STATIC_SUBSPACE_COMPRESSOR.get(),
                StaticSubspaceCompressorRenderer::new
        );
    }
}
