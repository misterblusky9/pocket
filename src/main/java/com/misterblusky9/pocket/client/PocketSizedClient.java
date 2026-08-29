package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PocketSized.MOD_ID, dist = Dist.CLIENT)
public final class PocketSizedClient {
    public static final java.util.Map<com.misterblusky9.pocket.item.PocketContainer,
            dev.engine_room.flywheel.lib.model.baked.PartialModel> CONTAINER_MODELS = buildContainerModels();

    private static java.util.Map<com.misterblusky9.pocket.item.PocketContainer,
            dev.engine_room.flywheel.lib.model.baked.PartialModel> buildContainerModels() {
        final java.util.EnumMap<com.misterblusky9.pocket.item.PocketContainer,
                dev.engine_room.flywheel.lib.model.baked.PartialModel> models =
                new java.util.EnumMap<>(com.misterblusky9.pocket.item.PocketContainer.class);
        for (final com.misterblusky9.pocket.item.PocketContainer container
                : com.misterblusky9.pocket.item.PocketContainer.values()) {
            models.put(container,
                    dev.engine_room.flywheel.lib.model.baked.PartialModel.of(container.modelId()));
        }
        return java.util.Collections.unmodifiableMap(models);
    }

    public static dev.engine_room.flywheel.lib.model.baked.PartialModel boxModelFor(
            final net.minecraft.world.item.ItemStack carrier) {
        return CONTAINER_MODELS.get(com.misterblusky9.pocket.item.PocketContainer.of(carrier));
    }
    public PocketSizedClient(final IEventBus modBus) {
        modBus.addListener(PocketPackageModels::register);
        modBus.addListener(TheMoonPackageRenderer::register);
        modBus.addListener(PocketShaders::register);
        modBus.addListener(PortableSubspaceCompressorRenderer::register);
        modBus.addListener(SubspaceRecyclerRenderer::register);
        modBus.addListener(SubspaceRecyclerRenderer::registerVisual);
        modBus.addListener(PocketKeys::register);
        NeoForge.EVENT_BUS.addListener(CompressionFieldRenderer::render);
        NeoForge.EVENT_BUS.addListener(MoonPhysicsRenderer::render);
        NeoForge.EVENT_BUS.addListener(CompressionBeamRenderer::render);
        NeoForge.EVENT_BUS.addListener(ColliderOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(TweezerBeamRenderer::render);
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
                    CompressionBeamRenderer.tick();
                    ScaleHandshake.tick();
                    TweezerDrag.tick();
                }
        );
        NeoForge.EVENT_BUS.addListener(ShrinkRayControls::onScroll);
        NeoForge.EVENT_BUS.addListener(CompressionGunControls::onScroll);
        NeoForge.EVENT_BUS.addListener(TweezerDrag::onScroll);

        NeoForge.EVENT_BUS.addListener(CompressionHud::render);
        NeoForge.EVENT_BUS.addListener(
                (RenderFrameEvent.Pre event) -> PocketClientFrame.beginFrame()
        );

        NeoForge.EVENT_BUS.addListener(
                (LevelEvent.Unload event) -> {
                    if (event.getLevel().isClientSide()) {
                        CompressionFieldRenderer.clear();
                        CompressionBeamRenderer.clear();
                        TweezerBeamRenderer.clear();
                        ColliderOutlineRenderer.clear();
                        ScaleHandshake.clear();
                        MoonPhysicsClient.clear();
                    }
                }
        );
    }
}
