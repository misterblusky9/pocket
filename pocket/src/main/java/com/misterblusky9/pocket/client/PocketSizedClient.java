package com.misterblusky9.pocket.client;

import net.minecraft.client.Minecraft;
import com.misterblusky9.pocket.item.ColliderWandItem;
import com.misterblusky9.pym.api.client.DebugOverlay;
import com.misterblusky9.pocket.PocketSized;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
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
        SwitchBearingPartials.init();
        HelmBearingPartials.init();
        CopycatFacadePartials.init();
        SubspaceHarnessClient.init();
        HelmBearingHandler.register();
        modBus.addListener(PocketPackageModels::register);
        modBus.addListener(PocketItemTooltips::register);
        modBus.addListener(PocketItemProperties::register);
        modBus.addListener(TheMoonPackageRenderer::register);
        modBus.addListener(PocketShaders::register);
        modBus.addListener(PortableSubspaceCompressorRenderer::register);
        modBus.addListener(StaticSubspaceCompressorRenderer::register);
        modBus.addListener(SwitchBearingRenderer::register);
        modBus.addListener(SwitchBearingRenderer::registerVisual);
        modBus.addListener(HelmBearingRenderer::register);
        modBus.addListener(HelmBearingRenderer::registerVisual);
        modBus.addListener(SwitchPistonRenderer::register);
        modBus.addListener(SwitchPistonRenderer::registerVisual);
        modBus.addListener(SubspaceRecyclerRenderer::register);
        modBus.addListener(SubspaceRecyclerRenderer::registerVisual);
        modBus.addListener(SubspaceHarnessClient::register);
        modBus.addListener(SubspaceHarnessClient::registerVisual);
        modBus.addListener(PocketKeys::register);
        modBus.addListener(CopycatFacadeModel::swap);
        modBus.addListener(CopycatFacadeModel::registerColours);
        NeoForge.EVENT_BUS.addListener(CompressionFieldRenderer::render);
        NeoForge.EVENT_BUS.addListener(CompressionBeamRenderer::render);
        DebugOverlay.showCollidersWhile(() -> {
            final var player = Minecraft.getInstance().player;
            return player != null && (player.getMainHandItem().getItem() instanceof ColliderWandItem
                    || player.getOffhandItem().getItem() instanceof ColliderWandItem);
        });
        NeoForge.EVENT_BUS.addListener(SwitchBearingOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(WeldFaceRenderer::render);
        NeoForge.EVENT_BUS.addListener(CopycatFacadeFrames::tick);
        CompressionGunRenderHandler.INSTANCE.registerListeners(NeoForge.EVENT_BUS);
        HotGlueGunRenderHandler.INSTANCE.registerListeners(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> {
                    CompressionBeamRenderer.tick();
                    CompressionGunRenderHandler.INSTANCE.tick();
                    HotGlueGunRenderHandler.INSTANCE.tick();
                    ShrinkRayHoverOutline.tick();
                }
        );
        NeoForge.EVENT_BUS.addListener(ScaleSelectionInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(FacadeAlignmentInput::onInteraction);
        NeoForge.EVENT_BUS.addListener(CompressionGunControls::onScroll);
        NeoForge.EVENT_BUS.addListener(ScaleToolModifierClient::onClientTick);

        NeoForge.EVENT_BUS.addListener(CompressionHud::render);
    }
}
