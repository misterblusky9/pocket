package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.SwitchPistonBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class SwitchPistonRenderer extends KineticBlockEntityRenderer<SwitchPistonBlockEntity> {
    public SwitchPistonRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.SWITCH_PISTON.get(),
                SwitchPistonRenderer::new
        );
    }

    public static void registerVisual(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(ModBlockEntities.SWITCH_PISTON.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .apply());
    }

    @Override
    protected BlockState getRenderedBlockState(final SwitchPistonBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}
