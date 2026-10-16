package com.misterblusky9.pocket.client;

import com.misterblusky9.pocket.PocketSized;
import com.misterblusky9.pocket.block.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogVisual;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class BlueprintEncasingClient {
    static final PartialModel SHAFT = partial("blueprint_encased_shaft");
    static final PartialModel COGWHEEL = partial("blueprint_encased_cogwheel");
    static final PartialModel LARGE_COGWHEEL = partial("blueprint_encased_large_cogwheel");

    private static PartialModel partial(String kind) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(PocketSized.MOD_ID, "block/" + kind + "/rotating"));
    }

    public static void init() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BLUEPRINT_ENCASED_SHAFT.get(),
                BlueprintEncasedShaftRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BLUEPRINT_ENCASED_COGWHEEL.get(),
                BlueprintEncasedCogwheelRenderer::small);
        event.registerBlockEntityRenderer(ModBlockEntities.BLUEPRINT_ENCASED_LARGE_COGWHEEL.get(),
                BlueprintEncasedCogwheelRenderer::large);
    }

    public static void registerVisuals(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.BLUEPRINT_ENCASED_SHAFT.get())
                    .factory((context, be, pt) -> new SingleAxisRotatingVisual<>(
                            context, be, pt, Direction.SOUTH, Models.partial(SHAFT)))
                    .skipVanillaRender(be -> true)
                    .apply();
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.BLUEPRINT_ENCASED_COGWHEEL.get())
                    .factory((context, be, pt) -> new EncasedCogVisual(
                            context, be, false, pt, Models.partial(COGWHEEL)))
                    .skipVanillaRender(be -> true)
                    .apply();
            SimpleBlockEntityVisualizer.builder(ModBlockEntities.BLUEPRINT_ENCASED_LARGE_COGWHEEL.get())
                    .factory((context, be, pt) -> new EncasedCogVisual(
                            context, be, true, pt, Models.partial(LARGE_COGWHEEL)))
                    .skipVanillaRender(be -> true)
                    .apply();
        });
    }

    private BlueprintEncasingClient() {}
}
