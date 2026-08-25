package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.PortableSubspaceCompressorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
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

public final class PortableSubspaceCompressorRenderer
        extends KineticBlockEntityRenderer<PortableSubspaceCompressorBlockEntity> {
    private static final PartialModel QUARTER_SHAFT = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath("simulated", "block/quarter_shaft")
    );

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
        renderRotatingBuffer(
                be,
                getRotatedModel(be, state),
                poseStack,
                buffer.getBuffer(RenderType.solid()),
                light
        );
    }

    @Override
    protected SuperByteBuffer getRotatedModel(
            final PortableSubspaceCompressorBlockEntity be,
            final BlockState state
    ) {
        final Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return CachedBuffers.partialFacing(QUARTER_SHAFT, state, facing);
    }
}
