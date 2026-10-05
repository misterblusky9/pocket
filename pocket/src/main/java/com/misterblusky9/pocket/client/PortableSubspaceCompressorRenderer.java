package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;

public final class PortableSubspaceCompressorRenderer
        extends KineticBlockEntityRenderer<PortableSubspaceCompressorBlockEntity> {
    private static final PartialModel PIPE_LEFT =
            partial("block/portable_subspace_compressor/exhaust_pipe_left");
    private static final PartialModel PIPE_RIGHT =
            partial("block/portable_subspace_compressor/exhaust_pipe_right");
    private static final PartialModel OUTLET_LEFT =
            partial("block/portable_subspace_compressor/exhaust_outlet_left");
    private static final PartialModel OUTLET_RIGHT =
            partial("block/portable_subspace_compressor/exhaust_outlet_right");
    private static final PartialModel HATCH_BOTTOM =
            partial("block/portable_subspace_compressor/hatch_bottom");
    private static final PartialModel HATCH_TOP =
            partial("block/portable_subspace_compressor/hatch_top");
    private static final PartialModel MOUTH =
            partial("block/portable_subspace_compressor/mouth");

    private static final Vector3f OUTLET_ROTATION_POINT_LEFT =
            new Vector3f(2.2F, 10.2F, 11.0F).div(16.0F);
    private static final Vector3f OUTLET_ROTATION_POINT_RIGHT =
            new Vector3f(13.6F, 10.2F, 11.0F).div(16.0F);
    private static final float OUTLET_ROTATION = (float) Math.toRadians(7.5D);

    public PortableSubspaceCompressorRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.PORTABLE_SUBSPACE_COMPRESSOR.get(),
                PortableSubspaceCompressorRenderer::new
        );
    }

    @Override
    protected void renderSafe(
            final PortableSubspaceCompressorBlockEntity be,
            final float partialTicks,
            final PoseStack poseStack,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        final BlockState state = getRenderedBlockState(be);
        final Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        renderRotatingBuffer(
                be,
                getRotatedModel(be, state),
                poseStack,
                buffer.getBuffer(RenderType.solid()),
                light
        );

        final VertexConsumer cutout = buffer.getBuffer(RenderType.cutout());

        renderPart(HATCH_BOTTOM, state, facing, poseStack, cutout, light);
        renderPart(HATCH_TOP, state, facing, poseStack, cutout, light);
        renderPart(MOUTH, state, facing.getOpposite(), poseStack, cutout, light);
        renderPart(PIPE_LEFT, state, facing, poseStack, cutout, light);
        renderPart(PIPE_RIGHT, state, facing, poseStack, cutout, light);

        rotateToFacing(CachedBuffers.partial(OUTLET_LEFT, state), facing)
                .translate(OUTLET_ROTATION_POINT_LEFT)
                .rotateY(OUTLET_ROTATION)
                .translateBack(OUTLET_ROTATION_POINT_LEFT)
                .light(light)
                .renderInto(poseStack, cutout);

        rotateToFacing(CachedBuffers.partial(OUTLET_RIGHT, state), facing)
                .translate(OUTLET_ROTATION_POINT_RIGHT)
                .rotateY(-OUTLET_ROTATION)
                .translateBack(OUTLET_ROTATION_POINT_RIGHT)
                .light(light)
                .renderInto(poseStack, cutout);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(
            final PortableSubspaceCompressorBlockEntity be,
            final BlockState state
    ) {
        return CachedBuffers.partialFacing(
                AllPartialModels.SHAFT_HALF,
                state,
                state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        );
    }

    private static void renderPart(
            final PartialModel model,
            final BlockState state,
            final Direction facing,
            final PoseStack poseStack,
            final VertexConsumer consumer,
            final int light
    ) {
        rotateToFacing(CachedBuffers.partial(model, state), facing)
                .light(light)
                .renderInto(poseStack, consumer);
    }

    private static SuperByteBuffer rotateToFacing(
            final SuperByteBuffer buffer,
            final Direction facing
    ) {
        buffer.rotateCentered(
                AngleHelper.rad(AngleHelper.horizontalAngle(facing)),
                Direction.UP
        );
        return buffer;
    }

    private static PartialModel partial(final String path) {
        return PartialModel.of(
                ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, path)
        );
    }
}
