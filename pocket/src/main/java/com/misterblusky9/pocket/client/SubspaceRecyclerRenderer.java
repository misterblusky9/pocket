package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.misterblusky9.pocket.block.SubspaceRecyclerBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class SubspaceRecyclerRenderer
        extends KineticBlockEntityRenderer<SubspaceRecyclerBlockEntity> {
    private static final PartialModel COG = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/subspace_recycler/inner")
    );

    public SubspaceRecyclerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    public static void register(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.SUBSPACE_RECYCLER.get(),
                SubspaceRecyclerRenderer::new
        );
    }

    public static void registerVisual(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer
                .builder(ModBlockEntities.SUBSPACE_RECYCLER.get())
                .factory(SingleAxisRotatingVisual.of(COG))
                .neverSkipVanillaRender()
                .apply());
    }

    @Override
    protected SuperByteBuffer getRotatedModel(
            final SubspaceRecyclerBlockEntity be,
            final BlockState state
    ) {
        return CachedBuffers.partial(COG, state);
    }
}
