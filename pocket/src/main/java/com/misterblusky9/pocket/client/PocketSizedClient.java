package com.misterblusky9.pocket.client;

import net.minecraft.client.Minecraft;
import com.misterblusky9.pocket.item.ColliderWandItem;
import com.misterblusky9.pym.api.client.DebugOverlay;
import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.compat.curios.CuriosCompressorRenderer;
import com.misterblusky9.pocket.ponder.PocketPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PocketSized.MOD_ID, dist = Dist.CLIENT)
public final class PocketSizedClient {
    public static final java.util.Map<com.misterblusky9.pocket.item.PocketContainer,
            dev.engine_room.flywheel.lib.model.baked.PartialModel> CONTAINER_MODELS = buildContainerModels();

    private static java.util.Map<com.misterblusky9.pocket.item.PocketContainer,
            dev.engine_room.flywheel.lib.model.baked.PartialModel> buildContainerModels() {
        java.util.EnumMap<com.misterblusky9.pocket.item.PocketContainer,
                dev.engine_room.flywheel.lib.model.baked.PartialModel> models =
                new java.util.EnumMap<>(com.misterblusky9.pocket.item.PocketContainer.class);
        for (com.misterblusky9.pocket.item.PocketContainer container
                : com.misterblusky9.pocket.item.PocketContainer.values()) {
            models.put(container,
                    dev.engine_room.flywheel.lib.model.baked.PartialModel.of(container.modelId()));
        }
        return java.util.Collections.unmodifiableMap(models);
    }

    public static dev.engine_room.flywheel.lib.model.baked.PartialModel boxModelFor(
            net.minecraft.world.item.ItemStack carrier) {
        return CONTAINER_MODELS.get(com.misterblusky9.pocket.item.PocketContainer.of(carrier));
    }

    public PocketSizedClient(IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.BakingCompleted event) ->
                PocketedSubLevelItemRenderer.invalidateMeshes());
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(PunchyCompat::apply));
        modBus.addListener((FMLClientSetupEvent event) ->
                event.enqueueWork(() -> PonderIndex.addPlugin(new PocketPonderPlugin())));
        SwitchBearingPartials.init();
        HelmBearingPartials.init();
        FlexibleCouplingRenderer.init();
        CopycatFacadePartials.init();
        BlueprintCasingModel.init();
        BlueprintEncasingClient.init();
        SubspaceHarnessClient.init();
        HelmBearingHandler.register();
        FlexibleShaftItemHandler.register();
        modBus.addListener(BlueprintEncasingClient::registerRenderers);
        modBus.addListener(BlueprintEncasingClient::registerVisuals);
        modBus.addListener(PocketPackageModels::register);
        modBus.addListener(PocketItemTooltips::register);
        modBus.addListener(PocketItemProperties::register);
        if (ModList.get().isLoaded("curios")) modBus.addListener(CuriosCompressorRenderer::register);
        modBus.addListener(TheMoonPackageRenderer::register);
        modBus.addListener(PocketShaders::register);
        modBus.addListener(HotGlueParticle::register);
        modBus.addListener(PortableSubspaceCompressorRenderer::register);
        modBus.addListener(StaticSubspaceCompressorRenderer::register);
        modBus.addListener(SwitchBearingRenderer::register);
        modBus.addListener(SwitchBearingRenderer::registerVisual);
        modBus.addListener(HelmBearingRenderer::register);
        modBus.addListener(FlexibleCouplingRenderer::register);
        modBus.addListener(HelmBearingRenderer::registerVisual);
        modBus.addListener(SwitchPistonRenderer::register);
        modBus.addListener(SwitchPistonRenderer::registerVisual);
        modBus.addListener(SubspaceRecyclerRenderer::register);
        modBus.addListener(SubspaceRecyclerRenderer::registerVisual);
        modBus.addListener(SubspaceHarnessClient::register);
        modBus.addListener(SubspaceHarnessClient::registerVisual);
        modBus.addListener(PocketKeys::register);
        modBus.addListener(BlueprintCasingModel::swap);
        modBus.addListener(BlueprintCasingModel::registerConnectivity);
        modBus.addListener(CopycatFacadeModel::swap);
        modBus.addListener(CopycatFacadeModel::registerColours);
        NeoForge.EVENT_BUS.addListener(CompressionFieldRenderer::render);
        NeoForge.EVENT_BUS.addListener(CompressionBeamRenderer::render);
        java.util.function.BooleanSupplier colliderWand = () -> {
            var player = Minecraft.getInstance().player;
            return player != null && (player.getMainHandItem().getItem() instanceof ColliderWandItem
                    || player.getOffhandItem().getItem() instanceof ColliderWandItem);
        };
        DebugOverlay.showCollidersWhile(colliderWand);
        DebugOverlay.showPlayerLodWhile(colliderWand);
        NeoForge.EVENT_BUS.addListener(SwitchBearingOutlineRenderer::render);
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassIndicator::render);
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
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassZoom::onClientTick);
        NeoForge.EVENT_BUS.addListener(PersonalCompressorKey::onClientTick);
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassZoom::onScroll);
        NeoForge.EVENT_BUS.addListener(CompressionGunControls::onKey);
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassZoom::onComputeFov);
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassZoom::onTurn);
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassZoom::onRenderHand);
        NeoForge.EVENT_BUS.addListener(MagnifyingGlassZoom::onMovementInput);
        NeoForge.EVENT_BUS.addListener(ScaleToolModifierClient::onClientTick);

        NeoForge.EVENT_BUS.addListener(CompressionHud::render);
    }
}
