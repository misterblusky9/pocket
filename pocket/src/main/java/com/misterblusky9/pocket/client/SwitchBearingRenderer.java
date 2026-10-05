package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.SwitchBearingBlock;
import com.misterblusky9.pocket.block.SwitchBearingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class SwitchBearingRenderer extends KineticBlockEntityRenderer<SwitchBearingBlockEntity> {
    public SwitchBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        SwitchBearingPartials.init();
        event.registerBlockEntityRenderer(
                ModBlockEntities.SWITCH_BEARING.get(),
                SwitchBearingRenderer::new
        );
    }

    public static void registerVisual(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(ModBlockEntities.SWITCH_BEARING.get())
                .factory(SwitchBearingVisual::new)
                .apply());
    }

    @Override
    protected void renderSafe(
            final SwitchBearingBlockEntity be,
            final float partialTicks,
            final PoseStack ms,
            final MultiBufferSource buffer,
            final int light,
            final int overlay
    ) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final Direction facing = be.getBlockState().getValue(BlockStateProperties.FACING);
        final SuperByteBuffer superBuffer =
                CachedBuffers.partial(SwitchBearingPartials.TOP, be.getBlockState());

        final float interpolatedAngle = be.getInterpolatedAngle(partialTicks - 1);
        kineticRotationTransform(
                superBuffer, be, facing.getAxis(), (float) (interpolatedAngle / 180 * Math.PI), light);

        if (facing.getAxis().isHorizontal()) {
            superBuffer.rotateCentered(
                    AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }
        superBuffer.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);
        superBuffer.renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final SwitchBearingBlockEntity be, final BlockState state) {
        return CachedBuffers.partialFacing(
                SwitchBearingPartials.SHAFT_HALF, state, state.getValue(SwitchBearingBlock.FACING).getOpposite());
    }
}
