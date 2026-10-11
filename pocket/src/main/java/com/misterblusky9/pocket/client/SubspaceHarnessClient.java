package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class SubspaceHarnessClient {
    public static final PartialModel COGS =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/subspace_harness/block_cogs"));

    public static final PartialModel SHAFT =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath(
                    PocketSized.MOD_ID, "block/subspace_harness/block_shaft_input"));

    public static void init() {}

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SUBSPACE_HARNESS.get(), BacktankRenderer::new);
    }

    public static void registerVisual(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(ModBlockEntities.SUBSPACE_HARNESS.get())
                .factory(SingleAxisRotatingVisual::backtank)
                .neverSkipVanillaRender()
                .apply());
    }

    private SubspaceHarnessClient() {}
}
